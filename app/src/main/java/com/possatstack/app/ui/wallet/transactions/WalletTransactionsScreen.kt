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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
                if (isIncoming && transaction.address != null) {
                    MiddleEllipsisText(
                        text = transaction.address,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

/**
 * Renders [text] on a single line. If the full string doesn't fit the
 * available width, the middle is replaced with an ellipsis so the start
 * and end of the string stay visible (useful for Bitcoin addresses).
 *
 * Uses `Modifier.layout` (not `BoxWithConstraints`) so it works inside
 * components that query intrinsic measurements, such as `ListItem`.
 */
@Composable
private fun MiddleEllipsisText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var availableWidth by remember { mutableStateOf(0) }
    val displayed by remember(text, style) {
        derivedStateOf {
            if (availableWidth <= 0) {
                text
            } else {
                middleEllipsize(text, availableWidth) { candidate ->
                    measurer
                        .measure(
                            text = candidate,
                            style = style,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = Int.MAX_VALUE),
                        ).size.width
                }
            }
        }
    }
    Text(
        text = displayed,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier =
            modifier.layout { measurable, constraints ->
                if (constraints.hasBoundedWidth && constraints.maxWidth != availableWidth) {
                    availableWidth = constraints.maxWidth
                }
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    )
}

private fun middleEllipsize(
    text: String,
    maxWidthPx: Int,
    widthOf: (String) -> Int,
): String {
    if (maxWidthPx <= 0 || text.isEmpty()) return text
    if (widthOf(text) <= maxWidthPx) return text
    val ellipsis = "..."
    if (widthOf(ellipsis) > maxWidthPx) return ellipsis
    var low = 1
    var high = (text.length - 1) / 2
    var best = ellipsis
    while (low <= high) {
        val mid = (low + high) / 2
        val candidate = text.take(mid) + ellipsis + text.takeLast(mid)
        if (widthOf(candidate) <= maxWidthPx) {
            best = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best
}

private fun formatSats(sats: Long): String {
    return when {
        sats >= 100_000_000 -> "%.8f BTC".format(sats / 100_000_000.0)
        sats >= 1_000_000 -> "%,.0f sats".format(sats.toDouble())
        else -> "%,d sats".format(sats)
    }
}
