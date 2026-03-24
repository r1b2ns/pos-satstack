package com.possatstack.app.wallet

/**
 * Holds the output descriptor strings needed to reconstruct a wallet.
 * Both [externalDescriptor] and [internalDescriptor] should be stored securely
 * by the caller — losing them means losing access to funds.
 */
data class WalletDescriptor(
    val externalDescriptor: String,
    val internalDescriptor: String,
    val network: WalletNetwork,
)
