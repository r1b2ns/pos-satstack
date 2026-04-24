package com.possatstack.app.wallet.signer

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import com.possatstack.app.util.AppLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps a dedicated AndroidKeyStore AES-GCM key used to encrypt the mnemonic.
 *
 * This key is separate from the [androidx.security.crypto.MasterKey] that the
 * rest of [AndroidKeystoreSignerSecretStore] uses to encrypt its prefs file.
 * Two keys are needed because:
 *  - The mnemonic key is `setUserAuthenticationRequired(true)` with a 30s
 *    validity window, so reading the mnemonic requires a recent biometric/PIN
 *    prompt.
 *  - The prefs-level key is NOT auth-required, so metadata reads (network,
 *    "is there a mnemonic at all?") work without prompting.
 *
 * If the device supports StrongBox (`PackageManager.FEATURE_STRONGBOX_KEYSTORE`)
 * the key is built inside it; otherwise it falls back to the TEE. The chosen
 * tier is logged on key creation.
 */
internal class MnemonicCipher private constructor(
    private val key: SecretKey,
    val strongBoxBacked: Boolean,
) {
    /** Ciphertext + IV bundle. Both encoded as Base64 NO_WRAP for prefs storage. */
    data class Envelope(
        val ciphertext: String,
        val iv: String,
    )

    fun encrypt(plaintext: ByteArray): Envelope {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return Envelope(
            ciphertext = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
            iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
        )
    }

    /**
     * Decrypt with the stored key. If the user has not authenticated recently
     * the platform throws [android.security.keystore.UserNotAuthenticatedException];
     * callers must catch that exception, invoke a biometric prompt, and retry.
     */
    fun decrypt(envelope: Envelope): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivBytes = android.util.Base64.decode(envelope.iv, android.util.Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
        val ciphertextBytes = android.util.Base64.decode(envelope.ciphertext, android.util.Base64.NO_WRAP)
        return cipher.doFinal(ciphertextBytes)
    }

    companion object {
        private const val TAG = "MnemonicCipher"
        private const val KEY_ALIAS = "pos_satstack_mnemonic_cipher_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val AUTH_VALIDITY_SECONDS = 30

        fun loadOrCreate(): MnemonicCipher {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                // We cannot introspect StrongBox-backing cheaply here; assume
                // whatever we generated stands, and report false (conservative).
                return MnemonicCipher(existing, strongBoxBacked = false)
            }
            return generate()
        }

        private fun generate(): MnemonicCipher {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { generateApi28Plus(strongBox = true) }
                    .onSuccess { return it }
                    .onFailure { exception ->
                        if (exception is StrongBoxUnavailableException) {
                            AppLogger.info(TAG, "StrongBox unavailable for mnemonic key, falling back to TEE")
                        } else {
                            AppLogger.error(TAG, "StrongBox key build failed, falling back", exception)
                        }
                    }
                return generateApi28Plus(strongBox = false)
            }
            return generateApi26(strongBox = false)
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun generateApi28Plus(strongBox: Boolean): MnemonicCipher {
            val builder =
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .applyAuthValidity()
                    .setRandomizedEncryptionRequired(true)

            if (strongBox) builder.setIsStrongBoxBacked(true)

            val key = keyGenerator().apply { init(builder.build()) }.generateKey()
            AppLogger.info(TAG, "Mnemonic key generated (strongBox=$strongBox)")
            return MnemonicCipher(key, strongBoxBacked = strongBox)
        }

        private fun generateApi26(strongBox: Boolean): MnemonicCipher {
            val builder =
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                    .setRandomizedEncryptionRequired(true)

            val key = keyGenerator().apply { init(builder.build()) }.generateKey()
            AppLogger.info(TAG, "Mnemonic key generated (API < 28, strongBox unavailable)")
            return MnemonicCipher(key, strongBoxBacked = strongBox)
        }

        private fun keyGenerator(): KeyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        /**
         * Use modern auth-params API on API 30+ (allows declaring acceptable
         * authenticator classes); fall back to the older duration-only API on
         * 28–29 where the API 30 one isn't available.
         */
        @RequiresApi(Build.VERSION_CODES.P)
        private fun KeyGenParameterSpec.Builder.applyAuthValidity(): KeyGenParameterSpec.Builder {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    AUTH_VALIDITY_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
            }
            return this
        }
    }
}
