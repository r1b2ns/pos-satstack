package com.possatstack.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.possatstack.app.R
import com.possatstack.app.ui.charge.ChargeScreen
import com.possatstack.app.ui.home.HomeScreen
import com.possatstack.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Charge : Screen("charge")
    data object Settings : Screen("settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val canNavigateBack = currentRoute != Screen.Home.route

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
            startDestination = Screen.Home.route,
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    innerPadding = innerPadding,
                    onChargeClick = { navController.navigate(Screen.Charge.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.Charge.route) {
                ChargeScreen(innerPadding = innerPadding)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(innerPadding = innerPadding)
            }
        }
    }
}
