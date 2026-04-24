package com.possatstack.app.wallet

/**
 * Reconstructable state of an on-chain wallet. Persisted in
 * [com.possatstack.app.wallet.storage.WalletStorage] after creation/import.
 *
 * **Does not include the mnemonic.** The mnemonic is high-sensitivity and
 * lives in [com.possatstack.app.wallet.signer.SignerSecretStore], protected
 * by its own Keystore master key (StrongBox when available) on a dedicated
 * prefs file. If you need to present the seed phrase to the user, go through
 * [OnChainWalletEngine.exportBackup], not this data class.
 *
 * The descriptor strings follow BIP-380 notation. They are portable across
 * descriptor-aware libraries (BDK, future alternatives) but require that the
 * chosen engine supports the descriptor kind (`wpkh(...)` for now).
 */
data class WalletDescriptor(
    val externalDescriptor: String,
    val internalDescriptor: String,
    val network: WalletNetwork,
)
