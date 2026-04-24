package com.possatstack.app.ui.charge

import com.possatstack.app.MainDispatcherRule
import com.possatstack.app.wallet.payment.FakePaymentOrchestrator
import com.possatstack.app.wallet.payment.PaymentMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newViewModel(orchestrator: FakePaymentOrchestrator = FakePaymentOrchestrator()): ChargeViewModel =
        ChargeViewModel(orchestrator)

    @Test
    fun `appendDigit replaces the initial zero`() {
        val viewModel = newViewModel()
        viewModel.appendDigit(5)
        assertEquals("5", viewModel.state.value.amountInput)
    }

    @Test
    fun `appendDigit appends after the first digit`() {
        val viewModel = newViewModel()
        viewModel.appendDigit(5)
        viewModel.appendDigit(0)
        viewModel.appendDigit(0)
        assertEquals("500", viewModel.state.value.amountInput)
    }

    @Test
    fun `appendDigit rejects values above one BTC`() {
        val viewModel = newViewModel()
        // Type 100 000 001 (> 100 000 000 = 1 BTC cap).
        "100000001".forEach { char ->
            viewModel.appendDigit(char.digitToInt())
        }
        assertEquals("10000000", viewModel.state.value.amountInput)
    }

    @Test
    fun `backspace drops the last digit and stops at zero`() {
        val viewModel = newViewModel()
        viewModel.appendDigit(1)
        viewModel.appendDigit(2)
        viewModel.appendDigit(3)
        viewModel.backspace()
        assertEquals("12", viewModel.state.value.amountInput)
        viewModel.backspace()
        viewModel.backspace()
        assertEquals("0", viewModel.state.value.amountInput)
        viewModel.backspace()
        assertEquals("0", viewModel.state.value.amountInput)
    }

    @Test
    fun `clearAmount resets the keypad`() {
        val viewModel = newViewModel()
        viewModel.appendDigit(4)
        viewModel.appendDigit(2)
        viewModel.clearAmount()
        assertEquals("0", viewModel.state.value.amountInput)
    }

    @Test
    fun `createOnChainCharge delegates to the orchestrator and resets amount`() =
        runTest {
            val orchestrator = FakePaymentOrchestrator()
            val viewModel = newViewModel(orchestrator)
            viewModel.appendDigit(1)
            viewModel.appendDigit(0)
            viewModel.appendDigit(0)

            var createdId: String? = null
            viewModel.createOnChainCharge(onCreated = { createdId = it })
            advanceUntilIdle()

            assertEquals(1, orchestrator.createCount)
            val (method, amount, memo) = orchestrator.createRequests.single()
            assertEquals(PaymentMethod.OnChain, method)
            assertEquals(100L, amount)
            assertNull(memo)
            assertNotNull(createdId)
            assertEquals("0", viewModel.state.value.amountInput)
            assertTrue(!viewModel.state.value.isCreating)
        }

    @Test
    fun `createOnChainCharge does nothing when the amount is zero`() =
        runTest {
            val orchestrator = FakePaymentOrchestrator()
            val viewModel = newViewModel(orchestrator)
            var called = false
            viewModel.createOnChainCharge(onCreated = { called = true })
            advanceUntilIdle()

            assertEquals(0, orchestrator.createCount)
            assertTrue(!called)
        }

    @Test
    fun `createOnChainCharge surfaces orchestrator failures as errorMessage`() =
        runTest {
            val orchestrator =
                FakePaymentOrchestrator().apply {
                    createChargeResult = Result.failure(RuntimeException("no wallet"))
                }
            val viewModel = newViewModel(orchestrator)
            viewModel.appendDigit(5)

            viewModel.createOnChainCharge(onCreated = { error("should not be called") })
            advanceUntilIdle()

            assertEquals("no wallet", viewModel.state.value.errorMessage)
            assertTrue(!viewModel.state.value.isCreating)
        }

    @Test
    fun `clearError removes the error`() =
        runTest {
            val orchestrator =
                FakePaymentOrchestrator().apply {
                    createChargeResult = Result.failure(RuntimeException("boom"))
                }
            val viewModel = newViewModel(orchestrator)
            viewModel.appendDigit(5)
            viewModel.createOnChainCharge(onCreated = {})
            advanceUntilIdle()
            assertEquals("boom", viewModel.state.value.errorMessage)

            viewModel.clearError()
            assertNull(viewModel.state.value.errorMessage)
        }
}
