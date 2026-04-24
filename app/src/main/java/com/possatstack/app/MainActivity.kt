package com.possatstack.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.possatstack.app.navigation.AppNavGraph
import com.possatstack.app.service.WalletSyncService
import com.possatstack.app.ui.theme.PosTheme
import com.possatstack.app.wallet.signer.ActivityHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Extends [FragmentActivity] so [androidx.biometric.BiometricPrompt] can
 * attach its DialogFragment. Compose setContent still works because
 * FragmentActivity extends ComponentActivity underneath.
 *
 * Registers with [ActivityHolder] on resume so the
 * [com.possatstack.app.wallet.signer.AndroidBiometricAuthenticator] can find a
 * live activity whenever it needs to prompt the user. The holder uses a
 * WeakReference and cleans up on pause.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
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
