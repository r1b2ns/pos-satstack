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
import com.possatstack.app.wallet.payment.ChargeStatus
import com.possatstack.app.wallet.payment.PaymentOrchestrator
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
    constructor(
        private val orchestrator: PaymentOrchestrator,
        private val walletEngine: OnChainWalletEngine,
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
    }
