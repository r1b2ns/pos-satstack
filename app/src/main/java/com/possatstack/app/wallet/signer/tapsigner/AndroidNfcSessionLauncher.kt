package com.possatstack.app.wallet.signer.tapsigner

import android.nfc.NfcAdapter
import android.nfc.Tag
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.signer.ActivityHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [NfcSessionLauncher] that talks to [NfcAdapter] via reader
 * mode. The currently-resumed `FragmentActivity` is fetched from
 * [ActivityHolder] so this class can live as an app-scoped singleton
 * without holding any Activity reference directly.
 *
 * Reader mode is narrower than the generic dispatcher: it routes tags
 * straight to this launcher without triggering the "new tag discovered"
 * screen, which is what we want when an explicit signing dialog is open.
 */
@Singleton
internal class AndroidNfcSessionLauncher
    @Inject
    constructor(
        private val activityHolder: ActivityHolder,
    ) : NfcSessionLauncher {
        @Volatile
        private var pending: CompletableDeferred<Tag>? = null

        override suspend fun awaitClient(timeoutMs: Long): TapsignerClient {
            val activity =
                activityHolder.current()
                    ?: throw TapsignerError.HostError("No activity available for NFC reader mode")
            val adapter =
                NfcAdapter.getDefaultAdapter(activity)
                    ?: throw TapsignerError.HostError("Device has no NFC adapter")
            if (!adapter.isEnabled) {
                throw TapsignerError.HostError("NFC is disabled")
            }

            val deferred = CompletableDeferred<Tag>()
            pending = deferred

            val callback =
                NfcAdapter.ReaderCallback { tag ->
                    AppLogger.info(TAG, "NFC tag discovered")
                    deferred.complete(tag)
                }

            adapter.enableReaderMode(
                activity,
                callback,
                READER_FLAGS,
                null,
            )

            try {
                val tag = withTimeout(timeoutMs) { deferred.await() }
                val client = IsoDepTapsignerClient(tag)
                client.connect()
                return client
            } catch (exception: TimeoutCancellationException) {
                throw TapsignerError.Timeout
            } finally {
                try {
                    adapter.disableReaderMode(activity)
                } catch (exception: Exception) {
                    AppLogger.warning(TAG, "disableReaderMode raised: ${exception.message}")
                }
                if (pending === deferred) pending = null
            }
        }

        override fun cancel() {
            pending?.cancel()
            pending = null
        }

        private companion object {
            const val TAG = "NfcSessionLauncher"

            /** IsoDep (for TAPSIGNER) + skip NDEF checks to speed up the first APDU. */
            const val READER_FLAGS: Int =
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        }
    }
