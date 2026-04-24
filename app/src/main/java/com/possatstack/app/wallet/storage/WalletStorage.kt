package com.possatstack.app.wallet.storage

import com.possatstack.app.wallet.WalletDescriptor

/**
 * Storage for reconstructable on-chain wallet data — descriptors, network,
 * active-backend fingerprint, and the full-scan flag.
 *
 * Does **not** handle the mnemonic. That lives in
 * [com.possatstack.app.wallet.signer.SignerSecretStore] with stricter
 * protections (StrongBox when available, biometric gate in Fase 3).
 *
 * Concrete implementations must store descriptors encrypted-at-rest and bound
 * to the device — enough to stop a cold disk read from leaking the xpub — but
 * they do not need to gate access behind user auth.
 */
interface WalletStorage {
    /** Persist the descriptor. Overwrites any existing value. */
    fun save(descriptor: WalletDescriptor)

    /** Return the stored descriptor or null if the wallet hasn't been created yet. */
    fun load(): WalletDescriptor?

    /**
     * Clear descriptor + full-scan flag + chain-backend id. Does NOT wipe the
     * mnemonic — [com.possatstack.app.wallet.signer.SignerSecretStore.wipe]
     * handles that separately so each store can fail independently.
     */
    fun clear()

    /** Mark that at least one full Electrum/Esplora scan has completed. */
    fun markFullScanDone()

    /** Unset the full-scan flag — used by wipe-on-swap after changing backends. */
    fun markFullScanUndone()

    /** True if a full scan was recorded and the cache is still considered valid. */
    fun isFullScanDone(): Boolean

    /**
     * Identifier of the chain backend last used to write the local cache
     * (see [com.possatstack.app.BuildConfig.CHAIN_BACKEND]). Used to detect
     * dev-time backend swaps and wipe the BDK cache before re-syncing.
     */
    fun storedChainBackend(): String?

    /** Record the active chain backend id. Call after a full scan completes. */
    fun markChainBackend(backendId: String)
}
