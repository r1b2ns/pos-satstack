package com.possatstack.app.wallet.signer.tapsigner

/**
 * Parsed response bodies returned by TAPSIGNER commands.
 *
 * The card speaks CBOR: success frames contain command-specific keys,
 * failure frames carry `error` + `code` and optionally `auth_delay` for
 * rate limits. [parseStatus], [parseXpub], and [parseSign] turn the raw
 * CBOR map into strongly-typed results and raise a
 * [TapsignerError.ProtocolError] if the shape is off.
 */
internal object TapsignerResponse {
    // Error code set exposed by TAPSIGNER. The card returns numeric codes;
    // we only need to distinguish the ones that change UI behaviour.
    private const val ERROR_CODE_BAD_CVC: Long = 429
    private const val ERROR_CODE_RATE_LIMITED: Long = 423
    private const val ERROR_CODE_NOT_SET_UP: Long = 406

    /** Decode the response body into a map or raise a neutral error. */
    fun decodeOrThrow(payload: ByteArray): Map<String, Any?> {
        val map =
            try {
                Cbor.decodeMap(payload)
            } catch (exception: Exception) {
                throw TapsignerError.ProtocolError("response is not a CBOR map", exception)
            }
        val error = map["error"]
        if (error != null) throw mapErrorToTapsignerError(map)
        return map
    }

    fun parseStatus(map: Map<String, Any?>): StatusResponse {
        val proto = map.requireLong("proto")
        val birth = map["birth"] as? Long
        val slots = (map["slots"] as? List<*>)?.mapNotNull { it as? Long }
        val cardNonce = map.requireBytes("card_nonce")
        val pubkey = map["pubkey"] as? ByteArray
        val isTestnet = map["testnet"] as? Long
        val path = (map["path"] as? List<*>)?.mapNotNull { it as? Long }
        return StatusResponse(
            proto = proto,
            birth = birth,
            slots = slots,
            cardNonce = cardNonce,
            cardPubkey = pubkey,
            isTestnet = isTestnet == 1L,
            currentDerivation = path,
        )
    }

    fun parseXpub(map: Map<String, Any?>): XpubResponse {
        val xpub = map.requireBytes("xpub")
        return XpubResponse(xpub = xpub)
    }

    fun parseSign(map: Map<String, Any?>): SignResponse {
        val sig = map.requireBytes("sig")
        val pubkey = map.requireBytes("pubkey")
        val cardNonce = map.requireBytes("card_nonce")
        return SignResponse(signature = sig, pubkey = pubkey, cardNonce = cardNonce)
    }

    private fun mapErrorToTapsignerError(map: Map<String, Any?>): TapsignerError {
        val code = map["code"] as? Long
        val reason = (map["error"] as? String).orEmpty()
        return when (code) {
            ERROR_CODE_BAD_CVC -> TapsignerError.WrongCvc(attemptsLeft = (map["attempts_left"] as? Long)?.toInt())
            ERROR_CODE_RATE_LIMITED -> {
                val delay = (map["auth_delay"] as? Long)?.toInt() ?: 0
                TapsignerError.RateLimited(waitSeconds = delay)
            }
            ERROR_CODE_NOT_SET_UP -> TapsignerError.NotSetUp
            else -> TapsignerError.ProtocolError("card error ${code ?: "?"}: $reason")
        }
    }

    private fun Map<String, Any?>.requireLong(key: String): Long =
        (this[key] as? Long) ?: throw TapsignerError.ProtocolError("missing `$key` in response")

    private fun Map<String, Any?>.requireBytes(key: String): ByteArray =
        (this[key] as? ByteArray) ?: throw TapsignerError.ProtocolError("missing `$key` in response")

    data class StatusResponse(
        val proto: Long,
        val birth: Long?,
        val slots: List<Long>?,
        val cardNonce: ByteArray,
        val cardPubkey: ByteArray?,
        val isTestnet: Boolean,
        val currentDerivation: List<Long>?,
    )

    data class XpubResponse(val xpub: ByteArray)

    data class SignResponse(val signature: ByteArray, val pubkey: ByteArray, val cardNonce: ByteArray)
}
