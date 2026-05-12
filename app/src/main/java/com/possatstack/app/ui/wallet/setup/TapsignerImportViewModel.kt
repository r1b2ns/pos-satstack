package com.possatstack.app.ui.wallet.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.signer.tapsigner.Cvc
import com.possatstack.app.wallet.signer.tapsigner.TapsignerError
import com.possatstack.app.wallet.signer.tapsigner.TapsignerImportReader
import com.possatstack.app.wallet.signer.tapsigner.TapsignerImportStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the TAPSIGNER xpub import onboarding flow.
 *
 * Owns no UI state of its own — it forwards [TapsignerImportReader]
 * progress to the screen and exposes [start] (begins the tap session),
 * [submitCvc] (called when the user confirms the PIN) and [cancel].
 *
 * After a successful read the screen invokes [TapsignerImportViewModel]'s
 * sibling, `WalletViewModel.importWalletWatching`, to persist the
 * resulting xpub as a watch-only wallet.
 */
@HiltViewModel
class TapsignerImportViewModel
    @Inject
    constructor(
        private val reader: TapsignerImportReader,
    ) : ViewModel() {
        val step: StateFlow<TapsignerImportStep> =
            reader.state
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = TapsignerImportStep.AwaitingCvc,
                )

        /** Kick off the full read flow. Suspends until success/error. */
        fun start(onResult: (Result<Pair<String, String>>) -> Unit) {
            viewModelScope.launch {
                runCatching { reader.fetchAccountXpub() }
                    .onSuccess { info ->
                        onResult(Result.success(info.masterFingerprint to info.accountXpub))
                    }
                    .onFailure { exception ->
                        AppLogger.warning(TAG, "TAPSIGNER import failed: ${exception.message}")
                        onResult(Result.failure(exception))
                    }
            }
        }

        fun submitCvc(cvcText: String) {
            try {
                val cvc = Cvc.parse(cvcText)
                reader.submitCvc(cvc)
            } catch (exception: IllegalArgumentException) {
                AppLogger.warning(TAG, "Invalid CVC shape: ${exception.message}")
            }
        }

        fun cancel() {
            reader.cancel()
        }

        override fun onCleared() {
            super.onCleared()
            reader.cancel()
        }

        private companion object {
            const val TAG = "TapsignerImportViewModel"
        }
    }
