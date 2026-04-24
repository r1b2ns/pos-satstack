package com.possatstack.app.wallet.payment

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.WalletError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [PaymentOrchestrator] implementation.
 *
 * For Fase 4 only [PaymentMethod.OnChain] is wired; Lightning and
 * SATSCARD throw until their engines ship in Fase 5.
 *
 * ### Status tracking
 *
 * Each charge gets its own [MutableStateFlow] and a monitor coroutine.
 * The monitor loops every [POLL_INTERVAL_MS]:
 *
 *  1. Triggers an incremental sync via the engine.
 *  2. Fetches the current transaction list.
 *  3. Compares against the txid baseline captured at charge creation.
 *  4. Emits [ChargeStatus.Detected] for an unseen tx with
 *     `receivedSats >= amountSats`, or [ChargeStatus.Confirmed] when the
 *     same tx reaches at least one confirmation. Stops on confirmation.
 *
 * Multi-charge limitation: if two charges have the same amount and a
 * single incoming tx matches both baselines, attribution is
 * deterministic but first-match-wins. POS flow is normally one active
 * charge at a time, so this is acceptable for Fase 4.
 */
@Singleton
class DefaultPaymentOrchestrator internal constructor(
    private val engine: OnChainWalletEngine,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long,
) : PaymentOrchestrator {
    @Inject
    constructor(engine: OnChainWalletEngine) : this(
        engine = engine,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    )

    private val charges = ConcurrentHashMap<String, Charge>()
    private val statuses = ConcurrentHashMap<String, MutableStateFlow<ChargeStatus>>()
    private val monitors = ConcurrentHashMap<String, Job>()
    private val baselines = ConcurrentHashMap<String, Set<String>>()

    override suspend fun availableMethods(): List<PaymentMethod> =
        if (engine.hasWallet()) listOf(PaymentMethod.OnChain) else emptyList()

    override suspend fun createCharge(
        method: PaymentMethod,
        amountSats: Long,
        memo: String?,
    ): Charge {
        require(amountSats > 0) { "amountSats must be > 0" }
        return when (method) {
            is PaymentMethod.OnChain -> createOnChainCharge(amountSats, memo)
            is PaymentMethod.Lightning ->
                throw WalletError.Unknown(UnsupportedOperationException("Lightning charges arrive in Fase 5"))
            is PaymentMethod.BearerCard ->
                throw WalletError.Unknown(UnsupportedOperationException("SATSCARD charges arrive in Fase 5"))
        }
    }

    override fun chargeStatus(chargeId: String): Flow<ChargeStatus> = statuses[chargeId]?.asStateFlow() ?: emptyFlow()

    override fun getCharge(chargeId: String): Charge? = charges[chargeId]

    override suspend fun cancelCharge(chargeId: String) {
        monitors.remove(chargeId)?.cancel()
        statuses[chargeId]?.value = ChargeStatus.Cancelled
        baselines.remove(chargeId)
        AppLogger.info(TAG, "Charge $chargeId cancelled")
    }

    // ─────────────────────────────────────────────────────────────────────

    private suspend fun createOnChainCharge(
        amountSats: Long,
        memo: String?,
    ): Charge {
        if (!engine.hasWallet()) throw WalletError.NoWallet

        val address = engine.getNewReceiveAddress()
        val baselineTxids =
            runCatching { engine.getTransactions() }
                .getOrNull().orEmpty()
                .map { it.txid }
                .toSet()

        val id = UUID.randomUUID().toString()
        val charge =
            Charge(
                id = id,
                method = PaymentMethod.OnChain,
                amountSats = amountSats,
                memo = memo,
                payload =
                    ChargePayload.OnChainAddress(
                        address = address,
                        bip21Uri = Bip21.build(address, amountSats, memo),
                    ),
                createdAtEpochMs = System.currentTimeMillis(),
            )

        charges[id] = charge
        baselines[id] = baselineTxids
        statuses[id] = MutableStateFlow(ChargeStatus.Pending)
        monitors[id] = scope.launch { monitorOnChainCharge(id) }

        AppLogger.info(TAG, "Charge $id created for $amountSats sat → ${address.value}")
        return charge
    }

    /**
     * Runs until the charge is cancelled or confirmed. On each tick it
     * triggers a sync and looks for a new tx that pays the charge amount.
     */
    private suspend fun monitorOnChainCharge(chargeId: String) {
        val charge = charges[chargeId] ?: return
        val flow = statuses[chargeId] ?: return
        val baseline = baselines[chargeId] ?: return

        while (true) {
            runCatching { engine.sync() }.onFailure { exception ->
                AppLogger.info(TAG, "sync during charge monitor failed: ${exception.message}")
            }

            val transactions =
                runCatching { engine.getTransactions() }
                    .getOrNull().orEmpty()
            val incoming =
                transactions
                    .filter { it.txid !in baseline && it.receivedSats >= charge.amountSats }
                    .maxByOrNull { it.receivedSats }

            when {
                incoming == null -> Unit
                incoming.isConfirmed -> {
                    flow.value =
                        ChargeStatus.Confirmed(
                            txid = Txid(incoming.txid),
                            blockHeight = incoming.blockHeight,
                        )
                    AppLogger.info(TAG, "Charge $chargeId confirmed at block ${incoming.blockHeight}")
                    monitors.remove(chargeId)
                    return
                }
                else -> {
                    flow.value = ChargeStatus.Detected(Txid(incoming.txid))
                }
            }

            delay(pollIntervalMs)
        }
    }

    /** Cancel all background monitors. Intended for tests / shutdown hooks. */
    internal fun shutdown() {
        monitors.values.forEach { it.cancel() }
        monitors.clear()
    }

    private companion object {
        const val TAG = "PaymentOrchestrator"
        const val DEFAULT_POLL_INTERVAL_MS = 15_000L
    }
}
