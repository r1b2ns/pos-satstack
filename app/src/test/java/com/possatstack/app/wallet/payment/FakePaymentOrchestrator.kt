package com.possatstack.app.wallet.payment

import com.possatstack.app.wallet.BitcoinAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FakePaymentOrchestrator : PaymentOrchestrator {
    var availableMethodsResult: List<PaymentMethod> = listOf(PaymentMethod.OnChain)
    var createChargeResult: Result<Charge>? = null

    private val charges = mutableMapOf<String, Charge>()
    private val statuses = mutableMapOf<String, MutableStateFlow<ChargeStatus>>()

    var availableMethodsCount = 0
    var createCount = 0
    var cancelCount = 0
    val createRequests = mutableListOf<Triple<PaymentMethod, Long, String?>>()
    val cancelledIds = mutableListOf<String>()

    override suspend fun availableMethods(): List<PaymentMethod> {
        availableMethodsCount++
        return availableMethodsResult
    }

    override suspend fun createCharge(
        method: PaymentMethod,
        amountSats: Long,
        memo: String?,
    ): Charge {
        createCount++
        createRequests += Triple(method, amountSats, memo)
        createChargeResult?.let { return it.getOrThrow() }
        val id = UUID.randomUUID().toString()
        val charge =
            Charge(
                id = id,
                method = method,
                amountSats = amountSats,
                memo = memo,
                payload =
                    ChargePayload.OnChainAddress(
                        address = BitcoinAddress("bc1qfake"),
                        bip21Uri = "bitcoin:bc1qfake?amount=$amountSats",
                    ),
                createdAtEpochMs = 0L,
            )
        charges[id] = charge
        statuses[id] = MutableStateFlow(ChargeStatus.Pending)
        return charge
    }

    override fun chargeStatus(chargeId: String): Flow<ChargeStatus> =
        statuses[chargeId]?.asStateFlow() ?: kotlinx.coroutines.flow.emptyFlow()

    override suspend fun cancelCharge(chargeId: String) {
        cancelCount++
        cancelledIds += chargeId
        statuses[chargeId]?.value = ChargeStatus.Cancelled
    }

    override fun getCharge(chargeId: String): Charge? = charges[chargeId]

    fun emitStatus(
        chargeId: String,
        status: ChargeStatus,
    ) {
        statuses[chargeId]?.value = status
    }
}
