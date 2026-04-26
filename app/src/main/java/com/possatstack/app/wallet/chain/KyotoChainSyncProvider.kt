package com.possatstack.app.wallet.chain

import android.content.Context
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.bitcoin.toWalletError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bitcoindevkit.CbfBuilder
import org.bitcoindevkit.CbfClient
import org.bitcoindevkit.CbfComponents
import org.bitcoindevkit.CbfException
import org.bitcoindevkit.Info
import org.bitcoindevkit.ScanType
import org.bitcoindevkit.Wallet
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * [ChainSyncProvider] backed by BDK's built-in CBF (BIP-157/158) light
 * client — a.k.a. the Kyoto integration.
 *
 * ### Lifecycle
 *
 * Each call:
 *  1. Builds a [CbfComponents] (`CbfClient` + `CbfNode`) bound to the
 *     wallet, with persistence under `noBackupFilesDir/kyoto/`.
 *  2. Spawns the node loop in a child coroutine — `CbfNode.run()` is a
 *     blocking native call that returns when [CbfClient.shutdown] is invoked.
 *  3. Drains [CbfClient.update] in the calling coroutine, applying each
 *     [org.bitcoindevkit.Update] to the wallet.
 *  4. Listens to [CbfClient.nextInfo] for [Info.Progress] events — these
 *     drive the UI's [SyncProgress.Syncing] percentage.
 *  5. Considers the scan "settled" when no new update arrives within
 *     [SETTLE_TIMEOUT_MS]. It then calls `client.shutdown()` and lets
 *     the node + info loops finish.
 *
 * ### Limitations (intentional, documented in docs/phase-5.md)
 *
 *  - Bootstrap-per-call. CBF is naturally a long-running node; doing a
 *    full bootstrap on every `sync()` is wasteful. Moving the node to a
 *    foreground service is follow-up work.
 *  - DNS-seed only. We don't ship a curated peer list; CBF falls back
 *    to the BDK-bundled DNS seeds. Provide [org.bitcoindevkit.Peer]s
 *    explicitly via the builder if a deployment needs deterministic peering.
 *  - Settle heuristic. Without an explicit "scan complete" event we
 *    rely on update-stream silence. Acceptable for incremental sync;
 *    full scans on mainnet should set [SETTLE_TIMEOUT_MS] higher (or
 *    use the recovery-checkpoint variant once exposed).
 */
@Singleton
class KyotoChainSyncProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ChainSyncProvider {
        private val dataDir: File
            get() = File(context.noBackupFilesDir, "kyoto").apply { if (!exists()) mkdirs() }

        override suspend fun sync(
            wallet: Wallet,
            network: WalletNetwork,
            fullScan: Boolean,
            onProgress: (SyncProgress) -> Unit,
        ) = withContext(Dispatchers.IO) {
            AppLogger.info(TAG, "Kyoto sync start (fullScan=$fullScan, network=$network)")
            onProgress(if (fullScan) SyncProgress.FullScan else SyncProgress.Syncing(0))

            val components =
                try {
                    CbfBuilder()
                        .dataDir(dataDir.absolutePath)
                        .scanType(if (fullScan) RECOVERY_SCAN else ScanType.Sync)
                        .build(wallet)
                } catch (exception: Exception) {
                    onProgress(SyncProgress.Idle)
                    throw exception.toWalletError()
                }

            try {
                runScan(components, wallet, onProgress)
                AppLogger.info(TAG, "Kyoto sync settled")
            } catch (exception: CbfException) {
                throw WalletError.ChainSourceUnreachable(exception)
            } catch (exception: Exception) {
                throw exception.toWalletError()
            } finally {
                components.tryShutdown()
                onProgress(SyncProgress.Idle)
            }
        }

        /**
         * Runs the node + drains updates + listens for progress until the
         * update stream goes silent for [SETTLE_TIMEOUT_MS]. Node and info
         * loops are launched as siblings inside [coroutineScope] so a
         * failure cancels the whole tree cleanly.
         */
        private suspend fun runScan(
            components: CbfComponents,
            wallet: Wallet,
            onProgress: (SyncProgress) -> Unit,
        ) = coroutineScope {
            val node = components.node
            val client = components.client

            val nodeJob: Job =
                launch(Dispatchers.IO) {
                    try {
                        node.run()
                    } catch (exception: Exception) {
                        AppLogger.warning(TAG, "node.run() returned with: ${exception.message}")
                    }
                }

            val infoJob: Job =
                launch(Dispatchers.IO) {
                    while (isActive && client.isRunning()) {
                        val event =
                            runCatching { client.nextInfo() }.getOrNull() ?: return@launch
                        if (event is Info.Progress) {
                            val percent = (event.filtersDownloadedPercent * 100f).toInt().coerceIn(0, 100)
                            onProgress(SyncProgress.Syncing(percent))
                        }
                    }
                }

            try {
                drainUpdates(client, wallet, onProgress)
            } finally {
                infoJob.cancel()
                nodeJob.cancel()
            }
        }

        /**
         * Pull updates one at a time, each one applied to [wallet], until
         * [SETTLE_TIMEOUT_MS] passes without a new update — the heuristic
         * we use as a stand-in for "scan complete".
         */
        private suspend fun drainUpdates(
            client: CbfClient,
            wallet: Wallet,
            onProgress: (SyncProgress) -> Unit,
        ) {
            var totalApplied = 0
            while (coroutineContext[Job]?.isActive == true) {
                val update =
                    withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                        try {
                            client.update()
                        } catch (exception: CbfException) {
                            AppLogger.warning(TAG, "client.update() failed: ${exception.message}")
                            null
                        }
                    } ?: break

                wallet.applyUpdate(update)
                totalApplied++
                onProgress(SyncProgress.Syncing(99))
                AppLogger.info(TAG, "Kyoto applied update #$totalApplied")
            }
            AppLogger.info(TAG, "Kyoto drainUpdates settled after $totalApplied updates")
        }

        private fun CbfComponents.tryShutdown() {
            try {
                client.shutdown()
            } catch (exception: Exception) {
                AppLogger.warning(TAG, "client.shutdown raised: ${exception.message}")
            }
        }

        private companion object {
            const val TAG = "KyotoChainSync"

            /**
             * No new updates within this window → assume the scan settled. CBF
             * doesn't expose an explicit "synced" event today; bumping this is
             * the lever for slow-bootstrap mainnet runs.
             */
            const val SETTLE_TIMEOUT_MS: Long = 8_000L

            /**
             * For a full scan we use the SDK's [ScanType.Sync] today because
             * [ScanType.Recovery] requires a [org.bitcoindevkit.RecoveryPoint]
             * checkpoint that the engine does not surface yet. The wipe-on-
             * backend-swap path keeps recovery from being silently skipped —
             * the BDK wallet still has its descriptors and CBF will catch up
             * via the regular scan on first run.
             */
            val RECOVERY_SCAN: ScanType = ScanType.Sync
        }
    }
