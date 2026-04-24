package com.possatstack.app.wallet.chain

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.FeeEstimate
import com.possatstack.app.wallet.FeeTarget
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChainDataSource] backed by BDK's built-in `EsploraClient`.
 *
 * Esplora ships inside `bdk-android` (2.3.1+) so no extra dependency is
 * required. The `EsploraClient` is stateless and cheap to construct per call;
 * we keep a lazy instance per-network keyed on a thread-safe field.
 *
 * Error mapping: every [EsploraException] becomes a [WalletError]
 * so callers never see BDK types leaking into the app.
 */
@Singleton
class EsploraChainDataSource
    @Inject
    constructor() : ChainDataSource {
        @Volatile
        private var client: EsploraClient? = null

        @Volatile
        private var clientUrl: String? = null

        override suspend fun broadcastRaw(rawTx: ByteArray): Txid =
            withContext(Dispatchers.IO) {
                val url = resolveUrl()
                AppLogger.info(TAG, "broadcast via $url (${rawTx.size} bytes)")
                try {
                    val transaction = Transaction(rawTx)
                    val txid = transaction.computeTxid().toString()
                    getOrBuildClient(url).broadcast(transaction)
                    Txid(txid)
                } catch (exception: EsploraException) {
                    throw WalletError.ChainSourceUnreachable(exception)
                }
            }

        override suspend fun estimateFees(target: FeeTarget): FeeEstimate =
            withContext(Dispatchers.IO) {
                val url = resolveUrl()
                try {
                    val estimates = getOrBuildClient(url).getFeeEstimates()
                    val rate = estimates.pickClosest(target.blocks)
                    FeeEstimate(satsPerVb = rate.first, targetBlocks = rate.second)
                } catch (exception: EsploraException) {
                    throw WalletError.ChainSourceUnreachable(exception)
                }
            }

        override suspend fun getBlockHeight(): Long =
            withContext(Dispatchers.IO) {
                val url = resolveUrl()
                try {
                    getOrBuildClient(url).getHeight().toLong()
                } catch (exception: EsploraException) {
                    throw WalletError.ChainSourceUnreachable(exception)
                }
            }

        override fun configureFor(network: WalletNetwork) {
            val url = urlFor(network) ?: error("Esplora not configured for network $network")
            if (clientUrl != url) {
                AppLogger.info(TAG, "Reconfiguring EsploraClient (network=$network, url=$url)")
                client = null
                clientUrl = url
            }
        }

        private fun resolveUrl(): String =
            clientUrl ?: error("EsploraChainDataSource used before configureFor(network) was called")

        private fun getOrBuildClient(url: String): EsploraClient {
            val existing = client
            if (existing != null && clientUrl == url) return existing
            val fresh = EsploraClient(url)
            client = fresh
            clientUrl = url
            return fresh
        }

        /**
         * Snap the user's requested confirmation target to the closest one the
         * server actually estimated. Esplora typically returns {1, 2, 3, 4, 5, 6,
         * 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
         * 144, 504, 1008}; picking the closest avoids nulls when the caller asks
         * for e.g. 12 blocks and the server only returned 10 or 15.
         */
        private fun Map<UShort, Double>.pickClosest(targetBlocks: Int): Pair<Double, Int> {
            require(isNotEmpty()) { "Esplora returned empty fee estimates" }
            val (closestTarget, rate) =
                entries.minByOrNull { (t, _) ->
                    kotlin.math.abs(t.toInt() - targetBlocks)
                }!!
            return rate to closestTarget.toInt()
        }

        private fun urlFor(network: WalletNetwork): String? =
            when (network) {
                WalletNetwork.MAINNET -> "https://blockstream.info/api"
                WalletNetwork.SIGNET -> "https://mempool.space/signet/api"
                WalletNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                WalletNetwork.REGTEST -> null
            }

        private companion object {
            const val TAG = "EsploraChainDataSource"
        }
    }
