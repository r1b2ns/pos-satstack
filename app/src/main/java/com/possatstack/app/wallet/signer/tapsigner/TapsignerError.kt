package com.possatstack.app.wallet.signer.tapsigner

/**
 * Failures that can surface from a TAPSIGNER NFC session.
 *
 * The concrete [com.possatstack.app.wallet.signer.TapsignerNfcSigner]
 * translates these into [com.possatstack.app.wallet.WalletError] so the
 * rest of the app keeps its neutral error vocabulary. Keeping a dedicated
 * hierarchy here is useful because the UI dialog renders each case with
 * tailored copy (rate-limit cooldown, tap again, etc.).
 */
sealed class TapsignerError(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    /** Card rejected the CVC; the card enforces an increasing backoff after repeated wrong entries. */
    data class WrongCvc(val attemptsLeft: Int?) : TapsignerError(
        "Wrong CVC" + (attemptsLeft?.let { " ($it attempts left)" } ?: ""),
    )

    /** Card is currently rate-limited; caller should retry after [waitSeconds]. */
    data class RateLimited(val waitSeconds: Int) : TapsignerError("Card is rate-limited ($waitSeconds seconds)")

    /** Tap interrupted before the protocol completed. */
    data object CardRemoved : TapsignerError("Card removed before transaction completed")

    /** Card didn't respond within the timeout for a single command. */
    data object Timeout : TapsignerError("Tap timed out")

    /** The on-card application did not match the expected Coinkite AID. */
    data object NotATapsigner : TapsignerError("NFC tag is not a TAPSIGNER card")

    /** TAPSIGNER reports it still needs initial setup (pick PIN + generate key). */
    data object NotSetUp : TapsignerError("TAPSIGNER has not been set up yet")

    /** User cancelled the dialog. */
    data object UserCancelled : TapsignerError("User cancelled the tap")

    /** Catch-all for protocol malformations — logged but surfaced generically. */
    data class ProtocolError(val reason: String, override val cause: Throwable? = null) :
        TapsignerError("TAPSIGNER protocol error: $reason", cause)

    /** Android or host-side failure (NFC disabled, adapter unavailable, etc). */
    data class HostError(val reason: String, override val cause: Throwable? = null) :
        TapsignerError("Host NFC error: $reason", cause)
}
