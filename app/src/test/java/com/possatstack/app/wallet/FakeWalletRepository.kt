package com.possatstack.app.wallet

class FakeWalletRepository : WalletRepository {

    var createWalletResult: Result<WalletDescriptor> = Result.success(
        WalletDescriptor(
            externalDescriptor = "external",
            internalDescriptor = "internal",
            network = WalletNetwork.SIGNET,
            mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        ),
    )
    var importWalletResult: Result<WalletDescriptor> = createWalletResult
    var loadWalletResult: Result<Unit> = Result.success(Unit)
    var syncResult: Result<Unit> = Result.success(Unit)
    var balanceResult: Result<Long> = Result.success(0L)
    var transactionsResult: Result<List<WalletTransaction>> = Result.success(emptyList())
    var receiveAddressResult: Result<BitcoinAddress> = Result.success(BitcoinAddress("bc1qtest"))
    var unsignedPsbtResult: Result<UnsignedPsbt> = Result.success(UnsignedPsbt("cHNidP8BAA=="))

    var createCount = 0
    var importCount = 0
    var loadCount = 0
    var syncCallsArgs = mutableListOf<Pair<WalletNetwork, Boolean>>()
    var balanceCount = 0
    var transactionsCount = 0
    var receiveAddressCount = 0
    val unsignedPsbtCallsArgs = mutableListOf<Triple<BitcoinAddress, Long, Long?>>()

    override suspend fun createWallet(network: WalletNetwork): WalletDescriptor {
        createCount++
        return createWalletResult.getOrThrow()
    }

    override suspend fun loadWallet(descriptor: WalletDescriptor) {
        loadCount++
        loadWalletResult.getOrThrow()
    }

    override suspend fun importWallet(mnemonic: String, network: WalletNetwork): WalletDescriptor {
        importCount++
        return importWalletResult.getOrThrow()
    }

    override suspend fun getNewReceiveAddress(): BitcoinAddress {
        receiveAddressCount++
        return receiveAddressResult.getOrThrow()
    }

    override suspend fun syncWallet(network: WalletNetwork, isFullScan: Boolean) {
        syncCallsArgs += network to isFullScan
        syncResult.getOrThrow()
    }

    override suspend fun getBalance(): Long {
        balanceCount++
        return balanceResult.getOrThrow()
    }

    override suspend fun getTransactions(): List<WalletTransaction> {
        transactionsCount++
        return transactionsResult.getOrThrow()
    }

    override suspend fun createUnsignedPsbt(
        recipient: BitcoinAddress,
        amountSats: Long,
        feeRateSatPerVb: Long?,
    ): UnsignedPsbt {
        unsignedPsbtCallsArgs += Triple(recipient, amountSats, feeRateSatPerVb)
        return unsignedPsbtResult.getOrThrow()
    }
}
