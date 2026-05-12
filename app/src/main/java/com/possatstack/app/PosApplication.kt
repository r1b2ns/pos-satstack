package com.possatstack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.possatstack.app.service.WalletSyncService
import com.possatstack.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import org.bitcoindevkit.cktap.uniffiEnsureInitialized

@HiltAndroidApp
class PosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        loadCktapNative()
        createNotificationChannels()
    }

    /**
     * Force the cktap-android (UniFFI) native library to load eagerly so the
     * first TAPSIGNER tap doesn't pay JNI initialization cost on the UI thread.
     */
    private fun loadCktapNative() {
        runCatching { uniffiEnsureInitialized() }
            .onFailure { AppLogger.error(TAG, "cktap native init failed", it) }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val syncChannel =
            NotificationChannel(
                WalletSyncService.CHANNEL_ID,
                getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.sync_channel_description)
                setShowBadge(false)
            }

        manager.createNotificationChannel(syncChannel)
    }

    private companion object {
        const val TAG = "PosApplication"
    }
}
