package com.possatstack.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.possatstack.app.R
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.WalletRepository
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that runs an Electrum wallet sync as soon as the app opens.
 *
 * It is started from [MainActivity] on every launch and stops itself as soon
 * as the sync finishes (or fails). Using START_NOT_STICKY means Android will
 * not restart the service if the process is killed mid-sync.
 *
 * The service decides between a full scan and an incremental sync based on
 * [WalletStorage.isFullScanDone]. On success it marks the full scan as done
 * so future launches use the faster incremental path.
 */
@AndroidEntryPoint
class WalletSyncService : Service() {

    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var walletStorage: WalletStorage

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        serviceScope.launch {
            runSync()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun runSync() {
        val descriptor = walletStorage.load() ?: run {
            AppLogger.info(TAG, "No wallet found — skipping startup sync")
            return
        }

        runCatching {
            val isFullScan = !walletStorage.isFullScanDone()
            AppLogger.info(TAG, "Startup sync started (fullScan=$isFullScan, network=${descriptor.network})")

            walletRepository.loadWallet(descriptor)
            walletRepository.syncWallet(descriptor.network, isFullScan)

            if (isFullScan) walletStorage.markFullScanDone()
            AppLogger.info(TAG, "Startup sync completed successfully")
        }.onFailure { exception ->
            AppLogger.error(TAG, "Startup sync failed", exception)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(getString(R.string.sync_notification_message))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "WalletSyncService"
        const val CHANNEL_ID = "wallet_sync"
        const val NOTIFICATION_ID = 1001
    }
}
