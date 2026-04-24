package com.possatstack.app.ui.wallet.transactions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.ui.wallet.WalletViewModel
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletTransactionsScreen(viewModel: WalletViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadTransactions()
    }

    val transactions = state.transactions
    val network = state.network
    val unconfirmed = remember(transactions) { transactions.filter { !it.isConfirmed } }
    val confirmed = remember(transactions) { transactions.filter { it.isConfirmed } }
    val groupedConfirmed = remember(confirmed) { groupByDate(confirmed) }

    val onTransactionClick: (WalletTransaction) -> Unit = { transaction ->
        val url = buildMempoolUrl(transaction.txid, network)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    if (transactions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.no_transactions),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ── Unconfirmed (pending) ───────────────────────────────────────
        if (unconfirmed.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.unconfirmed_transactions))
            }
            items(unconfirmed, key = { it.txid }) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }

        // ── Confirmed, grouped by date ──────────────────────────────────
        groupedConfirmed.forEach { (dateLabel, transactionsForDate) ->
            item { SectionHeader(dateLabel) }
            items(transactionsForDate, key = { it.txid }) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun TransactionItem(
    transaction: WalletTransaction,
    onClick: () -> Unit,
) {
    val isIncoming = transaction.netSats >= 0
    val icon =
        if (isIncoming) {
            Icons.AutoMirrored.Filled.CallReceived
        } else {
            Icons.AutoMirrored.Filled.CallMade
        }
    val label =
        if (isIncoming) {
            stringResource(R.string.transaction_received)
        } else {
            stringResource(R.string.transaction_sent)
        }
    val amountColor =
        if (isIncoming) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    val amountPrefix = if (isIncoming) "+" else ""
    val amountSats = kotlin.math.abs(transaction.netSats)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    if (isIncoming) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label)
                Text(
                    text = "$amountPrefix${formatSats(amountSats)}",
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = amountColor,
                )
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = transaction.txid,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!transaction.isConfirmed) {
                    Text(
                        text = stringResource(R.string.transaction_unconfirmed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (transaction.blockHeight != null) {
                    Text(
                        text = "Block ${transaction.blockHeight}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/**
 * Builds the mempool.space URL for a given transaction.
 * Uses the signet explorer for [WalletNetwork.SIGNET] and the mainnet
 * explorer for [WalletNetwork.MAINNET]. Falls back to mainnet for
 * any other network.
 */
private fun buildMempoolUrl(
    txid: String,
    network: WalletNetwork?,
): String {
    return when (network) {
        WalletNetwork.SIGNET -> "https://mempool.space/signet/tx/$txid"
        WalletNetwork.TESTNET -> "https://mempool.space/testnet/tx/$txid"
        else -> "https://mempool.space/tx/$txid"
    }
}

private fun groupByDate(transactions: List<WalletTransaction>): List<Pair<String, List<WalletTransaction>>> {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return transactions
        .groupBy { transaction ->
            transaction.confirmationTime?.let { epochSeconds ->
                dateFormat.format(Date(epochSeconds * 1000))
            } ?: "Unknown"
        }
        .toList()
}

private fun formatSats(sats: Long): String {
    return when {
        sats >= 100_000_000 -> "%.8f BTC".format(sats / 100_000_000.0)
        sats >= 1_000_000 -> "%,.0f sats".format(sats.toDouble())
        else -> "%,d sats".format(sats)
    }
}
