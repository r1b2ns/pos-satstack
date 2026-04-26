package com.possatstack.app.wallet.signer

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.security.KeyStore
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [SignerSecretStore] that persists the mnemonic in
 * [EncryptedSharedPreferences], whose values are AES-256-GCM-encrypted under
 * a hardware-backed [MasterKey] kept in the AndroidKeyStore (StrongBox when
 * available, TEE otherwise).
 *
 * No biometric/PIN prompt is required to save or read the mnemonic: this
 * codebase ships to POS terminals that may not have any biometric sensor.
 * The mnemonic is still protected at rest by the device-bound master key
 * and is never written outside the app's private storage.
 *
 * If the encrypted prefs file is left in an inconsistent state by a previous
 * install (different master key, partially-corrupt blob, etc.) [saveMnemonic]
 * recovers by wiping the master key + prefs and retrying once. Reads do not
 * auto-wipe: they surface [WalletError.SecretStoreUnavailable] so callers
 * can decide whether to recreate the wallet from a backup.
 */
@Singleton
class AndroidKeystoreSignerSecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SignerSecretStore {
        private val utf8: Charset = Charset.forName("UTF-8")

        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        private fun prefs(): SharedPreferences =
            cachedPrefs ?: synchronized(this) {
                cachedPrefs ?: openPrefs().also { cachedPrefs = it }
            }

        private fun openPrefs(): SharedPreferences {
            val masterKey =
                MasterKey.Builder(context, PREFS_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /**
         * Drop the cached handle, the master key, and the prefs file so the
         * next [openPrefs] starts from a clean slate. Used to recover from a
         * stale `EncryptedSharedPreferences` that can no longer decrypt its
         * contents (typical after a key-storage policy change or a leftover
         * prefs file from a previous incompatible app version).
         */
        private fun resetPrefsState() {
            cachedPrefs = null
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(PREFS_MASTER_KEY_ALIAS)
            }.onFailure { exception ->
                AppLogger.warning(TAG, "Failed to delete master key entry: ${exception.javaClass.simpleName}")
            }
            runCatching {
                context.deleteSharedPreferences(PREFS_FILE)
            }.onFailure { exception ->
                AppLogger.warning(TAG, "Failed to delete prefs file: ${exception.javaClass.simpleName}")
            }
        }

        override fun hasMnemonic(): Boolean =
            try {
                prefs().contains(KEY_MNEMONIC)
            } catch (exception: Exception) {
                AppLogger.error(TAG, "hasMnemonic failed: ${describe(exception)}", exception)
                false
            }

        override suspend fun saveMnemonic(
            mnemonic: CharArray,
            network: WalletNetwork,
        ) {
            withContext(Dispatchers.IO) {
                val plain = String(mnemonic)
                try {
                    writeEntries(plain, network)
                } catch (firstFailure: Exception) {
                    AppLogger.error(
                        TAG,
                        "Initial saveMnemonic failed; resetting prefs state and retrying: ${describe(firstFailure)}",
                        firstFailure,
                    )
                    resetPrefsState()
                    try {
                        writeEntries(plain, network)
                    } catch (secondFailure: Exception) {
                        AppLogger.error(
                            TAG,
                            "saveMnemonic still failing after reset: ${describe(secondFailure)}",
                            secondFailure,
                        )
                        throw WalletError.SecretStoreUnavailable(describe(secondFailure))
                    }
                }
            }
        }

        private fun writeEntries(
            mnemonic: String,
            network: WalletNetwork,
        ) {
            prefs().edit(commit = true) {
                putString(KEY_MNEMONIC, mnemonic)
                putString(KEY_NETWORK, network.name)
            }
        }

        override suspend fun readMnemonic(): CharArray =
            withContext(Dispatchers.IO) {
                val stored =
                    try {
                        prefs().getString(KEY_MNEMONIC, null)
                    } catch (exception: Exception) {
                        AppLogger.error(TAG, "readMnemonic failed: ${describe(exception)}", exception)
                        throw WalletError.SecretStoreUnavailable(describe(exception))
                    }
                stored?.toCharArray() ?: throw WalletError.NoWallet
            }

        override suspend fun readSeedBytes(passphrase: CharArray): ByteArray =
            withContext(Dispatchers.IO) {
                val mnemonic = readMnemonic()
                try {
                    deriveBip39Seed(mnemonic, passphrase)
                } finally {
                    mnemonic.zero()
                }
            }

        override fun storedNetwork(): WalletNetwork? =
            try {
                prefs().getString(KEY_NETWORK, null)?.let { name ->
                    runCatching { WalletNetwork.valueOf(name) }.getOrNull()
                }
            } catch (exception: Exception) {
                AppLogger.error(TAG, "storedNetwork failed: ${describe(exception)}", exception)
                null
            }

        override suspend fun wipe() {
            withContext(Dispatchers.IO) {
                runCatching { prefs().edit(commit = true) { clear() } }
                resetPrefsState()
            }
        }

        override fun securityPosture(): SecurityPosture =
            SecurityPosture(
                hardwareBacked = true,
                strongBoxBacked = isStrongBoxAvailable(),
                deviceSecure = isDeviceSecure(),
            )

        private fun isDeviceSecure(): Boolean {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return keyguard?.isDeviceSecure == true
        }

        private fun isStrongBoxAvailable(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        private fun describe(throwable: Throwable): String {
            val parts = mutableListOf<String>()
            var current: Throwable? = throwable
            while (current != null) {
                val message = current.message?.takeIf { it.isNotBlank() }
                parts += if (message != null) "${current.javaClass.simpleName}: $message" else current.javaClass.simpleName
                current = current.cause?.takeIf { it !== current }
            }
            return parts.joinToString(" <- ")
        }

        private fun CharArray.toUtf8Bytes(): ByteArray {
            val buffer = utf8.encode(CharBuffer.wrap(this))
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            if (buffer.hasArray()) buffer.array().fill(0)
            return bytes
        }

        private fun CharArray.zero() {
            fill(Char(0))
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
                normalisedMnemonic.zero()
                normalisedPassphrase.zero()
                saltChars.zero()
                saltBytes.fill(0)
            }
        }

        private companion object {
            const val TAG = "AndroidKeystoreSignerSecretStore"
            const val PREFS_FILE = "mnemonic_secure_prefs"
            const val PREFS_MASTER_KEY_ALIAS = "pos_satstack_mnemonic_prefs_master_key"
            const val KEY_MNEMONIC = "mnemonic_plain"
            const val KEY_NETWORK = "mnemonic_network"

            const val BIP39_ITERATIONS = 2048
            const val BIP39_SEED_BITS = 512
            const val BIP39_SALT_PREFIX = "mnemonic"
        }
    }
