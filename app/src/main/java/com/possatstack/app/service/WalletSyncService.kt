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
import com.possatstack.app.wallet.OnChainWalletEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that runs an Esplora wallet sync as soon as the app opens.
 *
 * Started from [MainActivity] on every launch and stops itself as soon as the
 * sync finishes (or fails). START_NOT_STICKY means Android will not restart
 * the service if the process is killed mid-sync.
 *
 * Full-scan vs. incremental is decided by the engine based on its own
 * persisted state (`isFullScanDone` + `CHAIN_BACKEND` comparison). This
 * service stays backend-agnostic.
 */
@AndroidEntryPoint
class WalletSyncService : Service() {
    @Inject lateinit var engine: OnChainWalletEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForegroundCompat(buildNotification())

        serviceScope.launch {
            runSync()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun runSync() {
        if (!engine.hasWallet()) {
            AppLogger.info(TAG, "No wallet found — skipping startup sync")
            return
        }
        runCatching {
            AppLogger.info(TAG, "Startup sync started")
            engine.loadWallet()
            engine.sync()
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
