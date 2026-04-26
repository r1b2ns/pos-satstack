package com.possatstack.app.wallet.signer.tapsigner

/**
 * Surfaces an NFC tap as a [TapsignerClient] stream.
 *
 * Android's NFC stack delivers tags via Activity callbacks; the signer
 * layer only cares about receiving an [IsoDep]-ready tag. [awaitClient]
 * enables reader-mode on the currently-resumed activity, blocks until a
 * tag is seen (or [timeoutMs] elapses), and hands it back wrapped.
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

    /** Abort the pending [awaitClient] call. Idempotent. */
    fun cancel()

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    }
}
