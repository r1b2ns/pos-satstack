package com.possatstack.app.wallet

/**
 * Chosen fee for a transaction build or fee bump.
 *
 * [TargetBlocks]  — resolve to a rate that aims for confirmation within N blocks
 *                   (the engine queries its [com.possatstack.app.wallet.chain.ChainDataSource]).
 * [SatsPerVb]     — explicit virtual-byte rate; the engine uses it verbatim.
 * [Absolute]      — total absolute fee in satoshis.
 */
sealed interface FeePolicy {
    data class TargetBlocks(val blocks: Int) : FeePolicy

    data class SatsPerVb(val rate: Double) : FeePolicy

    data class Absolute(val totalSats: Long) : FeePolicy
}

/** Query parameter for [com.possatstack.app.wallet.OnChainWalletEngine.estimateFees]. */
data class FeeTarget(val blocks: Int)

/** Estimated fee for a given confirmation target. */
data class FeeEstimate(
    val satsPerVb: Double,
    val targetBlocks: Int,
)
