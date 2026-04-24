package com.possatstack.app.wallet.chain

import com.possatstack.app.wallet.FeeEstimate
import com.possatstack.app.wallet.FeeTarget
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.WalletNetwork

class FakeChainDataSource : ChainDataSource {
    var configuredFor: WalletNetwork? = null

    var broadcastResult: Result<Txid> = Result.success(Txid("fa" + "0".repeat(62)))
    var estimateFeesResult: Result<FeeEstimate> = Result.success(FeeEstimate(10.0, 6))
    var blockHeightResult: Result<Long> = Result.success(800_000L)

    var broadcastCalls = mutableListOf<ByteArray>()
    var estimateFeesCalls = mutableListOf<FeeTarget>()
    var blockHeightCount = 0
    var configureForCalls = mutableListOf<WalletNetwork>()

    override fun configureFor(network: WalletNetwork) {
        configuredFor = network
        configureForCalls += network
    }

    override suspend fun broadcastRaw(rawTx: ByteArray): Txid {
        broadcastCalls += rawTx
        return broadcastResult.getOrThrow()
    }

    override suspend fun estimateFees(target: FeeTarget): FeeEstimate {
        estimateFeesCalls += target
        return estimateFeesResult.getOrThrow()
    }

    override suspend fun getBlockHeight(): Long {
        blockHeightCount++
        return blockHeightResult.getOrThrow()
    }
}
