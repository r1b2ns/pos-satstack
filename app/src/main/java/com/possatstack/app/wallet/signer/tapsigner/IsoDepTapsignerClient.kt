package com.possatstack.app.wallet.signer.tapsigner

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.possatstack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [TapsignerClient] backed by [IsoDep].
 *
 * Opens the NFC channel, runs `SELECT AID`, and exposes raw APDU
 * transceive. Timeouts are configured conservatively (5 seconds per APDU)
 * to surface [TapsignerError.Timeout] quickly when the card slips out of
 * range mid-exchange.
 *
 * Not thread-safe — callers must serialise access (the signer does so via
 * a per-session coroutine).
 */
internal class IsoDepTapsignerClient(
    private val tag: Tag,
) : TapsignerClient {
    private var isoDep: IsoDep? = null

    override val isConnected: Boolean
        get() = isoDep?.isConnected == true

    override suspend fun connect() =
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

            val selectResponse =
                try {
                    channel.transceive(TapsignerApdus.buildSelectAid())
                } catch (exception: Exception) {
                    throw TapsignerError.HostError("SELECT AID transceive failed", exception)
                }

            val parsedSelect = TapsignerApdus.parseResponse(selectResponse)
            if (!parsedSelect.isOk) {
                AppLogger.warning(TAG, "SELECT AID returned sw=0x${Integer.toHexString(parsedSelect.sw)}")
                throw TapsignerError.NotATapsigner
            }
        }

    override suspend fun transceive(apdu: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            val channel =
                isoDep
                    ?: throw TapsignerError.HostError("transceive before connect")
            try {
                channel.transceive(apdu)
            } catch (exception: Exception) {
                throw TapsignerError.HostError("APDU transceive failed", exception)
            }
        }

    override suspend fun close() {
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
        const val TAG = "IsoDepTapsignerClient"
        const val TRANSCEIVE_TIMEOUT_MS = 5_000
    }
}
