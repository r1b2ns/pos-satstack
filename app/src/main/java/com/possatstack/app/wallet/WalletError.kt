package com.possatstack.app.wallet

/**
 * Library-agnostic error surface for on-chain wallet operations.
 *
 * Every concrete [com.possatstack.app.wallet.bitcoin.BdkOnChainEngine] exception
 * must be mapped into one of these cases before reaching UI or ViewModel code.
 * This is what keeps the app independent of BDK (or whatever on-chain engine
 * replaces it later).
 */
sealed class WalletError(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** BIP-39 mnemonic could not be parsed (unknown word, bad checksum, wrong length). */
    data object InvalidMnemonic : WalletError("Invalid BIP-39 mnemonic")

    /** Bitcoin address string could not be parsed for the active network. */
    data class InvalidAddress(val input: String) : WalletError("Invalid Bitcoin address: $input")

    /** Connecting to the configured chain data source failed (network/DNS/TLS/HTTP 5xx). */
    data class ChainSourceUnreachable(
        override val cause: Throwable,
    ) : WalletError("Chain data source unreachable", cause)

    /** Wallet cannot fund the requested transaction. */
    data object InsufficientFunds : WalletError("Insufficient funds")

    /** Fee policy produced a fee below the current mempool min-relay rate. */
    data object FeeTooLow : WalletError("Fee below min-relay rate")

    /** Attempted to bump a tx that either does not exist or is already confirmed. */
    data class CannotBumpFee(val reason: String) : WalletError("Cannot bump fee: $reason")

    /** Signing failed (wrong key, PSBT malformed, user cancelled auth). */
    data class SigningFailed(val reason: String) : WalletError("Signing failed: $reason")

    /** Mnemonic could not be read from secure storage (no auth, hardware unavailable, etc). */
    data class SecretStoreUnavailable(val reason: String) : WalletError("Secret store unavailable: $reason")

    /** Engine has no loaded wallet — caller must create or import one first. */
    data object NoWallet : WalletError("No wallet loaded")

    /** Anything else from the underlying engine; prefer mapping to a specific case. */
    data class Unknown(override val cause: Throwable) : WalletError("Unknown wallet error", cause)
}
