package com.possatstack.app.wallet.signer.tapsigner

/**
 * High-level TAPSIGNER commands that the signer needs. Each command knows
 * how to serialise itself into a CBOR map per the tap-protocol spec.
 *
 * Only the subset used by the POS app is modelled:
 *  - [Status]: probe the card; required after `SELECT AID`.
 *  - [Xpub]: read the master xpub so the host can build/verify derivation.
 *  - [Sign]: sign a 32-byte digest with the key at the given subpath.
 *  - [Wait]: advance the card's rate-limit timer.
 *
 * The `epubkey` and `xcvc` fields are prepared by
 * [com.possatstack.app.wallet.signer.tapsigner.TapsignerSession] after the
 * ECDH handshake; raw commands here just shape the CBOR frame.
 */
internal sealed interface TapsignerCommand {
    val name: String

    fun toCborMap(): Map<String, Any?>

    fun encode(): ByteArray = Cbor.encode(toCborMap())

    data class Status(val nonce: ByteArray? = null) : TapsignerCommand {
        override val name = "status"

        override fun toCborMap(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            map["cmd"] = name
            if (nonce != null) map["nonce"] = nonce
            return map
        }
    }

    data class Xpub(
        val master: Boolean,
        val epubkey: ByteArray,
        val xcvc: ByteArray,
    ) : TapsignerCommand {
        override val name = "xpub"

        override fun toCborMap(): Map<String, Any?> =
            linkedMapOf(
                "cmd" to name,
                "master" to if (master) 1L else 0L,
                "epubkey" to epubkey,
                "xcvc" to xcvc,
            )
    }

    data class Sign(
        val digest: ByteArray,
        val subpath: List<Long>,
        val epubkey: ByteArray,
        val xcvc: ByteArray,
    ) : TapsignerCommand {
        override val name = "sign"

        init {
            require(digest.size == DIGEST_LENGTH) {
                "Sign digest must be $DIGEST_LENGTH bytes (got ${digest.size})"
            }
        }

        override fun toCborMap(): Map<String, Any?> =
            linkedMapOf(
                "cmd" to name,
                "digest" to digest,
                "subpath" to subpath,
                "epubkey" to epubkey,
                "xcvc" to xcvc,
            )
    }

    data object Wait : TapsignerCommand {
        override val name = "wait"

        override fun toCborMap(): Map<String, Any?> = linkedMapOf("cmd" to name)
    }

    companion object {
        const val DIGEST_LENGTH: Int = 32
    }
}
