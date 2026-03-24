package com.possatstack.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.possatstack.app.R
import com.possatstack.app.ui.charge.ChargeScreen
import com.possatstack.app.ui.home.HomeScreen
import com.possatstack.app.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(navController: NavHostController) {
    // Observe backstack so canNavigateBack triggers recomposition on navigation events
    @Suppress("UNUSED_VARIABLE")
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val canNavigateBack = navController.previousBackStackEntry != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.the_bitcoin_machine)) },
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
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<AppDestination.Home> {
                HomeScreen(
                    onMenuEntryClick = { destination ->
                        navController.navigate(destination) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<AppDestination.Charge> {
                ChargeScreen()
            }
            composable<AppDestination.Settings> {
                SettingsScreen()
            }
        }
    }
}
