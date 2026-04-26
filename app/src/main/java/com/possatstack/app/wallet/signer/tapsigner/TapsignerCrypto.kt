package com.possatstack.app.wallet.signer.tapsigner

/**
 * Crypto primitives needed by the TAPSIGNER tap-protocol: secp256k1 ECDH
 * (for deriving a per-session key from the card's pubkey) and the CVC
 * encryption / signature-reassembly helpers specified by Coinkite.
 *
 * Kept behind an interface because Android's stock providers filter
 * secp256k1 for security-policy reasons; the concrete wiring will come
 * from a dedicated secp256k1 library in the follow-up hardware QA pass.
 * The rest of the session / UI layer works against this interface today
 * and can be exercised with a fake.
 */
internal interface TapsignerCrypto {
    /** Generate an ephemeral secp256k1 key-pair for the session. */
    fun generateEphemeralKeyPair(): EphemeralKeyPair

    /** Compute the ECDH shared secret (SHA-256 of the compressed X coordinate). */
    fun deriveSharedSecret(
        ephemeralPrivate: ByteArray,
        cardPubkey: ByteArray,
    ): ByteArray

    /** Encrypt the CVC with the session key per Coinkite spec (XOR stream). */
    fun encryptCvc(
        cvc: Cvc,
        cardNonce: ByteArray,
        ourNonce: ByteArray,
        sessionKey: ByteArray,
    ): ByteArray

    /** Verify the signature/pubkey pair returned by a `sign` command. */
    fun verifyCardSignature(
        signature: ByteArray,
        digest: ByteArray,
        pubkey: ByteArray,
    ): Boolean

    data class EphemeralKeyPair(
        val privateKey: ByteArray,
        val publicKeyCompressed: ByteArray,
    )
}

/**
 * Placeholder [TapsignerCrypto] that fails loudly whenever the session
 * tries to start the ECDH handshake. Shipped so the rest of the stack
 * compiles and is testable without pulling a secp256k1 library in yet.
 *
 * Swap this binding in [com.possatstack.app.di.WalletModule] once a
 * secp256k1 dependency is chosen and validated against real hardware.
 */
internal class UnavailableTapsignerCrypto : TapsignerCrypto {
    override fun generateEphemeralKeyPair(): TapsignerCrypto.EphemeralKeyPair =
        throw TapsignerError.HostError(CRYPTO_NOT_WIRED)

    override fun deriveSharedSecret(
        ephemeralPrivate: ByteArray,
        cardPubkey: ByteArray,
    ): ByteArray = throw TapsignerError.HostError(CRYPTO_NOT_WIRED)

    override fun encryptCvc(
        cvc: Cvc,
        cardNonce: ByteArray,
        ourNonce: ByteArray,
        sessionKey: ByteArray,
    ): ByteArray = throw TapsignerError.HostError(CRYPTO_NOT_WIRED)

    override fun verifyCardSignature(
        signature: ByteArray,
        digest: ByteArray,
        pubkey: ByteArray,
    ): Boolean = throw TapsignerError.HostError(CRYPTO_NOT_WIRED)

    private companion object {
        const val CRYPTO_NOT_WIRED =
            "secp256k1 crypto for TAPSIGNER is not wired; " +
                "replace UnavailableTapsignerCrypto with a secp256k1-backed impl"
    }
}
