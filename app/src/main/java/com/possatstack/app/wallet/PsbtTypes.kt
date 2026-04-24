package com.possatstack.app.wallet

/** 32-byte transaction id encoded as a lowercase hex string. */
@JvmInline
value class Txid(val hex: String)

/** A spend output specified by the caller when building a transaction. */
data class PsbtRecipient(
    val address: BitcoinAddress,
    val amountSats: Long,
)

/**
 * Base64-encoded PSBT ready for signing.
 *
 * The [fingerprint] is a stable identifier of the wallet that produced the
 * PSBT (usually the master key fingerprint). Signers validate it before
 * applying their signature to avoid cross-wallet mistakes.
 */
data class UnsignedPsbt(
    val base64: String,
    val fingerprint: String,
)

/** Base64-encoded PSBT that has been signed and finalised. */
data class SignedPsbt(val base64: String)

/** Satoshi balance breakdown exposed by [OnChainWalletEngine.getBalance]. */
data class Balance(
    val confirmedSats: Long,
    val trustedPendingSats: Long,
    val untrustedPendingSats: Long,
) {
    /** Total spendable (confirmed + trusted pending). */
    val totalSats: Long get() = confirmedSats + trustedPendingSats
}
