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

    /**
     * Synchronises the wallet with the network via Electrum.
     *
     * Pass [isFullScan] = true for an initial scan of all script public keys
     * (slower, used when the wallet has never been synced before).
     * Pass false for an incremental sync of already-revealed addresses (faster).
     *
     * Throws [org.bitcoindevkit.ElectrumException] on connection failure.
     * Throws [IllegalStateException] if the wallet has not been initialised.
     */
    suspend fun syncWallet(network: WalletNetwork, isFullScan: Boolean)

    /**
     * Returns the wallet balance in satoshis (confirmed + trusted pending).
     * Throws [IllegalStateException] if the wallet has not been initialised.
     */
    suspend fun getBalance(): Long

    /**
     * Returns all transactions known to the wallet, sorted by confirmation time
     * descending (most recent first). Unconfirmed transactions appear before
     * confirmed ones.
     * Throws [IllegalStateException] if the wallet has not been initialised.
     */
    suspend fun getTransactions(): List<WalletTransaction>

    /**
     * Builds an **unsigned** PSBT (BIP-174) sending [amountSats] to [recipient].
     *
     * The returned PSBT contains all inputs (selected by the wallet's coin
     * selection algorithm), the recipient output, the change output and the
     * computed fee. No signatures are produced — the PSBT must be signed by an
     * external signer before it can be broadcast.
     *
     * @param recipient destination address. Must match the wallet's network.
     * @param amountSats amount to send, in satoshis. Must be greater than zero
     *   and not exceed the wallet's spendable balance.
     * @param feeRateSatPerVb desired fee rate in satoshis per virtual byte.
     *   When null, the wallet falls back to its default fee policy.
     *
     * @throws IllegalStateException if the wallet has not been initialised.
     * @throws IllegalArgumentException if [amountSats] is not positive or if
     *   [recipient] is not a valid address for the wallet's network.
     */
    suspend fun createUnsignedPsbt(
        recipient: BitcoinAddress,
        amountSats: Long,
        feeRateSatPerVb: Long? = null,
    ): UnsignedPsbt
}
