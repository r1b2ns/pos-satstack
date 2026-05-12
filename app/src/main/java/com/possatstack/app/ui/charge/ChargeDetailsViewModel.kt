package com.possatstack.app.ui.charge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.possatstack.app.navigation.AppDestination
import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.payment.Charge
import com.possatstack.app.wallet.payment.ChargePayload
import com.possatstack.app.wallet.payment.ChargeStatus
import com.possatstack.app.wallet.payment.PaymentOrchestrator
import com.possatstack.app.wallet.payment.TapToPayController
import com.possatstack.app.wallet.payment.TapToPayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Observes a single charge: renders its payload (address + BIP21 URI)
 * and listens to [PaymentOrchestrator.chargeStatus] for status updates.
 *
 * Invoked by navigation, so the charge id comes from the typed route.
 */
@HiltViewModel
class ChargeDetailsViewModel
    @Inject
    internal constructor(
        private val orchestrator: PaymentOrchestrator,
        private val walletEngine: OnChainWalletEngine,
        private val tapToPayController: TapToPayController,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        data class State(
            val charge: Charge? = null,
            val status: ChargeStatus = ChargeStatus.Pending,
            val network: WalletNetwork? = null,
            val isRefreshing: Boolean = false,
            val unsignedPsbt: UnsignedPsbt? = null,
        )

        private val chargeId: String =
            savedStateHandle.toRoute<AppDestination.ChargeDetails>().chargeId

        private val _state =
            MutableStateFlow(
                orchestrator.getCharge(chargeId).let { charge ->
                    State(charge = charge, unsignedPsbt = charge?.unsignedPsbt)
                },
            )
        val state: StateFlow<State> = _state.asStateFlow()

        val tapToPayState: StateFlow<TapToPayState> = tapToPayController.state

        init {
            viewModelScope.launch {
                val network = runCatching { walletEngine.getNetwork() }.getOrNull()
                _state.update { it.copy(network = network) }
            }
            viewModelScope.launch {
                orchestrator.chargeStatus(chargeId).collect { status ->
                    _state.update { it.copy(status = status) }
                }
            }
        }

        fun cancel() {
            viewModelScope.launch { orchestrator.cancelCharge(chargeId) }
        }

        fun refresh() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    orchestrator.refreshCharge(chargeId)
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        /**
         * Kick off the TAPSIGNER tap-to-pay flow for this charge. No-op if
         * the charge has no on-chain payload or the wallet network is not
         * loaded yet.
         */
        fun startTapToPay() {
            val charge = _state.value.charge ?: return
            val network = _state.value.network ?: return
            val onChain = charge.payload as? ChargePayload.OnChainAddress ?: return
            tapToPayController.start(
                chargeAddress = onChain.address,
                chargeAmountSats = charge.amountSats,
                network = network,
            )
        }

        fun submitTapCvc(cvc: String) {
            tapToPayController.submitCvc(cvc)
        }

        fun cancelTapToPay() {
            tapToPayController.cancel()
        }

        fun dismissTapToPay() {
            tapToPayController.reset()
        }
    }
