package com.possatstack.app.wallet.chain

import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletNetwork
import org.bitcoindevkit.Wallet

/**
 * Test double for [ChainSyncProvider]. Records every call and lets the
 * test inject a failure or a custom progress sequence.
 *
 * Mirrors [FakeChainDataSource] / [FakeOnChainWalletEngine] in style.
 */
class FakeChainSyncProvider : ChainSyncProvider {
    var syncResult: Result<Unit> = Result.success(Unit)
    var emitProgress: List<SyncProgress> = listOf(SyncProgress.Idle)

    var syncCount = 0
    var lastFullScan: Boolean? = null
    var lastNetwork: WalletNetwork? = null

    override suspend fun sync(
        wallet: Wallet,
        network: WalletNetwork,
        fullScan: Boolean,
        onProgress: (SyncProgress) -> Unit,
    ) {
        syncCount++
        lastFullScan = fullScan
        lastNetwork = network
        emitProgress.forEach(onProgress)
        syncResult.getOrThrow()
    }
}
