package com.possatstack.app.wallet.signer.tapsigner

/**
 * Transport abstraction for exchanging raw APDUs with a TAPSIGNER card.
 *
 * Kept behind an interface so the state-machine / signer logic can be
 * exercised with an in-memory fake in unit tests. The only production
 * implementation is [IsoDepTapsignerClient], which wraps
 * [android.nfc.tech.IsoDep].
 */
internal interface TapsignerClient {
    /** True once [connect] has succeeded and the applet has been selected. */
    val isConnected: Boolean

    /** Open the underlying transport and run the Coinkite `SELECT AID`. */
    suspend fun connect()

    /** Transceive a raw APDU (already framed) and return the raw response. */
    suspend fun transceive(apdu: ByteArray): ByteArray

    /** Close the transport. Idempotent. */
    suspend fun close()

    /** Convenience: send a [TapsignerCommand] and return the decoded response map. */
    suspend fun send(command: TapsignerCommand): Map<String, Any?> {
        val apdu = TapsignerApdus.buildCoinkiteApdu(command.encode())
        val rawResponse = transceive(apdu)
        val parsed = TapsignerApdus.parseResponse(rawResponse)
        if (!parsed.isOk) {
            throw TapsignerError.ProtocolError(
                "APDU error sw=0x${Integer.toHexString(parsed.sw)} for cmd=${command.name}",
            )
        }
        return TapsignerResponse.decodeOrThrow(parsed.data)
    }
}
