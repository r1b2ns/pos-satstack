package com.possatstack.app.ui.wallet.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.ui.wallet.WalletViewModel
import com.possatstack.app.wallet.signer.tapsigner.Cvc
import com.possatstack.app.wallet.signer.tapsigner.TapsignerError
import com.possatstack.app.wallet.signer.tapsigner.TapsignerImportStep
import kotlinx.coroutines.launch

/**
 * Onboarding screen that turns a TAPSIGNER tap into a watch-only wallet.
 *
 * Flow:
 *  1. Ask the user for the CVC printed on the card.
 *  2. Trigger an NFC reader session via [TapsignerImportViewModel].
 *  3. Read the master + account xpub through `cktap-android`.
 *  4. Persist as a watch-only wallet via [WalletViewModel.importWalletWatching].
 *  5. Hand off to the caller (typically navigates to the charge screen).
 */
@Composable
fun TapsignerImportScreen(
    walletViewModel: WalletViewModel,
    onImported: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TapsignerImportViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var persisting by remember { mutableStateOf(false) }
    var persistError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.start { result ->
            result.fold(
                onSuccess = { (fingerprint, xpub) ->
                    scope.launch {
                        persisting = true
                        runCatching { walletViewModel.importWalletWatching(xpub, fingerprint) }
                            .onSuccess {
                                persisting = false
                                onImported()
                            }
                            .onFailure { exception ->
                                persisting = false
                                persistError = exception.message
                            }
                    }
                },
                onFailure = { /* TapsignerImportStep.Failed already published */ },
            )
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.tapsigner_import_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tapsigner_import_subtitle),
                fontSize = 14.sp,
                color = Color(0xFF555555),
            )
            Spacer(Modifier.height(24.dp))

            when {
                persisting ->
                    SpinnerBody(stringResource(R.string.tapsigner_import_persisting))

                persistError != null ->
                    ErrorBody(
                        message = persistError ?: stringResource(R.string.tapsigner_error_generic),
                        onRetry = {
                            persistError = null
                            viewModel.start { result ->
                                result.fold(
                                    onSuccess = { (fingerprint, xpub) ->
                                        scope.launch {
                                            persisting = true
                                            runCatching {
                                                walletViewModel.importWalletWatching(xpub, fingerprint)
                                            }.onSuccess {
                                                persisting = false
                                                onImported()
                                            }.onFailure { exception ->
                                                persisting = false
                                                persistError = exception.message
                                            }
                                        }
                                    },
                                    onFailure = { /* state already updated */ },
                                )
                            }
                        },
                    )

                else -> StepBody(step, viewModel::submitCvc, viewModel::start, walletViewModel, scope)
            }
        }

        OutlinedButton(
            onClick = {
                viewModel.cancel()
                onCancel()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun StepBody(
    step: TapsignerImportStep,
    onCvcSubmit: (String) -> Unit,
    onRetry: ((Result<Pair<String, String>>) -> Unit) -> Unit,
    walletViewModel: WalletViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    when (step) {
        is TapsignerImportStep.AwaitingCvc -> CvcInputBody(onCvcSubmit)

        is TapsignerImportStep.AwaitingTap ->
            InstructionBody(stringResource(R.string.tapsigner_awaiting_tap))

        is TapsignerImportStep.Exchanging ->
            SpinnerBody(stringResource(R.string.tapsigner_exchanging))

        is TapsignerImportStep.Done ->
            InstructionBody(stringResource(R.string.tapsigner_done))

        is TapsignerImportStep.Failed ->
            ErrorBody(
                message = humanise(step.error),
                onRetry = {
                    onRetry { result ->
                        result.onSuccess { (fingerprint, xpub) ->
                            scope.launch {
                                runCatching { walletViewModel.importWalletWatching(xpub, fingerprint) }
                            }
                        }
                    }
                },
            )
    }
}

@Composable
private fun CvcInputBody(onCvcSubmit: (String) -> Unit) {
    var cvcText by remember { mutableStateOf("") }
    val isValid = remember(cvcText) { Cvc.isValidShape(cvcText) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tapsigner_cvc_prompt),
            style = MaterialTheme.typography.bodyMedium,
        )
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
            onClick = { onCvcSubmit(cvcText) },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.tapsigner_confirm))
        }
    }
}

@Composable
private fun InstructionBody(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun SpinnerBody(message: String) {
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
private fun ErrorBody(
    message: String,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tapsigner_retry))
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
        is TapsignerError.NotSetUp -> stringResource(R.string.tapsigner_error_not_set_up)
        else -> stringResource(R.string.tapsigner_error_generic)
    }
