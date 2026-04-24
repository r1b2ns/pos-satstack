package com.possatstack.app.wallet.chain

import com.possatstack.app.wallet.FeeEstimate
import com.possatstack.app.wallet.FeeTarget
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.WalletNetwork

/**
 * Library-agnostic chain data source.
 *
 * The [com.possatstack.app.wallet.OnChainWalletEngine] implementation owns the
 * wallet state (descriptors, UTXOs, revealed indexes) and asks a
 * [ChainDataSource] for anything that requires talking to the network:
 * broadcasting a signed tx, looking up fees, querying chain tip.
 *
 * Today the project ships with [EsploraChainDataSource]. Swapping to Kyoto
 * (BIP-157/158) or Floresta (Utreexo) in the future is a single binding change
 * in `WalletModule` + a gradle dep flip; see `libs.versions.toml` for the
 * commented alternatives.
 */
interface ChainDataSource {
    /**
     * Bind the data source to a specific network. Must be called once at
     * wallet load time; subsequent calls with a different network rebuild
     * any cached client/connection.
     */
    fun configureFor(network: WalletNetwork)

    /** Broadcast a serialized transaction to the network. */
    suspend fun broadcastRaw(rawTx: ByteArray): Txid

    /**
     * Ask the source for a fee estimate at the given confirmation [target].
     * Implementations may snap to the closest target they support.
     */
    suspend fun estimateFees(target: FeeTarget): FeeEstimate

    /** Chain tip height. */
    suspend fun getBlockHeight(): Long
}
