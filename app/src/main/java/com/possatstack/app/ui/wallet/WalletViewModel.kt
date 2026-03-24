package com.possatstack.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletRepository
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val walletStorage: WalletStorage,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = false,
        val hasWallet: Boolean = false,
        val receiveAddress: String? = null,
        val errorMessage: String? = null,
        val balanceSats: Long? = null,
        val syncProgress: SyncProgress = SyncProgress.Idle,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        initWallet()
    }

    /** Loads the wallet from storage into memory if one exists, then auto-syncs. */
    private fun initWallet() {
        viewModelScope.launch {
            val descriptor = walletStorage.load() ?: run {
                _state.update { it.copy(hasWallet = false) }
                return@launch
            }
            _state.update { it.copy(isLoading = true) }
            runCatching { walletRepository.loadWallet(descriptor) }
                .onSuccess {
                    _state.update { it.copy(isLoading = false, hasWallet = true) }
                    syncWallet()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    fun createWallet() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { walletRepository.createWallet(WalletNetwork.SIGNET) }
                .onSuccess { descriptor ->
                    walletStorage.save(descriptor)
                    // New wallet — no previous scan, mark as unseen so full scan runs
                    _state.update { it.copy(isLoading = false, hasWallet = true) }
                    syncWallet()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    fun deleteWallet() {
        walletStorage.clear()
        _state.update { State(hasWallet = false) }
    }

    fun importWallet(mnemonic: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { walletRepository.importWallet(mnemonic, WalletNetwork.SIGNET) }
                .onSuccess { descriptor ->
                    walletStorage.save(descriptor)
                    // Imported wallet — must do full scan to discover past transactions
                    _state.update { it.copy(isLoading = false, hasWallet = true) }
                    syncWallet()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    fun loadReceiveAddress() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, receiveAddress = null) }
            runCatching { walletRepository.getNewReceiveAddress() }
                .onSuccess { address ->
                    _state.update { it.copy(isLoading = false, receiveAddress = address.value) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
    }

    /**
     * Synchronises the wallet with the Electrum network.
     *
     * Determines automatically whether a full scan or incremental sync is needed
     * based on whether a full scan has been completed before for this wallet.
     *
     * The [State.syncProgress] field is updated throughout so the UI can show an
     * appropriate indicator. The indicator is removed (set to [SyncProgress.Idle])
     * only after the job finishes (successfully or with an error).
     */
    fun syncWallet() {
        val descriptor = walletStorage.load() ?: return
        viewModelScope.launch {
            val isFullScan = !walletStorage.isFullScanDone()
            runSync(descriptor.network, isFullScan)
        }
    }

    private suspend fun runSync(network: WalletNetwork, isFullScan: Boolean) {
        if (isFullScan) {
            runFullScan(network)
        } else {
            runIncrementalSync(network)
        }
    }

    private suspend fun runFullScan(network: WalletNetwork) {
        _state.update { it.copy(syncProgress = SyncProgress.FullScan) }
        runCatching { walletRepository.syncWallet(network, isFullScan = true) }
            .onSuccess {
                walletStorage.markFullScanDone()
                loadBalanceAfterSync()
            }
            .onFailure { e ->
                _state.update { it.copy(syncProgress = SyncProgress.Idle, errorMessage = e.message) }
            }
    }

    private suspend fun runIncrementalSync(network: WalletNetwork) {
        // Animate progress from 0 → 90 % while the blocking sync call runs,
        // then jump to 100 % when it completes.
        _state.update { it.copy(syncProgress = SyncProgress.Syncing(0)) }

        var progressPercent = 0
        val progressJob = viewModelScope.launch {
            while (progressPercent < 90) {
                delay(700)
                progressPercent = minOf(progressPercent + 15, 90)
                _state.update { it.copy(syncProgress = SyncProgress.Syncing(progressPercent)) }
            }
        }

        runCatching { walletRepository.syncWallet(network, isFullScan = false) }
            .also { progressJob.cancel() }
            .onSuccess {
                _state.update { it.copy(syncProgress = SyncProgress.Syncing(100)) }
                loadBalanceAfterSync()
            }
            .onFailure { e ->
                _state.update { it.copy(syncProgress = SyncProgress.Idle, errorMessage = e.message) }
            }
    }

    private suspend fun loadBalanceAfterSync() {
        val balance = runCatching { walletRepository.getBalance() }.getOrNull()
        _state.update { it.copy(syncProgress = SyncProgress.Idle, balanceSats = balance) }
    }

    fun getMnemonic(): String? = walletStorage.load()?.mnemonic

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
