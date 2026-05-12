package com.possatstack.app.wallet

/**
 * Lightweight representation of a wallet transaction, decoupled from the
 * underlying BDK library so it can be consumed directly by the UI layer.
 */
data class WalletTransaction(
    val txid: String,
    val sentSats: Long,
    val receivedSats: Long,
    val feeSats: Long?,
    val confirmationTime: Long?,
    val blockHeight: Long?,
    val isConfirmed: Boolean,
    /**
     * For incoming transactions: a wallet-owned output address.
     * For outgoing transactions: an external recipient address.
     * `null` when the address cannot be decoded (e.g. non-standard script).
     */
    val address: String? = null,
) {
    /** Net effect on the wallet balance in satoshis (positive = incoming). */
    val netSats: Long get() = receivedSats - sentSats
}
