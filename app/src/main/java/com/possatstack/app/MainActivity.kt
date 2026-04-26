package com.possatstack.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.possatstack.app.navigation.AppNavGraph
import com.possatstack.app.service.WalletSyncService
import com.possatstack.app.ui.theme.PosTheme
import com.possatstack.app.wallet.signer.ActivityHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Registers with [ActivityHolder] on resume so components that need the
 * currently foregrounded activity (TAPSIGNER NFC reader mode) can find it.
 * The holder uses a WeakReference and cleans up on pause.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var activityHolder: ActivityHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startWalletSync()
        setContent {
            PosTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityHolder.attach(this)
    }

    override fun onPause() {
        activityHolder.detach(this)
        super.onPause()
    }

    private fun startWalletSync() {
        val intent = Intent(this, WalletSyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
