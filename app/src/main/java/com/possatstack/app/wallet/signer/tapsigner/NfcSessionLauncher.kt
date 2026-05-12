package com.possatstack.app.wallet.signer.tapsigner

import android.nfc.Tag

/**
 * Surfaces an NFC tap as a [TapsignerClient] (for the in-house APDU
 * client) or as a raw [Tag] (used by the rust-cktap bridge in
 * [IsoDepCkTransport]).
 *
 * Android's NFC stack delivers tags via Activity callbacks; the signer
 * layer only cares about receiving an [IsoDep]-ready tag. Both
 * [awaitClient] and [awaitTag] enable reader-mode on the currently-resumed
 * activity and block until a tag is seen (or [timeoutMs] elapses).
 *
 * Concrete implementation [AndroidNfcSessionLauncher] uses [ActivityHolder]
 * to reach the current `Activity` without needing a direct reference.
 */
internal interface NfcSessionLauncher {
    /**
     * Open a reader session and suspend until a TAPSIGNER-compatible tag
     * touches the device. The returned client is connected and has
     * already completed `SELECT AID`.
     *
     * @throws TapsignerError.HostError if NFC is off, the adapter is
     *         missing, or no activity is available.
     * @throws TapsignerError.Timeout if no tag appears within [timeoutMs].
     * @throws TapsignerError.UserCancelled if [cancel] is called.
     */
    suspend fun awaitClient(timeoutMs: Long = DEFAULT_TIMEOUT_MS): TapsignerClient

    /**
     * Open a reader session and return the raw [Tag] once a card touches
     * the device. Used by the rust-cktap bridge — that library drives its
     * own `SELECT AID` / APDU traffic via [IsoDepCkTransport].
     *
     * Throws the same errors as [awaitClient].
     */
    suspend fun awaitTag(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Tag

    /** Abort the pending [awaitClient] / [awaitTag] call. Idempotent. */
    fun cancel()

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    }
}
