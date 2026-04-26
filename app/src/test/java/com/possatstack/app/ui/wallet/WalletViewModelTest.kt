package com.possatstack.app.ui.wallet

import com.possatstack.app.MainDispatcherRule
import com.possatstack.app.wallet.Balance
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.FakeOnChainWalletEngine
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletBackup
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletTransaction
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

    private fun newViewModel(engine: FakeOnChainWalletEngine = FakeOnChainWalletEngine()): WalletViewModel =
        WalletViewModel(engine)

    @Test
    fun `init with no stored wallet sets hasWallet to false`() =
        runTest {
            val viewModel = newViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.hasWallet)
            assertFalse(state.isLoading)
        }

    @Test
    fun `init with stored wallet loads it and triggers a sync`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    hasWalletValue = true
                    network = WalletNetwork.SIGNET
                    balanceResult = Result.success(Balance(12345L, 0, 0))
                }

            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasWallet)
            assertFalse(state.isLoading)
            assertEquals(WalletNetwork.SIGNET, state.network)
            assertEquals(1, engine.loadWalletCount)
            assertEquals(1, engine.syncCount)
            assertEquals(12345L, state.balanceSats)
            assertEquals(SyncProgress.Idle, state.syncProgress)
        }

    @Test
    fun `init surfaces error when loadWallet fails`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    hasWalletValue = true
                    loadWalletResult = Result.failure(RuntimeException("boom"))
                }

            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.hasWallet)
            assertFalse(state.isLoading)
            assertEquals("boom", state.errorMessage)
            assertEquals(0, engine.syncCount)
        }

    @Test
    fun `createWallet delegates to engine and triggers sync on success`() =
        runTest {
            val engine = FakeOnChainWalletEngine()
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.createWallet()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(1, engine.createWalletCount)
            assertEquals(listOf(WalletNetwork.SIGNET), engine.createWalletArgs)
            assertTrue(state.hasWallet)
            assertEquals(WalletNetwork.SIGNET, state.network)
            assertEquals(1, engine.syncCount)
        }

    @Test
    fun `createWallet surfaces error on failure`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    createWalletResult = Result.failure(IllegalStateException("nope"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.createWallet()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.hasWallet)
            assertEquals("nope", state.errorMessage)
            assertEquals(0, engine.syncCount)
        }

    @Test
    fun `importWallet wraps the mnemonic into a Bip39 backup and triggers sync`() =
        runTest {
            val engine = FakeOnChainWalletEngine()
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.importWallet(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            )
            advanceUntilIdle()

            assertEquals(1, engine.importWalletCount)
            val backup = engine.importWalletBackups.single() as WalletBackup.Bip39
            assertEquals(WalletNetwork.SIGNET, backup.network)
            assertTrue(viewModel.state.value.hasWallet)
            assertEquals(1, engine.syncCount)
        }

    @Test
    fun `importWallet surfaces error on failure`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    importWalletResult = Result.failure(IllegalArgumentException("bad seed"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.importWallet("invalid")
            advanceUntilIdle()

            assertEquals("bad seed", viewModel.state.value.errorMessage)
            assertFalse(viewModel.state.value.hasWallet)
        }

    @Test
    fun `deleteWallet calls engine and resets state`() =
        runTest {
            val engine = FakeOnChainWalletEngine().apply { hasWalletValue = true }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.deleteWallet()
            advanceUntilIdle()

            assertEquals(1, engine.deleteWalletCount)
            assertFalse(viewModel.state.value.hasWallet)
            assertNull(viewModel.state.value.balanceSats)
        }

    @Test
    fun `loadReceiveAddress success updates state with address`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    receiveAddressResult = Result.success(BitcoinAddress("bc1qabcd"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.loadReceiveAddress()
            advanceUntilIdle()

            assertEquals("bc1qabcd", viewModel.state.value.receiveAddress)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadReceiveAddress failure surfaces error`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    receiveAddressResult = Result.failure(RuntimeException("offline"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.loadReceiveAddress()
            advanceUntilIdle()

            assertEquals("offline", viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.receiveAddress)
        }

    @Test
    fun `syncWallet does nothing when no wallet exists`() =
        runTest {
            val engine = FakeOnChainWalletEngine()
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.syncWallet()
            advanceUntilIdle()

            // first invocation happens in init — no wallet means zero syncs
            assertEquals(0, engine.syncCount)
        }

    @Test
    fun `syncWallet failure sets error and idle`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    hasWalletValue = true
                    syncResult = Result.failure(RuntimeException("esplora"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("esplora", state.errorMessage)
            assertEquals(SyncProgress.Idle, state.syncProgress)
        }

    @Test
    fun `loadTransactions updates state on success`() =
        runTest {
            val tx =
                WalletTransaction(
                    txid = "abc",
                    sentSats = 0,
                    receivedSats = 1000,
                    feeSats = null,
                    confirmationTime = null,
                    blockHeight = null,
                    isConfirmed = false,
                )
            val engine =
                FakeOnChainWalletEngine().apply {
                    transactionsResult = Result.success(listOf(tx))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.loadTransactions()
            advanceUntilIdle()

            assertEquals(listOf(tx), viewModel.state.value.transactions)
        }

    @Test
    fun `loadMnemonic populates state mnemonic and zeroes the backup payload`() =
        runTest {
            val originalMnemonic = "foo bar baz"
            val backupChars = originalMnemonic.toCharArray()
            val engine =
                FakeOnChainWalletEngine().apply {
                    exportBackupResult = { WalletBackup.Bip39(backupChars, WalletNetwork.SIGNET) }
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.loadMnemonic()
            advanceUntilIdle()

            assertEquals(originalMnemonic, viewModel.state.value.mnemonic)
            assertTrue(
                "Backup CharArray should be zeroed after loadMnemonic",
                backupChars.all { it == '\u0000' },
            )
        }

    @Test
    fun `clearMnemonicFromState clears state mnemonic`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    exportBackupResult = {
                        WalletBackup.Bip39("x y z".toCharArray(), WalletNetwork.SIGNET)
                    }
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()

            viewModel.loadMnemonic()
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.mnemonic)

            viewModel.clearMnemonicFromState()
            assertNull(viewModel.state.value.mnemonic)
        }

    @Test
    fun `clearError removes the error message`() =
        runTest {
            val engine =
                FakeOnChainWalletEngine().apply {
                    hasWalletValue = true
                    loadWalletResult = Result.failure(RuntimeException("boom"))
                }
            val viewModel = newViewModel(engine)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.errorMessage)

            viewModel.clearError()
            assertNull(viewModel.state.value.errorMessage)
        }
}
