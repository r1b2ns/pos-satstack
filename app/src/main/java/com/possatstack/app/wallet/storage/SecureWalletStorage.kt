package com.possatstack.app.wallet.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork

/**
 * [WalletStorage] implementation backed by [EncryptedSharedPreferences].
 *
 * Keys are encrypted with AES-256-SIV and values with AES-256-GCM.
 * The master key is generated and stored in the Android Keystore, which means
 * the data is bound to the device and cannot be extracted even if the device
 * storage is read directly.
 *
 * This class must not be referenced anywhere outside the DI wiring layer.
 */
class SecureWalletStorage(context: Context) : WalletStorage {

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun save(descriptor: WalletDescriptor) {
        prefs.edit()
            .putString(KEY_EXTERNAL_DESCRIPTOR, descriptor.externalDescriptor)
            .putString(KEY_INTERNAL_DESCRIPTOR, descriptor.internalDescriptor)
            .putString(KEY_NETWORK, descriptor.network.name)
            .apply()
    }

    override fun load(): WalletDescriptor? {
        val external = prefs.getString(KEY_EXTERNAL_DESCRIPTOR, null) ?: return null
        val internal = prefs.getString(KEY_INTERNAL_DESCRIPTOR, null) ?: return null
        val network = prefs.getString(KEY_NETWORK, null)
            ?.let { runCatching { WalletNetwork.valueOf(it) }.getOrNull() }
            ?: return null

        return WalletDescriptor(
            externalDescriptor = external,
            internalDescriptor = internal,
            network = network,
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "wallet_secure_prefs"
        const val KEY_EXTERNAL_DESCRIPTOR = "external_descriptor"
        const val KEY_INTERNAL_DESCRIPTOR = "internal_descriptor"
        const val KEY_NETWORK = "network"
    }
}
