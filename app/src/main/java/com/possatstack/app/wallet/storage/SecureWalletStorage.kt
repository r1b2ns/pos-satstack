package com.possatstack.app.wallet.storage

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [WalletStorage] backed by [EncryptedSharedPreferences].
 *
 * Values are AES-256-GCM encrypted at rest with a master key held in the
 * Android Keystore — enough to stop a cold disk read from revealing the
 * wallet xpub. This class does NOT store the mnemonic (see
 * [com.possatstack.app.wallet.signer.SignerSecretStore] for that path, which
 * uses a separate file + stricter master-key policy).
 *
 * This class must not be referenced outside the DI wiring layer.
 */
class SecureWalletStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WalletStorage {
        private val prefs by lazy {
            val masterKey =
                MasterKey.Builder(context)
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

        override fun save(descriptor: WalletDescriptor) {
            prefs.edit {
                putString(KEY_EXTERNAL_DESCRIPTOR, descriptor.externalDescriptor)
                putString(KEY_INTERNAL_DESCRIPTOR, descriptor.internalDescriptor)
                putString(KEY_NETWORK, descriptor.network.name)
            }
        }

        override fun load(): WalletDescriptor? {
            val external = prefs.getString(KEY_EXTERNAL_DESCRIPTOR, null) ?: return null
            val internal = prefs.getString(KEY_INTERNAL_DESCRIPTOR, null) ?: return null
            val network =
                prefs.getString(KEY_NETWORK, null)
                    ?.let { runCatching { WalletNetwork.valueOf(it) }.getOrNull() }
                    ?: return null

            return WalletDescriptor(
                externalDescriptor = external,
                internalDescriptor = internal,
                network = network,
            )
        }

        override fun clear() {
            prefs.edit { clear() }
        }

        override fun markFullScanDone() {
            prefs.edit { putBoolean(KEY_FULL_SCAN_DONE, true) }
        }

        override fun markFullScanUndone() {
            prefs.edit { putBoolean(KEY_FULL_SCAN_DONE, false) }
        }

        override fun isFullScanDone(): Boolean = prefs.getBoolean(KEY_FULL_SCAN_DONE, false)

        override fun storedChainBackend(): String? = prefs.getString(KEY_CHAIN_BACKEND, null)

        override fun markChainBackend(backendId: String) {
            prefs.edit { putString(KEY_CHAIN_BACKEND, backendId) }
        }

        private companion object {
            const val PREFS_FILE = "wallet_secure_prefs"
            const val KEY_EXTERNAL_DESCRIPTOR = "external_descriptor"
            const val KEY_INTERNAL_DESCRIPTOR = "internal_descriptor"
            const val KEY_NETWORK = "network"
            const val KEY_FULL_SCAN_DONE = "full_scan_done"
            const val KEY_CHAIN_BACKEND = "chain_backend"
        }
    }
