package com.possatstack.app.wallet

/**
 * Abstraction over wallet operations. All app code must depend on this interface,
 * never on a concrete implementation (e.g. BDK). This makes it possible to swap
 * the underlying library or add support for other cryptocurrencies without touching
 * the rest of the codebase.
 */
interface WalletRepository {

    /**
     * Generates a new BIP-39 mnemonic, derives BIP-84 (native SegWit) descriptors,
     * and initialises the wallet in memory. Returns the [WalletDescriptor] that
     * must be persisted by the caller for future [loadWallet] calls.
     */
    suspend fun createWallet(network: WalletNetwork): WalletDescriptor

    /**
     * Reconstructs the wallet from previously stored [WalletDescriptor].
     */
    suspend fun loadWallet(descriptor: WalletDescriptor)

    /**
     * Restores a wallet from a BIP-39 mnemonic phrase (12 or 24 words).
     * Derives BIP-84 (native SegWit) descriptors, wipes any existing SQLite
     * data, and initialises the wallet in memory.
     * Throws [org.bitcoindevkit.Bip39Exception] if the mnemonic is invalid.
     */
    suspend fun importWallet(mnemonic: String, network: WalletNetwork): WalletDescriptor

    /**
     * Derives and returns the next unused receive address.
     */
    suspend fun getNewReceiveAddress(): BitcoinAddress
}
