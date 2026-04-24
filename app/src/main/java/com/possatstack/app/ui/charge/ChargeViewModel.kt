package com.possatstack.app.ui.charge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.possatstack.app.wallet.payment.PaymentMethod
import com.possatstack.app.wallet.payment.PaymentOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keypad + "Charge" button state for the home screen.
 *
 * The amount is held as a digit string (not a Long) so the keypad can
 * append/drop digits in constant time and preserve leading edits. On
 * [createOnChainCharge] the amount is parsed into sats, validated, and
 * passed to the [PaymentOrchestrator].
 */
@HiltViewModel
class ChargeViewModel
    @Inject
    constructor(
        private val orchestrator: PaymentOrchestrator,
    ) : ViewModel() {
        data class State(
            val amountInput: String = "0",
            val isCreating: Boolean = false,
            val errorMessage: String? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        fun appendDigit(digit: Int) {
            require(digit in 0..9) { "digit must be 0..9" }
            _state.update {
                val next =
                    if (it.amountInput == "0") digit.toString() else it.amountInput + digit
                val parsed = next.toLongOrNull()
                if (parsed != null && parsed <= MAX_SATS) {
                    it.copy(amountInput = next)
                } else {
                    it
                }
            }
        }

        fun backspace() {
            _state.update {
                val next = if (it.amountInput.length > 1) it.amountInput.dropLast(1) else "0"
                it.copy(amountInput = next)
            }
        }

        fun clearAmount() {
            _state.update { it.copy(amountInput = "0") }
        }

        /**
         * Creates an on-chain charge via the orchestrator. Invokes
         * [onCreated] with the new charge id so the caller can navigate
         * to the details screen, then resets the keypad.
         */
        fun createOnChainCharge(onCreated: (chargeId: String) -> Unit) {
            val amount = state.value.amountInput.toLongOrNull() ?: return
            if (amount <= 0) return
            viewModelScope.launch {
                _state.update { it.copy(isCreating = true, errorMessage = null) }
                runCatching { orchestrator.createCharge(PaymentMethod.OnChain, amount) }
                    .onSuccess { charge ->
                        _state.update { it.copy(isCreating = false, amountInput = "0") }
                        onCreated(charge.id)
                    }
                    .onFailure { exception ->
                        _state.update {
                            it.copy(isCreating = false, errorMessage = exception.message)
                        }
                    }
            }
        }

        fun clearError() {
            _state.update { it.copy(errorMessage = null) }
        }

        private companion object {
            const val MAX_SATS: Long = 100_000_000L
        }
    }
