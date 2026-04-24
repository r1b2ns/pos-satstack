package com.possatstack.app.wallet

import com.possatstack.app.wallet.signer.BiometricAuthenticator

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
     * transferring to another wallet. Accessing the mnemonic payload may
     * prompt the user via [auth] if the secret store requires it.
     */
    suspend fun exportBackup(auth: BiometricAuthenticator): WalletBackup

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
     * Sign the PSBT with the wallet's seed. [auth] is consulted if the secret
     * store demands user presence. In Fase 3 this responsibility moves to a
     * separate `Signer`; for now the engine signs.
     */
    suspend fun signPsbt(
        psbt: UnsignedPsbt,
        auth: BiometricAuthenticator,
    ): SignedPsbt

    /**
     * Submit a finalised signed PSBT to the network and return the resulting
     * [Txid].
     */
    suspend fun broadcast(signed: SignedPsbt): Txid

    /**
     * Produce an **unsigned** replacement PSBT for [txid] at the new fee. RBF
     * must be enabled on the original transaction. The returned PSBT must
     * then go through [signPsbt] + [broadcast] like any other send.
     */
    suspend fun bumpFee(
        txid: Txid,
        newPolicy: FeePolicy,
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
