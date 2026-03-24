package com.possatstack.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.possatstack.app.R
import com.possatstack.app.ui.theme.BitcoinOrange

sealed class HomeMenuEntry(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val destination: AppDestination,
) {
    data object Charge : HomeMenuEntry(
        titleRes = R.string.charge,
        subtitleRes = R.string.charge_subtitle,
        icon = Icons.Default.CurrencyBitcoin,
        iconTint = BitcoinOrange,
        destination = AppDestination.Charge,
    )

    data object Settings : HomeMenuEntry(
        titleRes = R.string.settings,
        subtitleRes = R.string.settings_subtitle,
        icon = Icons.Default.Settings,
        destination = AppDestination.Settings,
    )

    companion object {
        val all: List<HomeMenuEntry> = listOf(Charge, Settings)
    }
}
