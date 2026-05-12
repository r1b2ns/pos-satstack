package com.possatstack.app.wallet.signer.tapsigner

/**
 * Surfaces an NFC tap as a connected [IsoDepCkTransport].
 *
 * Android's NFC stack delivers tags via Activity callbacks; the signer
 * layer only cares about receiving an `IsoDep`-ready transport that the
 * `cktap-android` Rust bindings can drive. [awaitTransport] enables
 * reader-mode on the currently-resumed activity, blocks until a tag is
 * seen (or [timeoutMs] elapses), and hands it back already connected.
 *
 * Concrete implementation [AndroidNfcSessionLauncher] uses [ActivityHolder]
 * to reach the current `Activity` without needing a direct reference.
 */
internal interface NfcSessionLauncher {
    /**
     * Open a reader session and suspend until a Coinkite-Tap-compatible
     * tag touches the device. The returned transport is connected and
     * ready for `org.bitcoindevkit.cktap.toCktap`.
     *
     * @throws TapsignerError.HostError if NFC is off, the adapter is
     *         missing, or no activity is available.
     * @throws TapsignerError.Timeout if no tag appears within [timeoutMs].
     * @throws TapsignerError.UserCancelled if [cancel] is called.
     */
    suspend fun awaitTransport(timeoutMs: Long = DEFAULT_TIMEOUT_MS): IsoDepCkTransport

    /** Abort the pending [awaitTransport] call. Idempotent. */
    fun cancel()

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
    }
}
