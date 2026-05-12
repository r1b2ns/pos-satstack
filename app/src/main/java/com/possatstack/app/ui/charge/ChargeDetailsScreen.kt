package com.possatstack.app.ui.charge

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.util.SatsFormatter
import com.possatstack.app.util.generateQrBitmap
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.payment.ChargePayload
import com.possatstack.app.wallet.payment.ChargeStatus

private const val QR_SIZE_PX = 512

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeDetailsScreen(
    onDismiss: () -> Unit,
    viewModel: ChargeDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val charge = state.charge

    Box(modifier = Modifier.fillMaxSize()) {
        if (charge == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.charge_details_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.back)) }
            }
            return@Box
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AmountHeader(amountSats = charge.amountSats)

                    Spacer(Modifier.height(16.dp))

                    StatusBanner(status = state.status, network = state.network)

                    Spacer(Modifier.height(16.dp))

                    when (val payload = charge.payload) {
                        is ChargePayload.OnChainAddress -> OnChainPayloadCard(payload, state.network)
                        is ChargePayload.LightningInvoice ->
                            PlaceholderCard(stringResource(R.string.charge_details_ln_placeholder))
                        is ChargePayload.BearerSlot ->
                            PlaceholderCard(stringResource(R.string.charge_details_bearer_placeholder))
                    }
                }

                BottomAction(
                    status = state.status,
                    onCancel = {
                        viewModel.cancel()
                        onDismiss()
                    },
                    onDone = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun BottomAction(
    status: ChargeStatus,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val content: (@Composable () -> Unit)? =
        when (status) {
            is ChargeStatus.Pending -> {
                {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) { Text(stringResource(R.string.charge_details_cancel)) }
                }
            }
            is ChargeStatus.Confirmed,
            is ChargeStatus.Cancelled,
            is ChargeStatus.Expired,
            is ChargeStatus.Failed,
            -> {
                {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) { Text(stringResource(R.string.charge_details_done)) }
                }
            }
            is ChargeStatus.Detected -> null
        }

    if (content == null) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
    ) {
        content()
    }
}

@Composable
private fun AmountHeader(amountSats: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "₿${SatsFormatter.format(amountSats.toString())}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.charge_sats_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBanner(
    status: ChargeStatus,
    network: WalletNetwork?,
) {
    val (label, container) =
        when (status) {
            is ChargeStatus.Pending ->
                stringResource(R.string.charge_status_pending) to MaterialTheme.colorScheme.surfaceVariant
            is ChargeStatus.Detected ->
                stringResource(R.string.charge_status_detected) to MaterialTheme.colorScheme.tertiaryContainer
            is ChargeStatus.Confirmed ->
                stringResource(R.string.charge_status_confirmed) to MaterialTheme.colorScheme.primaryContainer
            is ChargeStatus.Cancelled ->
                stringResource(R.string.charge_status_cancelled) to MaterialTheme.colorScheme.surfaceVariant
            is ChargeStatus.Expired ->
                stringResource(R.string.charge_status_expired) to MaterialTheme.colorScheme.surfaceVariant
            is ChargeStatus.Failed ->
                stringResource(R.string.charge_status_failed) to MaterialTheme.colorScheme.errorContainer
        }

    val mempoolTxid =
        when (status) {
            is ChargeStatus.Detected -> status.txid?.hex
            is ChargeStatus.Confirmed -> status.txid?.hex
            else -> null
        }

    val hint =
        when (status) {
            is ChargeStatus.Detected -> stringResource(R.string.charge_status_detected_hint)
            is ChargeStatus.Cancelled -> stringResource(R.string.charge_status_cancelled_hint)
            else -> null
        }

    val context = LocalContext.current

    val clickableModifier =
        if (mempoolTxid != null) {
            Modifier.clickable {
                val url = buildMempoolUrl(mempoolTxid, network)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } else {
            Modifier
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(clickableModifier)
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                if (mempoolTxid != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.charge_view_on_mempool),
                        tint = Color.White,
                    )
                }
            }
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

private fun buildMempoolUrl(
    txid: String,
    network: WalletNetwork?,
): String =
    when (network) {
        WalletNetwork.SIGNET -> "https://mempool.space/signet/tx/$txid"
        WalletNetwork.TESTNET -> "https://mempool.space/testnet/tx/$txid"
        else -> "https://mempool.space/tx/$txid"
    }

private fun buildMempoolAddressUrl(
    address: String,
    network: WalletNetwork?,
): String =
    when (network) {
        WalletNetwork.SIGNET -> "https://mempool.space/signet/address/$address"
        WalletNetwork.TESTNET -> "https://mempool.space/testnet/address/$address"
        else -> "https://mempool.space/address/$address"
    }

@Composable
private fun OnChainPayloadCard(
    payload: ChargePayload.OnChainAddress,
    network: WalletNetwork?,
) {
    val qrBitmap = remember(payload.bip21Uri) { generateQrBitmap(payload.bip21Uri, QR_SIZE_PX) }
    val context = LocalContext.current

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.qr_code_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f),
            )

            Spacer(Modifier.height(16.dp))

            SelectionContainer {
                Text(
                    text = payload.address.value,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            textDecoration = TextDecoration.Underline,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.clickable {
                            val url = buildMempoolAddressUrl(payload.address.value, network)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.charge_view_address_on_mempool),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderCard(message: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
