package com.possatstack.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.possatstack.app.R
import com.possatstack.app.ui.charge.ChargeDetailsScreen
import com.possatstack.app.ui.charge.ChargeScreen
import com.possatstack.app.ui.components.SyncProgressToast
import com.possatstack.app.ui.onboarding.OnboardingSetupScreen
import com.possatstack.app.ui.onboarding.WalletSetupChoice
import com.possatstack.app.ui.onboarding.WelcomeCarouselScreen
import com.possatstack.app.ui.scanqrcode.ScanQRCodeScreen
import com.possatstack.app.ui.settings.SettingsScreen
import com.possatstack.app.ui.theme.BitcoinOrange
import com.possatstack.app.ui.wallet.WalletScreen
import com.possatstack.app.ui.wallet.WalletViewModel
import com.possatstack.app.ui.wallet.import.WalletImportScreen
import com.possatstack.app.ui.wallet.receive.WalletReceiveScreen
import com.possatstack.app.ui.wallet.seedphrase.WalletSeedPhraseScreen
import com.possatstack.app.ui.wallet.send.WalletSendScreen
import com.possatstack.app.ui.wallet.transactions.WalletTransactionsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(navController: NavHostController) {
    // Activity-scoped: the same instance is shared across all wallet screens and
    // allows the sync progress toast to be shown from any destination.
    val walletViewModel: WalletViewModel = hiltViewModel()
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()

    // Wait for the wallet engine to report its current state before deciding
    // the start destination — otherwise the UI would briefly route through
    // onboarding even when a wallet is already configured.
    if (!walletState.isInitialized) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BitcoinOrange)
        }
        return
    }

    val rootDestination: AppDestination =
        if (walletState.hasWallet) AppDestination.Charge else AppDestination.Welcome

    // Recompose on every navigation event so the top bar reacts immediately.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Show back button on every destination except Home.
    // Wrapped in remember(currentBackStackEntry) so it recalculates on every
    // navigation event, not just on initial composition.
    val canNavigateBack =
        remember(currentBackStackEntry) {
            navController.previousBackStackEntry != null
        }

    val isChargeRoute =
        remember(currentBackStackEntry) {
            currentBackStackEntry?.destination?.route?.contains("Charge") == true
        }

    val isOnboardingRoute =
        remember(currentBackStackEntry) {
            val route = currentBackStackEntry?.destination?.route.orEmpty()
            route.contains("Welcome") || route.contains("OnboardingSetup")
        }

    val isSettingsRoute =
        remember(currentBackStackEntry) {
            currentBackStackEntry?.destination?.route?.contains("Settings") == true
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isOnboardingRoute) {
                    CenterAlignedTopAppBar(
                    title = {
                        if (isSettingsRoute) {
                            Text(stringResource(R.string.settings))
                        }
                    },
                    navigationIcon = {
                        if (canNavigateBack) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (isChargeRoute) {
                            IconButton(onClick = {
                                navController.navigate(AppDestination.Settings) {
                                    launchSingleTop = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        }
                    },
                    colors =
                        if (isChargeRoute) {
                            TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.White,
                            )
                        } else {
                            TopAppBarDefaults.centerAlignedTopAppBarColors()
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = rootDestination,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<AppDestination.Welcome> {
                    WelcomeCarouselScreen(
                        onContinue = {
                            navController.navigate(AppDestination.OnboardingSetup) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable<AppDestination.OnboardingSetup> { backStackEntry ->
                    // Listen for the result returned by the QR-scan module.
                    // Once consumed we clear the key so re-entering the screen
                    // (e.g. user backs out and tries again) starts clean.
                    LaunchedEffect(backStackEntry) {
                        backStackEntry.savedStateHandle
                            .getStateFlow<String?>(AppDestination.SCAN_RESULT_KEY, null)
                            .collect { scanned ->
                                if (scanned != null) {
                                    backStackEntry.savedStateHandle
                                        .remove<String>(AppDestination.SCAN_RESULT_KEY)
                                    // TODO: persist the imported xpub via WalletViewModel
                                    // before completing onboarding. For now we just
                                    // advance to the charge screen.
                                    navController.navigate(AppDestination.Charge) {
                                        popUpTo(AppDestination.Welcome) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                    }

                    OnboardingSetupScreen(
                        onContinue = { choice ->
                            when (choice) {
                                WalletSetupChoice.ImportPublicKey ->
                                    navController.navigate(AppDestination.ScanQRCode) {
                                        launchSingleTop = true
                                    }

                                WalletSetupChoice.CreateWallet ->
                                    navController.navigate(AppDestination.Charge) {
                                        popUpTo(AppDestination.Welcome) { inclusive = true }
                                        launchSingleTop = true
                                    }
                            }
                        },
                    )
                }

                composable<AppDestination.Charge> {
                    ChargeScreen(
                        onChargeCreated = { chargeId ->
                            navController.navigate(AppDestination.ChargeDetails(chargeId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable<AppDestination.ChargeDetails> {
                    ChargeDetailsScreen(
                        onDismiss = { navController.popBackStack() },
                    )
                }

                composable<AppDestination.Settings> {
                    SettingsScreen(
                        onNavigate = { destination ->
                            navController.navigate(destination) { launchSingleTop = true }
                        },
                    )
                }

                // ── Wallet ────────────────────────────────────────────────────
                // All wallet screens receive the Activity-scoped WalletViewModel
                // so state changes (import, sync, balance) propagate instantly.

                composable<AppDestination.Wallet> {
                    WalletScreen(
                        onNavigate = { destination ->
                            navController.navigate(destination) { launchSingleTop = true }
                        },
                        viewModel = walletViewModel,
                    )
                }

                composable<AppDestination.WalletImport> {
                    WalletImportScreen(
                        onImported = { navController.popBackStack() },
                        viewModel = walletViewModel,
                    )
                }

                composable<AppDestination.WalletReceive> {
                    WalletReceiveScreen(viewModel = walletViewModel)
                }

                composable<AppDestination.WalletSend> {
                    WalletSendScreen()
                }

                composable<AppDestination.WalletTransactions> {
                    WalletTransactionsScreen(viewModel = walletViewModel)
                }

                composable<AppDestination.WalletSeedPhrase> {
                    WalletSeedPhraseScreen(viewModel = walletViewModel)
                }

                composable<AppDestination.ScanQRCode> {
                    ScanQRCodeScreen(
                        onResult = { value ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(AppDestination.SCAN_RESULT_KEY, value)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }

        // ── Global sync progress toast ─────────────────────────────────────
        // Shown on top of every screen while a sync is running.
        SyncProgressToast(
            progress = walletState.syncProgress,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp),
        )
    }
}
