package com.possatstack.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.possatstack.app.R
import com.possatstack.app.navigation.AppDestination
import com.possatstack.app.ui.theme.PosTheme

@Composable
fun SettingsScreen(
    onNavigate: (AppDestination) -> Unit = {},
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.wallet)) },
                supportingContent = { Text(stringResource(R.string.wallet_settings_subtitle)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable { onNavigate(AppDestination.Wallet) },
            )
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PosTheme { SettingsScreen() }
}
