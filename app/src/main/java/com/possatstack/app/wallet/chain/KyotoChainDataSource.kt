package com.possatstack.app.wallet.chain

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.FeeEstimate
import com.possatstack.app.wallet.FeeTarget
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChainDataSource] used together with [KyotoChainSyncProvider].
 *
 * Kyoto's CBF client is geared towards filter sync; it does not expose a
 * usable fee oracle (P2P has none) and broadcast happens during a live
 * node session, not as a stateless RPC. So this data source delegates:
 *
 *  - **Broadcast**: forwarded to a public Esplora HTTP endpoint via the
 *    existing [EsploraChainDataSource]. CBF will see the tx in the
 *    mempool on the next P2P refresh; this keeps single-shot broadcast
 *    cheap without spinning up a node.
 *  - **Fee estimation**: same fallback to Esplora HTTP. The phase-5 doc
 *    accepts this trade-off explicitly: "fall back to a tiny mempool.space
 *    HTTP call for fees".
 *  - **Block height**: same fallback.
 *
 * Once an always-on CBF foreground service exists, [broadcastRaw] can
 * route through `CbfClient.broadcast` directly and `estimateFees` can
 * combine `minBroadcastFeerate` with a target-aware oracle.
 */
@Singleton
class KyotoChainDataSource
    @Inject
    constructor(
        private val esploraFallback: EsploraChainDataSource,
    ) : ChainDataSource {
        override fun configureFor(network: WalletNetwork) {
            AppLogger.info(TAG, "configureFor($network) — delegating to Esplora fallback")
            try {
                esploraFallback.configureFor(network)
            } catch (exception: IllegalStateException) {
                throw WalletError.ChainSourceUnreachable(exception)
            }
        }

        override suspend fun broadcastRaw(rawTx: ByteArray): Txid = esploraFallback.broadcastRaw(rawTx)

        override suspend fun estimateFees(target: FeeTarget): FeeEstimate = esploraFallback.estimateFees(target)

        override suspend fun getBlockHeight(): Long = esploraFallback.getBlockHeight()

        private companion object {
            const val TAG = "KyotoChainDataSource"
        }
    }
