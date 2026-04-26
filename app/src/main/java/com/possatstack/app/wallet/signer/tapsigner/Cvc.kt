package com.possatstack.app.wallet.signer.tapsigner

/**
 * The 6-to-8-digit code printed on the back of every TAPSIGNER card.
 *
 * Treated as a short-lived secret: the user types it per signing session and
 * the app never persists it. The value is held only in the bytes wrapped by
 * this class, which the caller is expected to zero out ([wipe]) after the
 * exchange completes.
 *
 * The card enforces a rising backoff after repeated wrong entries; the UI
 * surfaces [com.possatstack.app.wallet.signer.tapsigner.TapsignerError.RateLimited]
 * in that case.
 */
class Cvc private constructor(private val digits: ByteArray) {
    val length: Int get() = digits.size

    fun asBytes(): ByteArray = digits.copyOf()

    fun wipe() {
        digits.fill(0)
    }

    companion object {
        const val MIN_LENGTH = 6
        const val MAX_LENGTH = 8

        fun parse(input: String): Cvc {
            val trimmed = input.trim()
            require(trimmed.length in MIN_LENGTH..MAX_LENGTH) {
                "CVC must have between $MIN_LENGTH and $MAX_LENGTH digits (got ${trimmed.length})"
            }
            require(trimmed.all { it.isDigit() }) {
                "CVC must contain only digits"
            }
            return Cvc(trimmed.toByteArray(Charsets.US_ASCII))
        }

        fun isValidShape(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed.length in MIN_LENGTH..MAX_LENGTH && trimmed.all { it.isDigit() }
        }
    }
}
