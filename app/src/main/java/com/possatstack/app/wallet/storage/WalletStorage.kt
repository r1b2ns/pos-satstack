package com.possatstack.app.wallet.storage

import com.possatstack.app.wallet.WalletDescriptor

/**
 * Abstraction for secure persistence of wallet data.
 *
 * Implementations must store data in a way that is protected at rest.
 * No app code outside the DI wiring layer should reference a concrete
 * implementation — always depend on this interface.
 */
interface WalletStorage {

    /**
     * Persists the wallet descriptor and mnemonic (when present) securely.
     * Overwrites any previously stored data.
     */
    fun save(descriptor: WalletDescriptor)

    /**
     * Returns the stored [WalletDescriptor], or null if none has been saved yet.
     * The returned descriptor will have [WalletDescriptor.mnemonic] populated
     * if it was saved during wallet creation.
     */
    fun load(): WalletDescriptor?

    /**
     * Removes all stored wallet data. Use with caution — this is irreversible
     * unless the user has an external backup of their seed phrase.
     * Also resets the full-scan flag so the next wallet load triggers a new
     * full scan.
     */
    fun clear()

    /**
     * Records that at least one full Electrum scan has been completed for
     * the current wallet. After this is called, subsequent syncs will use
     * the faster incremental sync path.
     */
    fun markFullScanDone()

    /**
     * Returns true if [markFullScanDone] has been called for the current
     * wallet, false otherwise (fresh wallet or after [clear]).
     */
    fun isFullScanDone(): Boolean
}
