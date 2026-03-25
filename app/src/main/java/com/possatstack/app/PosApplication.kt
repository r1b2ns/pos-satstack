package com.possatstack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.possatstack.app.service.WalletSyncService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val syncChannel = NotificationChannel(
            WalletSyncService.CHANNEL_ID,
            getString(R.string.sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.sync_channel_description)
            setShowBadge(false)
        }

        manager.createNotificationChannel(syncChannel)
    }
}
