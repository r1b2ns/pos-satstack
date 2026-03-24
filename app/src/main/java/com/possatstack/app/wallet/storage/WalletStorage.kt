package com.possatstack.app.wallet.storage

import com.possatstack.app.wallet.WalletDescriptor

/**
 * Abstraction for secure persistence of wallet descriptors.
 *
 * Implementations must store data in a way that is protected at rest.
 * No app code outside the DI wiring layer should reference a concrete
 * implementation — always depend on this interface.
 */
interface WalletStorage {

    /**
     * Persists the wallet descriptor securely.
     * Overwrites any previously stored descriptor.
     */
    fun save(descriptor: WalletDescriptor)

    /**
     * Returns the stored [WalletDescriptor], or null if none has been saved yet.
     */
    fun load(): WalletDescriptor?

    /**
     * Removes all stored wallet data. Use with caution — this is irreversible
     * unless the user has an external backup of their seed phrase.
     */
    fun clear()
}
