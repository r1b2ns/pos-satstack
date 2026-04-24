package com.possatstack.app.wallet.payment

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.FakeOnChainWalletEngine
import com.possatstack.app.wallet.WalletError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPaymentOrchestratorTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val engine = FakeOnChainWalletEngine()

    // pollIntervalMs set to Long.MAX_VALUE so the monitor's delay suspends
    // effectively forever — tests focus on the observable API, not the loop.
    private val orchestrator =
        DefaultPaymentOrchestrator(
            engine = engine,
            scope = testScope,
            pollIntervalMs = Long.MAX_VALUE,
        )

    @After
    fun tearDown() {
        orchestrator.shutdown()
    }

    @Test
    fun `availableMethods returns empty when the engine has no wallet`() =
        runTest {
            engine.hasWalletValue = false
            assertEquals(emptyList<PaymentMethod>(), orchestrator.availableMethods())
        }

    @Test
    fun `availableMethods returns OnChain when the engine has a wallet`() =
        runTest {
            engine.hasWalletValue = true
            assertEquals(listOf(PaymentMethod.OnChain), orchestrator.availableMethods())
        }

    @Test
    fun `createCharge OnChain throws NoWallet when the engine has no wallet`() =
        runTest {
            engine.hasWalletValue = false
            try {
                orchestrator.createCharge(PaymentMethod.OnChain, 1_000L)
                fail("Expected WalletError.NoWallet")
            } catch (error: WalletError.NoWallet) {
                assertTrue(error is WalletError)
            }
        }

    @Test
    fun `createCharge OnChain returns a charge with a BIP21 payload`() =
        runTest {
            engine.hasWalletValue = true
            engine.receiveAddressResult = Result.success(BitcoinAddress("bc1qalpha"))

            val charge = orchestrator.createCharge(PaymentMethod.OnChain, 2_500L, memo = "Coffee")

            assertEquals(PaymentMethod.OnChain, charge.method)
            assertEquals(2_500L, charge.amountSats)
            assertEquals("Coffee", charge.memo)
            val payload = charge.payload as ChargePayload.OnChainAddress
            assertEquals("bc1qalpha", payload.address.value)
            assertTrue(payload.bip21Uri.startsWith("bitcoin:bc1qalpha"))
            assertTrue(payload.bip21Uri.contains("amount="))
            assertTrue(payload.bip21Uri.contains("label=Coffee"))
        }

    @Test
    fun `createCharge Lightning fails until Fase 5`() =
        runTest {
            engine.hasWalletValue = true
            try {
                orchestrator.createCharge(PaymentMethod.Lightning, 1_000L)
                fail("Expected WalletError.Unknown")
            } catch (error: WalletError.Unknown) {
                assertTrue(error.cause is UnsupportedOperationException)
            }
        }

    @Test
    fun `createCharge BearerCard fails until Fase 5`() =
        runTest {
            engine.hasWalletValue = true
            try {
                orchestrator.createCharge(PaymentMethod.BearerCard("card-1"), 1_000L)
                fail("Expected WalletError.Unknown")
            } catch (error: WalletError.Unknown) {
                assertTrue(error.cause is UnsupportedOperationException)
            }
        }

    @Test
    fun `chargeStatus emits Pending for a freshly-created charge`() =
        runTest {
            engine.hasWalletValue = true
            val charge = orchestrator.createCharge(PaymentMethod.OnChain, 1_000L)
            val status = orchestrator.chargeStatus(charge.id).first()
            assertEquals(ChargeStatus.Pending, status)
        }

    @Test
    fun `chargeStatus for unknown id returns an empty flow`() =
        runTest {
            val collected = mutableListOf<ChargeStatus>()
            orchestrator.chargeStatus("does-not-exist").collect { collected += it }
            assertTrue(collected.isEmpty())
        }

    @Test
    fun `cancelCharge transitions the status to Cancelled`() =
        runTest {
            engine.hasWalletValue = true
            val charge = orchestrator.createCharge(PaymentMethod.OnChain, 1_000L)
            orchestrator.cancelCharge(charge.id)
            val status = orchestrator.chargeStatus(charge.id).first()
            assertEquals(ChargeStatus.Cancelled, status)
        }

    @Test
    fun `cancelCharge on unknown id is a no-op`() =
        runTest {
            orchestrator.cancelCharge("unknown")
            assertTrue(orchestrator.getCharge("unknown") == null)
        }

    @Test
    fun `getCharge returns the stored charge`() =
        runTest {
            engine.hasWalletValue = true
            val charge = orchestrator.createCharge(PaymentMethod.OnChain, 1_000L)
            assertSame(charge, orchestrator.getCharge(charge.id))
        }

    @Test
    fun `getCharge returns null for unknown id`() {
        assertNull(orchestrator.getCharge("unknown"))
    }

    @Test
    fun `createCharge with non-positive amount is rejected`() =
        runTest {
            engine.hasWalletValue = true
            try {
                orchestrator.createCharge(PaymentMethod.OnChain, 0L)
                fail("Expected IllegalArgumentException")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message!!.contains("> 0"))
            }
        }
}
