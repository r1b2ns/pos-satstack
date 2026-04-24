package com.possatstack.app.wallet.payment

import kotlinx.coroutines.flow.Flow

/**
 * Top-level entry point for creating and tracking charges.
 *
 * Sits above [com.possatstack.app.wallet.OnChainWalletEngine] (and, in
 * Fase 5, above `LightningEngine` and `BearerMethodEngine`). The UI
 * knows only about charges and methods — it never talks to an engine
 * directly, so adding a new method means implementing a new engine +
 * extending this orchestrator, without touching the UI.
 */
interface PaymentOrchestrator {
    /** Payment methods that are enabled in the current build & wallet state. */
    suspend fun availableMethods(): List<PaymentMethod>

    /**
     * Create a new charge using [method]. For [PaymentMethod.OnChain] this
     * reveals a new receive address and wraps it in a BIP-21 URI; future
     * methods produce their own payload (bolt11 invoice, SATSCARD slot).
     *
     * @throws com.possatstack.app.wallet.WalletError.NoWallet           if no wallet is loaded.
     * @throws com.possatstack.app.wallet.WalletError if the underlying engine can't produce the payload.
     */
    suspend fun createCharge(
        method: PaymentMethod,
        amountSats: Long,
        memo: String? = null,
    ): Charge

    /**
     * Stream of status updates for the given charge. Emits the current
     * state on collection and then new states whenever the underlying
     * engine detects a change (new tx in mempool, confirmation, etc).
     * Returns an empty flow for unknown charges.
     */
    fun chargeStatus(chargeId: String): Flow<ChargeStatus>

    /**
     * Mark [chargeId] as [ChargeStatus.Cancelled]. Further status emissions
     * stop. Safe to call on unknown ids (no-op).
     */
    suspend fun cancelCharge(chargeId: String)

    /** Retrieve the [Charge] record by id, or null if unknown/cancelled. */
    fun getCharge(chargeId: String): Charge?
}
