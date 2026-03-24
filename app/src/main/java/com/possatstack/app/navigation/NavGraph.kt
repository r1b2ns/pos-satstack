package com.possatstack.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.possatstack.app.ui.charge.ChargeScreen
import com.possatstack.app.ui.home.HomeScreen
import com.possatstack.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Charge : Screen("charge")

    data object Settings : Screen("settings")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChargeClick = { navController.navigate(Screen.Charge.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Charge.route) {
            ChargeScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
