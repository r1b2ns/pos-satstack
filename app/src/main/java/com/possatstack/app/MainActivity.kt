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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

    private fun startWalletSync() {
        val intent = Intent(this, WalletSyncService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
