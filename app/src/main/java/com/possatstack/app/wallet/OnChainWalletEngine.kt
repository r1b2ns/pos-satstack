package com.possatstack.app.wallet

/**
 * Library-agnostic contract for on-chain Bitcoin wallet operations.
 *
 * All app code (ViewModels, Services, UI) depends on this interface, never on
 * a concrete implementation. Swapping the underlying wallet library (today
 * BDK, tomorrow a hypothetical Rust-wallets alternative) is a single DI
 * binding change in `WalletModule`.
 *
 * The `PaymentMethod`/`PaymentOrchestrator` layer (Fase 4) will wrap this for
 * cobrança-side use cases; for Fases 1 and 2, the ViewModel still talks to
 * the engine directly.
 *
 * ## Error surface
 *
 * Every function may throw a [WalletError]. Implementations translate the
 * underlying library's exceptions into one of those cases before re-throwing.
 * Callers must never see `org.bitcoindevkit.*` (or any other lib-specific)
 * exception types.
 */
interface OnChainWalletEngine {
    // ─────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────

    /** True when a wallet has been created or imported on this device. */
    suspend fun hasWallet(): Boolean

    /** Warm the engine from persisted descriptors + secret store. No-op when already loaded. */
    suspend fun loadWallet()

    /**
     * Generate a new BIP-39 mnemonic, derive BIP-84 descriptors, and persist
     * both (mnemonic to [com.possatstack.app.wallet.signer.SignerSecretStore],
     * descriptors to [com.possatstack.app.wallet.storage.WalletStorage]).
     */
    suspend fun createWallet(network: WalletNetwork)

    /**
     * Restore from a [WalletBackup]. Currently supports [WalletBackup.Bip39];
     * future kinds (xpub watching, channel state) can be added without
     * breaking existing call sites.
     *
     * The caller is responsible for zeroing any [CharArray] inside [backup]
     * after this call returns.
     */
    suspend fun importWallet(backup: WalletBackup)

    /** Wipe both descriptor storage and mnemonic secret store. Irreversible. */
    suspend fun deleteWallet()

    /**
     * Export a backup suitable for showing the user (Bip39 mnemonic) or
     * transferring to another wallet.
     */
    suspend fun exportBackup(): WalletBackup

    // ─────────────────────────────────────────────────────────────────
    //  Receiving
    // ─────────────────────────────────────────────────────────────────

    /** Reveal and persist the next unused external address. */
    suspend fun getNewReceiveAddress(): BitcoinAddress

    // ─────────────────────────────────────────────────────────────────
    //  Sending — unsigned PSBT → sign → broadcast (Fase 2)
    // ─────────────────────────────────────────────────────────────────

    /** Build an unsigned PSBT for the given recipients under [feePolicy]. */
    suspend fun createUnsignedPsbt(
        recipients: List<PsbtRecipient>,
        feePolicy: FeePolicy,
    ): UnsignedPsbt

    /**
     * Submit a finalised signed PSBT to the network and return the resulting
     * [Txid]. Signing happens outside the engine via a
     * [com.possatstack.app.wallet.signer.Signer] (software seed, TAPSIGNER,
     * etc.) — the engine only builds unsigned PSBTs and broadcasts signed ones.
     */
    suspend fun broadcast(signed: SignedPsbt): Txid

    /**
     * Produce an **unsigned** replacement PSBT for [txid] at the new fee. RBF
     * must be enabled on the original transaction. The returned PSBT must go
     * through a [com.possatstack.app.wallet.signer.Signer] and then [broadcast]
     * like any other send.
     */
    suspend fun bumpFee(
        txid: Txid,
        newPolicy: FeePolicy,
    ): UnsignedPsbt

    /**
     * Build an unsigned PSBT that spends from a **foreign** BIP-84 xpub
     * (e.g., a TAPSIGNER held by the customer) into [recipient]. The loaded
     * merchant wallet is not used — a transient watch-only BDK wallet is
     * created from the descriptors below, synced once, and used solely to
     * build the transaction.
     *
     * The descriptors registered with BDK are:
     *   - receive: `wpkh([fp/path]xpub/0/&#42;)`
     *   - change:  `wpkh([fp/path]xpub/1/&#42;)`
     *
     * BDK fills `bip32_derivation` on every input so the external signer
     * (TAPSIGNER) can find its key and produce the signature.
     *
     * @param accountXpub xpub at the BIP-84 account level (depth 3).
     * @param masterFingerprint 8 hex chars of the external master key.
     * @param accountDerivationPath BIP-84 path from master to the account
     *        xpub, e.g. `"84'/0'/0'"` (no leading `m/`).
     * @param network the network of the external signer (must match
     *        [recipient]'s network — otherwise BDK rejects the build).
     * @throws com.possatstack.app.wallet.WalletError on sync / build failure.
     */
    suspend fun buildPsbtFromExternalSigner(
        accountXpub: String,
        masterFingerprint: String,
        accountDerivationPath: String,
        network: WalletNetwork,
        recipient: PsbtRecipient,
        feePolicy: FeePolicy,
    ): UnsignedPsbt

    // ─────────────────────────────────────────────────────────────────
    //  Fees
    // ─────────────────────────────────────────────────────────────────

    /** Ask the configured chain data source for an estimate. */
    suspend fun estimateFees(target: FeeTarget): FeeEstimate

    // ─────────────────────────────────────────────────────────────────
    //  State
    // ─────────────────────────────────────────────────────────────────

    /** Satoshi balance breakdown. */
    suspend fun getBalance(): Balance

    /** Known transactions, newest first. */
    suspend fun getTransactions(): List<WalletTransaction>

    /** Network of the loaded wallet, or null if none. */
    suspend fun getNetwork(): WalletNetwork?

    // ─────────────────────────────────────────────────────────────────
    //  Sync
    // ─────────────────────────────────────────────────────────────────

    /**
     * Synchronise local state with the chain data source. The engine decides
     * between full-scan and incremental internally (based on persisted state
     * in [com.possatstack.app.wallet.storage.WalletStorage]).
     */
    suspend fun sync(onProgress: (SyncProgress) -> Unit = {})
}
