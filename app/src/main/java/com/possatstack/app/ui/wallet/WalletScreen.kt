package com.possatstack.app.ui.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.navigation.AppDestination
import com.possatstack.app.wallet.SyncProgress

@Composable
fun WalletScreen(
    onNavigate: (AppDestination) -> Unit,
    viewModel: WalletViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Balance card ──────────────────────────────────────────────────
            if (state.hasWallet) {
                BalanceCard(
                    balanceSats = state.balanceSats,
                    isSyncing = state.syncProgress != SyncProgress.Idle,
                    onSyncClick = { viewModel.syncWallet() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.no_wallet_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // ── Wallet Management ─────────────────────────────────────────────

            SectionHeader(stringResource(R.string.wallet_management))

            WalletListItem(
                icon = Icons.Default.Add,
                title = stringResource(R.string.wallet_create),
                subtitle = stringResource(R.string.wallet_create_subtitle),
                onClick = { showCreateDialog = true },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

            WalletListItem(
                icon = Icons.Default.Download,
                title = stringResource(R.string.wallet_import),
                subtitle = stringResource(R.string.wallet_import_subtitle),
                onClick = { onNavigate(AppDestination.WalletImport) },
            )

            // ── Transactions ──────────────────────────────────────────────────

            SectionHeader(stringResource(R.string.transactions))

            WalletListItem(
                icon = Icons.AutoMirrored.Filled.CallReceived,
                title = stringResource(R.string.wallet_receive),
                subtitle = stringResource(R.string.wallet_receive_subtitle),
                enabled = state.hasWallet,
                onClick = { onNavigate(AppDestination.WalletReceive) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

            WalletListItem(
                icon = Icons.AutoMirrored.Filled.CallMade,
                title = stringResource(R.string.wallet_send),
                subtitle = stringResource(R.string.wallet_send_subtitle),
                enabled = state.hasWallet,
                onClick = { onNavigate(AppDestination.WalletSend) },
            )

            // ── Security ──────────────────────────────────────────────────────

            SectionHeader(stringResource(R.string.security))

            WalletListItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.wallet_seed_phrase),
                subtitle = stringResource(R.string.wallet_seed_phrase_subtitle),
                enabled = state.hasWallet && viewModel.getMnemonic() != null,
                onClick = { onNavigate(AppDestination.WalletSeedPhrase) },
            )

            // ── Danger zone ───────────────────────────────────────────────────

            SectionHeader(stringResource(R.string.danger_zone))

            WalletListItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.wallet_delete),
                subtitle = stringResource(R.string.wallet_delete_subtitle),
                enabled = state.hasWallet,
                isDestructive = true,
                onClick = { showDeleteDialog = true },
            )

            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            },
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.wallet_create_confirm_title)) },
            text = { Text(stringResource(R.string.wallet_create_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    viewModel.createWallet()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wallet_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallet_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteWallet()
                }) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ── Private components ────────────────────────────────────────────────────────

@Composable
private fun BalanceCard(
    balanceSats: Long?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (balanceSats != null) {
                        stringResource(R.string.balance_btc, balanceSats / 100_000_000.0)
                    } else {
                        stringResource(R.string.balance_unknown)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                IconButton(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.sync_wallet),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (balanceSats != null) {
                Text(
                    text = stringResource(R.string.balance_sats, balanceSats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun WalletListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        headlineContent = { Text(text = title, color = titleColor) },
        supportingContent = {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    isDestructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .alpha(contentAlpha)
            .clickable(enabled = enabled, onClick = onClick),
    )
}
