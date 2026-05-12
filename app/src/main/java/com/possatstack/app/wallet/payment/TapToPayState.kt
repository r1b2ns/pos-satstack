package com.possatstack.app.wallet.payment

/**
 * State machine for the "tap-to-pay with TAPSIGNER" experiment.
 *
 * Drives the dialog rendered by `ChargeDetailsScreen`:
 *  [Idle] → no dialog
 *  [AwaitingCvc] → ask the user for the TAPSIGNER CVC
 *  [AwaitingTap] → ask the user to hold the card against the phone
 *  [Working] → spinner with sub-step label
 *  [Success] / [Failed] → terminal message; user dismisses to return to [Idle]
 */
sealed interface TapToPayState {
    data object Idle : TapToPayState

    data object AwaitingCvc : TapToPayState

    data object AwaitingTap : TapToPayState

    data class Working(val step: Step) : TapToPayState

    data class Success(val txidHex: String) : TapToPayState

    data class Failed(val reason: String) : TapToPayState

    enum class Step {
        ReadingCard,
        BuildingPsbt,
        Signing,
        Broadcasting,
    }
}
