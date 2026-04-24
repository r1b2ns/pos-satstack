package com.possatstack.app.wallet.payment

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.Txid

/**
 * A single charge created by the merchant. Immutable once generated.
 * [ChargeStatus] is tracked separately by [PaymentOrchestrator.chargeStatus].
 */
data class Charge(
    val id: String,
    val method: PaymentMethod,
    val amountSats: Long,
    val memo: String?,
    val payload: ChargePayload,
    val createdAtEpochMs: Long,
)

/**
 * What the UI actually shows to the customer: an address + BIP21 URI for
 * on-chain, a bolt11 invoice for Lightning, or a SATSCARD deposit slot.
 *
 * Each [PaymentMethod] has a matching payload shape so the UI can render
 * the correct widget (QR code, copyable string) without knowing which
 * engine produced it.
 */
sealed interface ChargePayload {
    /** On-chain: address + BIP21 URI (`bitcoin:address?amount=...&label=...`). */
    data class OnChainAddress(
        val address: BitcoinAddress,
        val bip21Uri: String,
    ) : ChargePayload

    /** Reserved for Fase 5. Bolt11 invoice. */
    data class LightningInvoice(val bolt11: String) : ChargePayload

    /** Reserved for Fase 5. SATSCARD deposit slot. */
    data class BearerSlot(val address: BitcoinAddress) : ChargePayload
}

/**
 * State of a charge, observed through [PaymentOrchestrator.chargeStatus].
 *
 * - [Pending]   — charge created, no incoming tx detected yet.
 * - [Detected]  — a matching tx is in the mempool (0 confirmations).
 * - [Confirmed] — the tx reached at least one confirmation.
 * - [Cancelled] — merchant explicitly cancelled the charge.
 * - [Expired]   — reserved for Lightning invoices that carry a timeout.
 * - [Failed]    — anything else (sync error, engine error, etc).
 */
sealed interface ChargeStatus {
    data object Pending : ChargeStatus

    data class Detected(val txid: Txid?) : ChargeStatus

    data class Confirmed(val txid: Txid?, val blockHeight: Long?) : ChargeStatus

    data object Cancelled : ChargeStatus

    data object Expired : ChargeStatus

    data class Failed(val reason: String) : ChargeStatus
}
