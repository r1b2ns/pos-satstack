package com.possatstack.app.ui.wallet

import com.possatstack.app.MainDispatcherRule
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.FakeWalletRepository
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletTransaction
import com.possatstack.app.wallet.storage.FakeWalletStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sampleDescriptor = WalletDescriptor(
        externalDescriptor = "external",
        internalDescriptor = "internal",
        network = WalletNetwork.SIGNET,
        mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
    )

    private fun newViewModel(
        repository: FakeWalletRepository = FakeWalletRepository(),
        storage: FakeWalletStorage = FakeWalletStorage(),
    ): WalletViewModel = WalletViewModel(repository, storage)

    @Test
    fun `init with no stored wallet sets hasWallet to false`() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasWallet)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init with stored wallet loads it and triggers a full scan sync`() = runTest {
        val repository = FakeWalletRepository().apply {
            balanceResult = Result.success(12345L)
        }
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }

        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasWallet)
        assertFalse(state.isLoading)
        assertEquals(WalletNetwork.SIGNET, state.network)
        assertEquals(1, repository.loadCount)
        assertEquals(listOf(WalletNetwork.SIGNET to true), repository.syncCallsArgs)
        assertEquals(1, storage.markFullScanDoneCount)
        assertEquals(12345L, state.balanceSats)
        assertEquals(SyncProgress.Idle, state.syncProgress)
    }

    @Test
    fun `init with stored wallet and full scan already done runs incremental sync`() = runTest {
        val repository = FakeWalletRepository()
        val storage = FakeWalletStorage().apply {
            preload(sampleDescriptor)
            preloadFullScanDone()
        }

        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        assertEquals(listOf(WalletNetwork.SIGNET to false), repository.syncCallsArgs)
        // full scan flag should not be set again
        assertEquals(0, storage.markFullScanDoneCount)
        assertEquals(SyncProgress.Idle, viewModel.state.value.syncProgress)
    }

    @Test
    fun `init surfaces error when loadWallet fails`() = runTest {
        val repository = FakeWalletRepository().apply {
            loadWalletResult = Result.failure(RuntimeException("boom"))
        }
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }

        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasWallet)
        assertFalse(state.isLoading)
        assertEquals("boom", state.errorMessage)
        assertTrue(repository.syncCallsArgs.isEmpty())
    }

    @Test
    fun `createWallet persists descriptor and triggers sync on success`() = runTest {
        val repository = FakeWalletRepository()
        val storage = FakeWalletStorage()
        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        viewModel.createWallet()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, repository.createCount)
        assertEquals(1, storage.saveCount)
        assertTrue(state.hasWallet)
        assertEquals(WalletNetwork.SIGNET, state.network)
        assertEquals(listOf(WalletNetwork.SIGNET to true), repository.syncCallsArgs)
    }

    @Test
    fun `createWallet surfaces error on failure`() = runTest {
        val repository = FakeWalletRepository().apply {
            createWalletResult = Result.failure(IllegalStateException("nope"))
        }
        val storage = FakeWalletStorage()
        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        viewModel.createWallet()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasWallet)
        assertEquals("nope", state.errorMessage)
        assertEquals(0, storage.saveCount)
    }

    @Test
    fun `importWallet persists descriptor and triggers sync on success`() = runTest {
        val repository = FakeWalletRepository()
        val storage = FakeWalletStorage()
        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        viewModel.importWallet("mnemonic words here")
        advanceUntilIdle()

        assertEquals(1, repository.importCount)
        assertEquals(1, storage.saveCount)
        assertTrue(viewModel.state.value.hasWallet)
        assertEquals(listOf(WalletNetwork.SIGNET to true), repository.syncCallsArgs)
    }

    @Test
    fun `importWallet surfaces error on failure`() = runTest {
        val repository = FakeWalletRepository().apply {
            importWalletResult = Result.failure(IllegalArgumentException("bad seed"))
        }
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.importWallet("invalid")
        advanceUntilIdle()

        assertEquals("bad seed", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.hasWallet)
    }

    @Test
    fun `deleteWallet clears storage and resets state`() = runTest {
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }
        val viewModel = newViewModel(storage = storage)
        advanceUntilIdle()

        viewModel.deleteWallet()

        assertEquals(1, storage.clearCount)
        assertFalse(viewModel.state.value.hasWallet)
        assertNull(viewModel.state.value.balanceSats)
    }

    @Test
    fun `loadReceiveAddress success updates state with address`() = runTest {
        val repository = FakeWalletRepository().apply {
            receiveAddressResult = Result.success(BitcoinAddress("bc1qabcd"))
        }
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.loadReceiveAddress()
        advanceUntilIdle()

        assertEquals("bc1qabcd", viewModel.state.value.receiveAddress)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `loadReceiveAddress failure surfaces error`() = runTest {
        val repository = FakeWalletRepository().apply {
            receiveAddressResult = Result.failure(RuntimeException("offline"))
        }
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.loadReceiveAddress()
        advanceUntilIdle()

        assertEquals("offline", viewModel.state.value.errorMessage)
        assertNull(viewModel.state.value.receiveAddress)
    }

    @Test
    fun `syncWallet does nothing when no wallet is stored`() = runTest {
        val repository = FakeWalletRepository()
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.syncWallet()
        advanceUntilIdle()

        assertTrue(repository.syncCallsArgs.isEmpty())
    }

    @Test
    fun `syncWallet failure on full scan path sets error and idle`() = runTest {
        val repository = FakeWalletRepository().apply {
            syncResult = Result.failure(RuntimeException("electrum"))
        }
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }
        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("electrum", state.errorMessage)
        assertEquals(SyncProgress.Idle, state.syncProgress)
        assertEquals(0, storage.markFullScanDoneCount)
    }

    @Test
    fun `loadTransactions updates state on success`() = runTest {
        val tx = WalletTransaction(
            txid = "abc",
            sentSats = 0,
            receivedSats = 1000,
            feeSats = null,
            confirmationTime = null,
            blockHeight = null,
            isConfirmed = false,
        )
        val repository = FakeWalletRepository().apply {
            transactionsResult = Result.success(listOf(tx))
        }
        val viewModel = newViewModel(repository)
        advanceUntilIdle()

        viewModel.loadTransactions()
        advanceUntilIdle()

        assertEquals(listOf(tx), viewModel.state.value.transactions)
    }

    @Test
    fun `getMnemonic returns stored mnemonic when wallet exists`() {
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }
        val viewModel = newViewModel(storage = storage)

        assertEquals(sampleDescriptor.mnemonic, viewModel.getMnemonic())
    }

    @Test
    fun `getMnemonic returns null when no wallet stored`() {
        val viewModel = newViewModel()
        assertNull(viewModel.getMnemonic())
    }

    @Test
    fun `clearError removes the error message`() = runTest {
        val repository = FakeWalletRepository().apply {
            loadWalletResult = Result.failure(RuntimeException("boom"))
        }
        val storage = FakeWalletStorage().apply { preload(sampleDescriptor) }
        val viewModel = newViewModel(repository, storage)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
    }
}
