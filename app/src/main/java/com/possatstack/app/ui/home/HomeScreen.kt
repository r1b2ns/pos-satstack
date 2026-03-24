package com.possatstack.app.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.possatstack.app.R
import com.possatstack.app.ui.theme.BitcoinOrange
import com.possatstack.app.ui.theme.PosTheme

sealed class HomeDestination(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val iconTint: Color? = null,
) {
    data object Charge : HomeDestination(
        titleRes = R.string.charge,
        subtitleRes = R.string.charge_subtitle,
        icon = Icons.Default.CurrencyBitcoin,
        iconTint = BitcoinOrange,
    )

    data object Settings : HomeDestination(
        titleRes = R.string.settings,
        subtitleRes = R.string.settings_subtitle,
        icon = Icons.Default.Settings,
    )
}

@Composable
fun HomeScreen(
    innerPadding: PaddingValues = PaddingValues(),
    onChargeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val items = listOf(
        HomeDestination.Charge to onChargeClick,
        HomeDestination.Settings to onSettingsClick,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items) { (destination, onClick) ->
            MenuCard(destination = destination, onClick = onClick)
        }
    }
}

@Composable
private fun MenuCard(
    destination: HomeDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.titleRes),
                modifier = Modifier.size(48.dp),
                tint = destination.iconTint ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(destination.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PosTheme { HomeScreen() }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    PosTheme { HomeScreen() }
}

@Preview(showBackground = true)
@Composable
private fun MenuCardChargePreview() {
    PosTheme {
        MenuCard(destination = HomeDestination.Charge, onClick = {})
    }
}
