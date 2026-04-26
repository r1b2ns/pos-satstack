package com.possatstack.app.wallet.signer.tapsigner

import com.possatstack.app.wallet.SignedPsbt

/**
 * Progress emitted by a TAPSIGNER session while the user is interacting with
 * the card. The signing dialog collects a `Flow<TapsignerStep>` and renders
 * each step appropriately (tap instructions, CVC entry, spinner, result).
 *
 * Typical happy-path sequence:
 *   [AwaitingCvc] → [AwaitingTap] → [Exchanging] → [Done]
 *
 * Failures land on [Failed] regardless of where they happen.
 */
sealed interface TapsignerStep {
    /** Dialog should ask the user to enter the CVC printed on the card. */
    data object AwaitingCvc : TapsignerStep

    /** Dialog should show "hold the card against the phone" guidance. */
    data object AwaitingTap : TapsignerStep

    /** Card is present and the client is sending/receiving APDUs. */
    data object Exchanging : TapsignerStep

    /** Card needs to cool down before the next attempt. */
    data class RateLimited(val waitSeconds: Int) : TapsignerStep

    /** Session finished successfully with a signed PSBT. */
    data class Done(val signed: SignedPsbt) : TapsignerStep

    /** Session failed — dialog should render the error and allow retry/dismiss. */
    data class Failed(val error: TapsignerError) : TapsignerStep
}
