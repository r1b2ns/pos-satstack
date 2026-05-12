package com.possatstack.app.wallet.signer.tapsigner

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.possatstack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.cktap.CkTransport

/**
 * [CkTransport] implementation backed by Android's [IsoDep]. Hands raw
 * APDUs to a Coinkite Tap card and returns the response, blocking I/O on
 * [Dispatchers.IO] so callers can suspend without freezing the UI thread.
 *
 * Constructed from the [Tag] delivered by [NfcSessionLauncher]. [connect]
 * MUST be called before the first [transmitApdu]; otherwise APDU dispatch
 * fails. [close] releases the channel; subsequent transmits throw.
 *
 * Not thread-safe — callers must serialise access (the signer session does
 * this naturally via a single coroutine).
 */
internal class IsoDepCkTransport(
    private val tag: Tag,
) : CkTransport {
    private var isoDep: IsoDep? = null

    val isConnected: Boolean
        get() = isoDep?.isConnected == true

    suspend fun connect() =
        withContext(Dispatchers.IO) {
            val channel =
                IsoDep.get(tag)
                    ?: throw TapsignerError.NotATapsigner
            channel.timeout = TRANSCEIVE_TIMEOUT_MS
            try {
                channel.connect()
            } catch (exception: Exception) {
                throw TapsignerError.HostError("IsoDep connect failed", exception)
            }
            isoDep = channel
        }

    override suspend fun transmitApdu(commandApdu: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            val channel =
                isoDep
                    ?: throw TapsignerError.HostError("transmitApdu before connect")
            try {
                channel.transceive(commandApdu)
            } catch (exception: Exception) {
                throw TapsignerError.HostError("APDU transceive failed", exception)
            }
        }

    suspend fun close() {
        withContext(Dispatchers.IO) {
            try {
                isoDep?.close()
            } catch (exception: Exception) {
                AppLogger.warning(TAG, "IsoDep close raised: ${exception.message}")
            } finally {
                isoDep = null
            }
        }
    }

    private companion object {
        const val TAG = "IsoDepCkTransport"

        /** Conservative per-APDU timeout — surfaces card-lost errors fast. */
        const val TRANSCEIVE_TIMEOUT_MS = 5_000
    }
}
