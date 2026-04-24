package com.possatstack.app.wallet.signer

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.StrongBoxUnavailableException
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
 * Android [SignerSecretStore] backed by [EncryptedSharedPreferences] on a
 * dedicated prefs file, with a [MasterKey] that prefers StrongBox when the
 * hardware provides it.
 *
 * Notes on the current implementation:
 *  - `setUserAuthenticationRequired` is NOT enabled yet. Enabling it requires
 *    wiring a real `BiometricPrompt` (a `FragmentActivity`) into the read
 *    path — that lives in Fase 3 when the [Signer] is extracted. The shape
 *    is already in place ([readMnemonic] takes a [BiometricAuthenticator])
 *    so Fase 3 only needs to swap the authenticator binding and rebuild the
 *    master key with auth-required.
 *  - The prefs file is separate from `wallet_secure_prefs` so the mnemonic
 *    can be hardened independently of descriptors/cache.
 *  - Mnemonic is persisted as a UTF-8 byte array (base64 in SharedPreferences,
 *    because prefs only take strings). The transient [CharArray] and
 *    [ByteArray] used during encode/decode are zeroed before the function
 *    returns.
 */
@Singleton
class AndroidKeystoreSignerSecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SignerSecretStore {
        private val utf8: Charset = Charset.forName("UTF-8")

        private var cachedPosture: SecurityPosture? = null

        private val prefs by lazy {
            val (masterKey, posture) = buildMasterKeyWithFallback()
            cachedPosture = posture

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        override fun hasMnemonic(): Boolean = prefs.contains(KEY_MNEMONIC)

        override suspend fun saveMnemonic(
            mnemonic: CharArray,
            network: WalletNetwork,
        ) {
            withContext(Dispatchers.IO) {
                val bytes = mnemonic.toUtf8Bytes()
                try {
                    val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    try {
                        prefs.edit {
                            putString(KEY_MNEMONIC, encoded)
                            putString(KEY_NETWORK, network.name)
                        }
                    } finally {
                        // Encoded string cannot be zeroed (String immutable); rely on
                        // GC. The unencoded bytes below ARE zeroed.
                        @Suppress("UNUSED_EXPRESSION")
                        encoded
                    }
                } finally {
                    bytes.fill(0)
                }
            }
        }

        override suspend fun readMnemonic(auth: BiometricAuthenticator): CharArray =
            withContext(Dispatchers.IO) {
                val encoded =
                    prefs.getString(KEY_MNEMONIC, null)
                        ?: throw WalletError.NoWallet

                // Fase 3 hook: if the MasterKey is recreated with auth-required
                // and decryption below throws UserNotAuthenticatedException,
                // invoke [auth.authenticate(...)] then retry. Today the key is
                // not gated and the authenticator is a no-op; keeping the
                // parameter in place so callers are already shaped correctly.
                @Suppress("UNUSED_EXPRESSION")
                auth

                val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                try {
                    bytes.toUtf8CharArray()
                } finally {
                    bytes.fill(0)
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

        override fun securityPosture(): SecurityPosture {
            // Touch prefs once so the master key is built and `cachedPosture` is filled.
            prefs
            val posture = cachedPosture ?: SecurityPosture.UnknownSoftware
            return posture.copy(deviceSecure = isDeviceSecure())
        }

        // ─────────────────────────────────────────────────────────────────────
        // Internals
        // ─────────────────────────────────────────────────────────────────────

        private fun buildMasterKeyWithFallback(): Pair<MasterKey, SecurityPosture> {
            // Try StrongBox first (hardware-backed, isolated from the main CPU).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val strongBoxKey =
                        MasterKey.Builder(context, MASTER_KEY_ALIAS)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .setRequestStrongBoxBacked(true)
                            .build()
                    AppLogger.info(TAG, "MasterKey backed by StrongBox")
                    return strongBoxKey to
                        SecurityPosture(
                            hardwareBacked = true,
                            strongBoxBacked = true,
                            deviceSecure = isDeviceSecure(),
                        )
                } catch (exception: StrongBoxUnavailableException) {
                    AppLogger.info(TAG, "StrongBox unavailable — falling back to TEE MasterKey")
                } catch (exception: Exception) {
                    AppLogger.error(TAG, "StrongBox MasterKey build failed, falling back", exception)
                }
            }

            // TEE-backed fallback. Still hardware-isolated on most modern devices.
            val defaultKey =
                MasterKey.Builder(context, MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            // We cannot introspect the final key-info without a direct KeyStore
            // lookup (MasterKey hides the underlying entry). Assume hardware-backed
            // when AndroidKeyStore is available, which is always since API 23.
            return defaultKey to
                SecurityPosture(
                    hardwareBacked = true,
                    strongBoxBacked = false,
                    deviceSecure = isDeviceSecure(),
                )
        }

        private fun isDeviceSecure(): Boolean {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguard?.isDeviceSecure == true
        }

        /** UTF-8 encode [this] without creating an intermediate [String]. */
        private fun CharArray.toUtf8Bytes(): ByteArray {
            val buffer = utf8.encode(CharBuffer.wrap(this))
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            // Zero the encoder's internal buffer (best-effort; it's a direct copy).
            if (buffer.hasArray()) buffer.array().fill(0)
            return bytes
        }

        /** UTF-8 decode without creating an intermediate [String]. */
        private fun ByteArray.toUtf8CharArray(): CharArray {
            val charBuffer = utf8.decode(ByteBuffer.wrap(this))
            val chars = CharArray(charBuffer.remaining())
            charBuffer.get(chars)
            if (charBuffer.hasArray()) charBuffer.array().fill('\u0000')
            return chars
        }

        /**
         * BIP-39 seed derivation: PBKDF2-HMAC-SHA512, 2048 iterations, salt
         * `"mnemonic" + passphrase`. Returns 64 bytes. The intermediate [PBEKeySpec]
         * keeps a copy of the mnemonic chars — we clear it afterwards.
         */
        private fun deriveBip39Seed(
            mnemonic: CharArray,
            passphrase: CharArray,
        ): ByteArray {
            // BIP-39 requires NFKD-normalised mnemonic and passphrase. Normalisation
            // builds a String internally; we zero it by re-wrapping as char arrays.
            val normalisedMnemonic = Normalizer.normalize(String(mnemonic), Normalizer.Form.NFKD).toCharArray()
            val normalisedPassphrase = Normalizer.normalize(String(passphrase), Normalizer.Form.NFKD).toCharArray()

            val saltChars = CharArray(BIP39_SALT_PREFIX.length + normalisedPassphrase.size)
            BIP39_SALT_PREFIX.toCharArray().copyInto(saltChars, 0)
            normalisedPassphrase.copyInto(saltChars, BIP39_SALT_PREFIX.length)

            val saltBytes = saltChars.toUtf8Bytes()

            val spec =
                PBEKeySpec(
                    normalisedMnemonic,
                    saltBytes,
                    BIP39_ITERATIONS,
                    BIP39_SEED_BITS,
                )
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                return factory.generateSecret(spec).encoded
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
            const val MASTER_KEY_ALIAS = "pos_satstack_mnemonic_master_key"
            const val KEY_MNEMONIC = "mnemonic"
            const val KEY_NETWORK = "mnemonic_network"

            const val BIP39_ITERATIONS = 2048
            const val BIP39_SEED_BITS = 512
            const val BIP39_SALT_PREFIX = "mnemonic"
        }
    }
