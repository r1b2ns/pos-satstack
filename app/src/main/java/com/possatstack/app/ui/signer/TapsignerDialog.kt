package com.possatstack.app.ui.signer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.possatstack.app.R
import com.possatstack.app.wallet.signer.TapsignerNfcSigner
import com.possatstack.app.wallet.signer.tapsigner.Cvc
import com.possatstack.app.wallet.signer.tapsigner.TapsignerError
import com.possatstack.app.wallet.signer.tapsigner.TapsignerStep

/**
 * Dialog that drives a single TAPSIGNER signing session.
 *
 * Observes [TapsignerNfcSigner.sessionState] and renders the appropriate
 * body for each [TapsignerStep]:
 *  - [TapsignerStep.AwaitingCvc] → CVC text field + continue button.
 *  - [TapsignerStep.AwaitingTap] → tap instructions.
 *  - [TapsignerStep.Exchanging]  → spinner.
 *  - [TapsignerStep.RateLimited] → cooldown notice.
 *  - [TapsignerStep.Done]        → dismisses itself after notifying the caller.
 *  - [TapsignerStep.Failed]      → error copy + retry / dismiss.
 *
 * The caller drives the signing flow (by calling `signer.signPsbt(...)`)
 * and passes the current [step] into this composable each recomposition.
 */
@Composable
fun TapsignerDialog(
    step: TapsignerStep,
    onCvcSubmit: (Cvc) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tapsigner_title)) },
        text = { TapsignerDialogBody(step = step, onCvcSubmit = onCvcSubmit) },
        confirmButton = {
            when (step) {
                is TapsignerStep.Failed ->
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.tapsigner_retry)) }
                is TapsignerStep.Done ->
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.charge_details_done)) }
                else ->
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun TapsignerDialogBody(
    step: TapsignerStep,
    onCvcSubmit: (Cvc) -> Unit,
) {
    when (step) {
        is TapsignerStep.AwaitingCvc -> CvcInput(onCvcSubmit = onCvcSubmit)
        is TapsignerStep.AwaitingTap -> InstructionBlock(stringResource(R.string.tapsigner_awaiting_tap))
        is TapsignerStep.Exchanging -> SpinnerBlock(stringResource(R.string.tapsigner_exchanging))
        is TapsignerStep.RateLimited ->
            InstructionBlock(stringResource(R.string.tapsigner_rate_limited, step.waitSeconds))
        is TapsignerStep.Done -> InstructionBlock(stringResource(R.string.tapsigner_done))
        is TapsignerStep.Failed -> InstructionBlock(humanise(step.error))
    }
}

@Composable
private fun CvcInput(onCvcSubmit: (Cvc) -> Unit) {
    var cvcText by remember { mutableStateOf("") }
    val isValid = remember(cvcText) { Cvc.isValidShape(cvcText) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = cvcText,
            onValueChange = { cvcText = it.filter(Char::isDigit).take(Cvc.MAX_LENGTH) },
            label = { Text(stringResource(R.string.tapsigner_cvc_label)) },
            supportingText = {
                Text(
                    text =
                        if (cvcText.isEmpty() || isValid) {
                            stringResource(R.string.tapsigner_cvc_hint)
                        } else {
                            stringResource(R.string.tapsigner_cvc_invalid)
                        },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = cvcText.isNotEmpty() && !isValid,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onCvcSubmit(Cvc.parse(cvcText)) },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.tapsigner_confirm))
        }
    }
}

@Composable
private fun InstructionBlock(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun SpinnerBlock(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun humanise(error: TapsignerError): String =
    when (error) {
        is TapsignerError.WrongCvc -> stringResource(R.string.tapsigner_error_wrong_cvc)
        is TapsignerError.RateLimited ->
            stringResource(R.string.tapsigner_rate_limited, error.waitSeconds)
        is TapsignerError.CardRemoved -> stringResource(R.string.tapsigner_error_card_removed)
        is TapsignerError.NotATapsigner -> stringResource(R.string.tapsigner_error_not_tapsigner)
        is TapsignerError.Timeout -> stringResource(R.string.tapsigner_error_timeout)
        is TapsignerError.UserCancelled -> stringResource(R.string.cancel)
        else -> stringResource(R.string.tapsigner_error_generic)
    }
