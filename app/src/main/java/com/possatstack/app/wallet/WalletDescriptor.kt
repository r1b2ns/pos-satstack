package com.possatstack.app.wallet

/**
 * Holds everything needed to reconstruct and optionally back up a wallet.
 *
 * [externalDescriptor] and [internalDescriptor] are sufficient for recovering
 * funds. [mnemonic] is set when the wallet is first created and should be
 * presented to the user for an external backup. It is null when the wallet is
 * loaded from storage without the original seed phrase.
 */
data class WalletDescriptor(
    val externalDescriptor: String,
    val internalDescriptor: String,
    val network: WalletNetwork,
    /** BIP-39 mnemonic. Present on creation, null on subsequent loads. */
    val mnemonic: String? = null,
)
