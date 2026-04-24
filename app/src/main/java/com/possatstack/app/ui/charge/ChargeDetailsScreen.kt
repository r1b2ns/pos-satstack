package com.possatstack.app.ui.charge

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.util.SatsFormatter
import com.possatstack.app.util.generateQrBitmap
import com.possatstack.app.wallet.payment.ChargePayload
import com.possatstack.app.wallet.payment.ChargeStatus

private const val QR_SIZE_PX = 512

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

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmountHeader(amountSats = charge.amountSats)

            Spacer(Modifier.height(16.dp))

            StatusBanner(status = state.status)

            Spacer(Modifier.height(16.dp))

            when (val payload = charge.payload) {
                is ChargePayload.OnChainAddress -> OnChainPayloadCard(payload)
                is ChargePayload.LightningInvoice ->
                    PlaceholderCard(stringResource(R.string.charge_details_ln_placeholder))
                is ChargePayload.BearerSlot ->
                    PlaceholderCard(stringResource(R.string.charge_details_bearer_placeholder))
            }

            Spacer(Modifier.height(24.dp))

            when (state.status) {
                is ChargeStatus.Confirmed,
                is ChargeStatus.Cancelled,
                is ChargeStatus.Expired,
                is ChargeStatus.Failed,
                ->
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) { Text(stringResource(R.string.charge_details_done)) }

                is ChargeStatus.Pending,
                is ChargeStatus.Detected,
                ->
                    OutlinedButton(
                        onClick = {
                            viewModel.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) { Text(stringResource(R.string.charge_details_cancel)) }
            }
        }
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
private fun StatusBanner(status: ChargeStatus) {
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

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnChainPayloadCard(payload: ChargePayload.OnChainAddress) {
    val qrBitmap = remember(payload.bip21Uri) { generateQrBitmap(payload.bip21Uri, QR_SIZE_PX) }

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
                        ),
                    textAlign = TextAlign.Center,
                )
            }
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
