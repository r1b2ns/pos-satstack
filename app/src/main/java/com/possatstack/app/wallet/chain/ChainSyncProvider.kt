package com.possatstack.app.wallet.chain

import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletNetwork
import org.bitcoindevkit.Wallet

/**
 * Engine-side abstraction for "do a chain scan and apply the results to the
 * BDK wallet". Sister of [ChainDataSource]: the latter handles single-shot
 * RPC-style queries (broadcast, fee estimate, height); this one drives the
 * full multi-step scan that wires the wallet up to the chain.
 *
 * Two implementations exist today:
 *  - [EsploraChainSyncProvider] — HTTP-based; one fullScan / sync call per
 *    invocation. Cheap, snappy, depends on a public Esplora server.
 *  - [KyotoChainSyncProvider] — BIP-157/158 light client; bootstraps a P2P
 *    node on each call, runs filter download until updates settle, applies
 *    each update to the wallet, and tears the node down. Self-sovereign,
 *    slower on first scan, no fee oracle.
 *
 * The interface is intentionally narrower than [ChainDataSource]: this
 * lives inside the engine and never needs to be observed directly by UI
 * code — progress reaches the UI through [com.possatstack.app.wallet.SyncProgress].
 */
interface ChainSyncProvider {
    /**
     * Run a single scan cycle against [wallet]. Implementations apply
     * updates internally; the caller persists the wallet afterwards.
     *
     * @param wallet active BDK wallet (already mutex-protected by caller).
     * @param network used by the provider to pick endpoints / peer sets.
     * @param fullScan true to start from genesis / wallet birth (after a
     *        wipe-on-backend-swap or a fresh import); false for an
     *        incremental sync from the last persisted state.
     * @param onProgress receives [SyncProgress.FullScan] / [SyncProgress.Syncing] /
     *        [SyncProgress.Idle]. The provider is responsible for
     *        emitting at least one terminal [SyncProgress.Idle].
     */
    suspend fun sync(
        wallet: Wallet,
        network: WalletNetwork,
        fullScan: Boolean,
        onProgress: (SyncProgress) -> Unit,
    )
}
