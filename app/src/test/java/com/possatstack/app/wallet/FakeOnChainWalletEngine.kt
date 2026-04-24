package com.possatstack.app.wallet

import com.possatstack.app.wallet.signer.BiometricAuthenticator

/**
 * Test double for [OnChainWalletEngine]. Every call records a counter so tests
 * can verify interaction; every suspend function honours a [Result] so tests
 * can inject failure paths.
 */
class FakeOnChainWalletEngine : OnChainWalletEngine {
    // ── configurable results ─────────────────────────────────────────────
    var hasWalletValue: Boolean = false
    var network: WalletNetwork? = WalletNetwork.SIGNET

    var loadWalletResult: Result<Unit> = Result.success(Unit)
    var createWalletResult: Result<Unit> = Result.success(Unit)
    var importWalletResult: Result<Unit> = Result.success(Unit)
    var deleteWalletResult: Result<Unit> = Result.success(Unit)
    var exportBackupResult: () -> WalletBackup = {
        WalletBackup.Bip39(DEFAULT_MNEMONIC.toCharArray(), WalletNetwork.SIGNET)
    }

    var receiveAddressResult: Result<BitcoinAddress> = Result.success(BitcoinAddress("bc1qtest"))
    var createPsbtResult: Result<UnsignedPsbt> = Result.success(UnsignedPsbt("cHNidP//", "fp"))
    var signPsbtResult: Result<SignedPsbt> = Result.success(SignedPsbt("cHNidP//signed"))
    var broadcastResult: Result<Txid> = Result.success(Txid("abcd" + "0".repeat(60)))
    var bumpFeeResult: Result<UnsignedPsbt> = Result.success(UnsignedPsbt("cHNidP//bump", "fp"))
    var estimateFeesResult: Result<FeeEstimate> = Result.success(FeeEstimate(5.0, 6))

    var balanceResult: Result<Balance> = Result.success(Balance(0, 0, 0))
    var transactionsResult: Result<List<WalletTransaction>> = Result.success(emptyList())
    var syncResult: Result<Unit> = Result.success(Unit)

    // ── call counters ────────────────────────────────────────────────────
    var hasWalletCount = 0
    var loadWalletCount = 0
    var createWalletCount = 0
    var createWalletArgs = mutableListOf<WalletNetwork>()
    var importWalletCount = 0
    var importWalletBackups = mutableListOf<WalletBackup>()
    var deleteWalletCount = 0
    var exportBackupCount = 0
    var receiveAddressCount = 0
    var createPsbtCount = 0
    var signPsbtCount = 0
    var broadcastCount = 0
    var bumpFeeCount = 0
    var estimateFeesCount = 0
    var balanceCount = 0
    var transactionsCount = 0
    var networkQueryCount = 0
    var syncCount = 0
    var syncProgressReporter: ((SyncProgress) -> Unit)? = null

    // ── OnChainWalletEngine ─────────────────────────────────────────────

    override suspend fun hasWallet(): Boolean {
        hasWalletCount++
        return hasWalletValue
    }

    override suspend fun loadWallet() {
        loadWalletCount++
        loadWalletResult.getOrThrow()
        hasWalletValue = true
    }

    override suspend fun createWallet(network: WalletNetwork) {
        createWalletCount++
        createWalletArgs += network
        createWalletResult.getOrThrow()
        this.network = network
        hasWalletValue = true
    }

    override suspend fun importWallet(backup: WalletBackup) {
        importWalletCount++
        importWalletBackups += backup
        importWalletResult.getOrThrow()
        network = backup.network
        hasWalletValue = true
    }

    override suspend fun deleteWallet() {
        deleteWalletCount++
        deleteWalletResult.getOrThrow()
        hasWalletValue = false
        network = null
    }

    override suspend fun exportBackup(auth: BiometricAuthenticator): WalletBackup {
        exportBackupCount++
        return exportBackupResult()
    }

    override suspend fun getNewReceiveAddress(): BitcoinAddress {
        receiveAddressCount++
        return receiveAddressResult.getOrThrow()
    }

    override suspend fun createUnsignedPsbt(
        recipients: List<PsbtRecipient>,
        feePolicy: FeePolicy,
    ): UnsignedPsbt {
        createPsbtCount++
        return createPsbtResult.getOrThrow()
    }

    override suspend fun signPsbt(
        psbt: UnsignedPsbt,
        auth: BiometricAuthenticator,
    ): SignedPsbt {
        signPsbtCount++
        return signPsbtResult.getOrThrow()
    }

    override suspend fun broadcast(signed: SignedPsbt): Txid {
        broadcastCount++
        return broadcastResult.getOrThrow()
    }

    override suspend fun bumpFee(
        txid: Txid,
        newPolicy: FeePolicy,
    ): UnsignedPsbt {
        bumpFeeCount++
        return bumpFeeResult.getOrThrow()
    }

    override suspend fun estimateFees(target: FeeTarget): FeeEstimate {
        estimateFeesCount++
        return estimateFeesResult.getOrThrow()
    }

    override suspend fun getBalance(): Balance {
        balanceCount++
        return balanceResult.getOrThrow()
    }

    override suspend fun getTransactions(): List<WalletTransaction> {
        transactionsCount++
        return transactionsResult.getOrThrow()
    }

    override suspend fun getNetwork(): WalletNetwork? {
        networkQueryCount++
        return network
    }

    override suspend fun sync(onProgress: (SyncProgress) -> Unit) {
        syncCount++
        syncProgressReporter = onProgress
        syncResult.getOrThrow()
    }

    companion object {
        const val DEFAULT_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
