package com.possatstack.app.wallet.signer.tapsigner

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.possatstack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.cktap.CkTransport

/**
 * [CkTransport] backed by [IsoDep]. Lets the rust-cktap bindings drive
 * APDU traffic to a TAPSIGNER / SATSCARD over Android NFC.
 *
 * The rust-cktap library handles SELECT AID, the ECDH handshake, CVC
 * encryption, and the per-input PSBT sign loop. This transport only
 * forwards raw APDUs from Rust to the card and the response bytes back.
 *
 * Connection is opened lazily on the first [transmitApdu] call so that
 * the [Tag] obtained from `NfcAdapter.ReaderCallback` can be wrapped
 * without an extra synchronous step.
 */
internal class IsoDepCkTransport(
    private val tag: Tag,
) : CkTransport {
    @Volatile
    private var isoDep: IsoDep? = null

    override suspend fun transmitApdu(commandApdu: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            val channel = ensureConnected()
            try {
                channel.transceive(commandApdu)
            } catch (exception: Exception) {
                throw TapsignerError.HostError("APDU transceive failed", exception)
            }
        }

    /** Close the underlying [IsoDep] channel. Idempotent. */
    fun close() {
        val channel = isoDep ?: return
        isoDep = null
        try {
            channel.close()
        } catch (exception: Exception) {
            AppLogger.warning(TAG, "IsoDep close raised: ${exception.message}")
        }
    }

    private fun ensureConnected(): IsoDep {
        isoDep?.let { return it }
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
        return channel
    }

    private companion object {
        const val TAG = "IsoDepCkTransport"
        const val TRANSCEIVE_TIMEOUT_MS = 10_000
    }
}
