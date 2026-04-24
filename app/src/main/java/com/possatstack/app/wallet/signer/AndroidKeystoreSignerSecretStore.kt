package com.possatstack.app.wallet.signer

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [SignerSecretStore] with two-layer protection for the mnemonic:
 *
 *  1. The **prefs file** (`mnemonic_secure_prefs`) is encrypted at rest with
 *     an [EncryptedSharedPreferences] instance backed by a non-auth-required
 *     [MasterKey]. It stores metadata (network, presence flag) and the
 *     mnemonic ciphertext blob. Reads of metadata do not prompt the user.
 *
 *  2. The **mnemonic payload itself** is wrapped a second time by a
 *     [MnemonicCipher] whose underlying AndroidKeyStore key has
 *     `setUserAuthenticationRequired(true)` with a 30-second validity. Reading
 *     the plaintext therefore always requires a recent biometric/PIN prompt.
 *
 * On cold boot, [readMnemonic] catches [UserNotAuthenticatedException], calls
 * [BiometricAuthenticator.authenticate] to unlock all time-bound Keystore keys,
 * and retries once. If the user cancels, [WalletError.SecretStoreUnavailable]
 * is thrown.
 */
@Singleton
class AndroidKeystoreSignerSecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SignerSecretStore {
        private val utf8: Charset = Charset.forName("UTF-8")

        private val prefs by lazy {
            val masterKey =
                MasterKey.Builder(context, PREFS_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private val mnemonicCipher: MnemonicCipher by lazy { MnemonicCipher.loadOrCreate() }

        override fun hasMnemonic(): Boolean = prefs.contains(KEY_CIPHERTEXT)

        override suspend fun saveMnemonic(
            mnemonic: CharArray,
            network: WalletNetwork,
        ) {
            withContext(Dispatchers.IO) {
                val bytes = mnemonic.toUtf8Bytes()
                try {
                    val envelope = mnemonicCipher.encrypt(bytes)
                    prefs.edit {
                        putString(KEY_CIPHERTEXT, envelope.ciphertext)
                        putString(KEY_IV, envelope.iv)
                        putString(KEY_NETWORK, network.name)
                    }
                } finally {
                    bytes.fill(0)
                }
            }
        }

        override suspend fun readMnemonic(auth: BiometricAuthenticator): CharArray =
            withContext(Dispatchers.IO) {
                val envelope = loadEnvelopeOrThrow()
                val plaintext = decryptWithRetry(envelope, auth)
                try {
                    plaintext.toUtf8CharArray()
                } finally {
                    plaintext.fill(0)
                }
            }

        override suspend fun readSeedBytes(
            auth: BiometricAuthenticator,
            passphrase: CharArray,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val mnemonic = readMnemonic(auth)
                try {
                    deriveBip39Seed(mnemonic, passphrase)
                } finally {
                    mnemonic.fill('\u0000')
                }
            }

        override fun storedNetwork(): WalletNetwork? =
            prefs.getString(KEY_NETWORK, null)?.let { name ->
                runCatching { WalletNetwork.valueOf(name) }.getOrNull()
            }

        override suspend fun wipe() {
            withContext(Dispatchers.IO) {
                prefs.edit { clear() }
            }
        }

        override fun securityPosture(): SecurityPosture =
            SecurityPosture(
                hardwareBacked = true,
                strongBoxBacked = mnemonicCipher.strongBoxBacked,
                deviceSecure = isDeviceSecure(),
            )

        // ─────────────────────────────────────────────────────────────────────
        //  Internals
        // ─────────────────────────────────────────────────────────────────────

        private fun loadEnvelopeOrThrow(): MnemonicCipher.Envelope {
            val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: throw WalletError.NoWallet
            val iv = prefs.getString(KEY_IV, null) ?: throw WalletError.NoWallet
            return MnemonicCipher.Envelope(ciphertext = ciphertext, iv = iv)
        }

        /**
         * Decrypt, catching [UserNotAuthenticatedException] once to surface the
         * biometric prompt. A second failure is surfaced as [WalletError].
         */
        private suspend fun decryptWithRetry(
            envelope: MnemonicCipher.Envelope,
            auth: BiometricAuthenticator,
        ): ByteArray =
            try {
                mnemonicCipher.decrypt(envelope)
            } catch (exception: UserNotAuthenticatedException) {
                when (val result = auth.authenticate("Access wallet secret")) {
                    is AuthResult.Authenticated -> {
                        try {
                            mnemonicCipher.decrypt(envelope)
                        } catch (retryException: UserNotAuthenticatedException) {
                            AppLogger.error(TAG, "Keystore still not authenticated after prompt", retryException)
                            throw WalletError.SecretStoreUnavailable("auth not registered")
                        }
                    }
                    is AuthResult.Cancelled -> throw WalletError.SecretStoreUnavailable("user cancelled")
                    is AuthResult.Failed -> throw WalletError.SecretStoreUnavailable(result.reason)
                }
            }

        private fun isDeviceSecure(): Boolean {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguard?.isDeviceSecure == true
        }

        private fun CharArray.toUtf8Bytes(): ByteArray {
            val buffer = utf8.encode(CharBuffer.wrap(this))
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            if (buffer.hasArray()) buffer.array().fill(0)
            return bytes
        }

        private fun ByteArray.toUtf8CharArray(): CharArray {
            val charBuffer = utf8.decode(ByteBuffer.wrap(this))
            val chars = CharArray(charBuffer.remaining())
            charBuffer.get(chars)
            if (charBuffer.hasArray()) charBuffer.array().fill('\u0000')
            return chars
        }

        private fun deriveBip39Seed(
            mnemonic: CharArray,
            passphrase: CharArray,
        ): ByteArray {
            val normalisedMnemonic = Normalizer.normalize(String(mnemonic), Normalizer.Form.NFKD).toCharArray()
            val normalisedPassphrase = Normalizer.normalize(String(passphrase), Normalizer.Form.NFKD).toCharArray()

            val saltChars = CharArray(BIP39_SALT_PREFIX.length + normalisedPassphrase.size)
            BIP39_SALT_PREFIX.toCharArray().copyInto(saltChars, 0)
            normalisedPassphrase.copyInto(saltChars, BIP39_SALT_PREFIX.length)

            val saltBytes = saltChars.toUtf8Bytes()

            val spec = PBEKeySpec(normalisedMnemonic, saltBytes, BIP39_ITERATIONS, BIP39_SEED_BITS)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                normalisedMnemonic.fill('\u0000')
                normalisedPassphrase.fill('\u0000')
                saltChars.fill('\u0000')
                saltBytes.fill(0)
            }
        }

        private companion object {
            const val TAG = "SignerSecretStore"
            const val PREFS_FILE = "mnemonic_secure_prefs"
            const val PREFS_MASTER_KEY_ALIAS = "pos_satstack_mnemonic_prefs_master_key"
            const val KEY_CIPHERTEXT = "mnemonic_ciphertext"
            const val KEY_IV = "mnemonic_iv"
            const val KEY_NETWORK = "mnemonic_network"

            const val BIP39_ITERATIONS = 2048
            const val BIP39_SEED_BITS = 512
            const val BIP39_SALT_PREFIX = "mnemonic"
        }
    }
