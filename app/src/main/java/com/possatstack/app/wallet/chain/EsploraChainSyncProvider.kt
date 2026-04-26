package com.possatstack.app.wallet.chain

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.bitcoin.toWalletError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.Wallet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChainSyncProvider] backed by BDK's `EsploraClient`.
 *
 * Owns its own client instance to keep the engine library-agnostic — the
 * only place the engine still mentions BDK types is the `Wallet` parameter
 * it forwards. Each call constructs a fresh client (cheap, stateless) so
 * we don't have to track network changes here.
 */
@Singleton
class EsploraChainSyncProvider
    @Inject
    constructor() : ChainSyncProvider {
        override suspend fun sync(
            wallet: Wallet,
            network: WalletNetwork,
            fullScan: Boolean,
            onProgress: (SyncProgress) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val url =
                urlFor(network) ?: throw WalletError.ChainSourceUnreachable(
                    IllegalStateException("Esplora not configured for network $network"),
                )

            AppLogger.info(TAG, "Esplora sync start (fullScan=$fullScan, network=$network, url=$url)")
            onProgress(if (fullScan) SyncProgress.FullScan else SyncProgress.Syncing(0))

            try {
                val client = EsploraClient(url)
                val update =
                    if (fullScan) {
                        val request = wallet.startFullScan().build()
                        client.fullScan(request, STOP_GAP, PARALLEL_REQUESTS)
                    } else {
                        val request = wallet.startSyncWithRevealedSpks().build()
                        client.sync(request, PARALLEL_REQUESTS)
                    }
                wallet.applyUpdate(update)
                onProgress(SyncProgress.Idle)
                AppLogger.info(TAG, "Esplora sync complete")
            } catch (exception: Exception) {
                onProgress(SyncProgress.Idle)
                throw exception.toWalletError()
            }
        }

        private fun urlFor(network: WalletNetwork): String? =
            when (network) {
                WalletNetwork.MAINNET -> "https://blockstream.info/api"
                WalletNetwork.SIGNET -> "https://mempool.space/signet/api"
                WalletNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                WalletNetwork.REGTEST -> null
            }

        private companion object {
            const val TAG = "EsploraChainSync"
            const val STOP_GAP: ULong = 20u
            const val PARALLEL_REQUESTS: ULong = 4u
        }
    }
