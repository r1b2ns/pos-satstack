package com.possatstack.app.wallet.signer.tapsigner

/**
 * APDU framing for the Coinkite TAPSIGNER tap-protocol.
 *
 * Commands are exchanged as ISO-7816 short APDUs on top of NFC ISO-DEP.
 * The card exposes a single INS byte (0xCB) that wraps a CBOR payload in
 * the data field; the response APDU's body is also a CBOR map.
 *
 * Reference: https://github.com/coinkite/coinkite-tap-proto
 *
 * All helpers here are pure byte-level — no NFC calls, no crypto — so they
 * can be exercised in plain JVM unit tests.
 */
internal object TapsignerApdus {
    /** The AID advertised by Coinkite's TAPSIGNER / SATSCARD applet. */
    val APPLET_AID: ByteArray =
        byteArrayOf(
            0xF0.toByte(), 0x43, 0x6F, 0x69, 0x6E, 0x6B, 0x69, 0x74,
            0x65, 0x43, 0x41, 0x52, 0x44, 0x76, 0x31,
        )

    private const val CLA_ISO: Byte = 0x00
    private const val INS_SELECT: Byte = 0xA4.toByte()
    private const val INS_COINKITE: Byte = 0xCB.toByte()
    private const val P1_SELECT_BY_NAME: Byte = 0x04
    private const val P2_FIRST_OR_ONLY: Byte = 0x00
    private const val LE_ANY: Byte = 0x00

    const val SW_OK: Int = 0x9000

    /** Build the `SELECT AID` APDU that must be the first command after tap. */
    fun buildSelectAid(): ByteArray = buildApdu(CLA_ISO, INS_SELECT, P1_SELECT_BY_NAME, P2_FIRST_OR_ONLY, APPLET_AID)

    /** Wrap a CBOR command payload into a Coinkite APDU (INS=0xCB). */
    fun buildCoinkiteApdu(cborPayload: ByteArray): ByteArray {
        require(cborPayload.size in 1..255) {
            "Coinkite APDU payload must fit in a short APDU (got ${cborPayload.size} bytes)"
        }
        return buildApdu(CLA_ISO, INS_COINKITE, 0x00, 0x00, cborPayload)
    }

    /**
     * Split a raw APDU response into [data] (everything except the final two
     * bytes) and [sw] (the status word, big-endian).
     */
    fun parseResponse(rawResponse: ByteArray): Response {
        require(rawResponse.size >= 2) { "APDU response truncated (${rawResponse.size} bytes)" }
        val swHi = rawResponse[rawResponse.size - 2].toInt() and 0xFF
        val swLo = rawResponse[rawResponse.size - 1].toInt() and 0xFF
        val sw = (swHi shl 8) or swLo
        val data = rawResponse.copyOfRange(0, rawResponse.size - 2)
        return Response(data = data, sw = sw)
    }

    private fun buildApdu(
        cla: Byte,
        ins: Byte,
        p1: Byte,
        p2: Byte,
        data: ByteArray,
    ): ByteArray {
        // CLA INS P1 P2 LC <data...> LE
        val apdu = ByteArray(5 + data.size + 1)
        apdu[0] = cla
        apdu[1] = ins
        apdu[2] = p1
        apdu[3] = p2
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        apdu[apdu.size - 1] = LE_ANY
        return apdu
    }

    data class Response(val data: ByteArray, val sw: Int) {
        val isOk: Boolean get() = sw == SW_OK

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Response) return false
            return sw == other.sw && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * sw + data.contentHashCode()
    }
}
