package com.possatstack.app.ui.charge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.possatstack.app.navigation.AppDestination
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
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        data class State(
            val charge: Charge? = null,
            val status: ChargeStatus = ChargeStatus.Pending,
        )

        private val chargeId: String =
            savedStateHandle.toRoute<AppDestination.ChargeDetails>().chargeId

        private val _state = MutableStateFlow(State(charge = orchestrator.getCharge(chargeId)))
        val state: StateFlow<State> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                orchestrator.chargeStatus(chargeId).collect { status ->
                    _state.update { it.copy(status = status) }
                }
            }
        }

        fun cancel() {
            viewModelScope.launch { orchestrator.cancelCharge(chargeId) }
        }
    }
