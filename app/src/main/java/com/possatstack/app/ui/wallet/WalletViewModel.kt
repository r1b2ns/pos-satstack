package com.possatstack.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.WalletBackup
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel
    @Inject
    constructor(
        private val engine: OnChainWalletEngine,
    ) : ViewModel() {
        data class State(
            val isLoading: Boolean = false,
            /**
             * Flips to `true` once [initWallet] has finished probing the
             * engine, regardless of whether a wallet was found. Consumers
             * (notably the navigation start-destination decision) wait for
             * this to flip before reading [hasWallet] so they don't briefly
             * route the user through onboarding while the engine loads.
             */
            val isInitialized: Boolean = false,
            val hasWallet: Boolean = false,
            val receiveAddress: String? = null,
            val errorMessage: String? = null,
            val balanceSats: Long? = null,
            val syncProgress: SyncProgress = SyncProgress.Idle,
            val transactions: List<WalletTransaction> = emptyList(),
            val network: WalletNetwork? = null,
            /** Populated by [loadMnemonic]; cleared by [clearMnemonicFromState]. */
            val mnemonic: String? = null,
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state.asStateFlow()

        init {
            initWallet()
        }

        private fun initWallet() {
            viewModelScope.launch {
                if (!engine.hasWallet()) {
                    _state.update { it.copy(isInitialized = true, hasWallet = false) }
                    return@launch
                }
                _state.update { it.copy(isLoading = true) }
                runCatching { engine.loadWallet() }
                    .onSuccess {
                        val network = runCatching { engine.getNetwork() }.getOrNull()
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isInitialized = true,
                                hasWallet = true,
                                network = network,
                            )
                        }
                        syncWallet()
                    }
                    .onFailure { exception ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isInitialized = true,
                                errorMessage = exception.message,
                            )
                        }
                    }
            }
        }

        fun createWallet() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                runCatching { engine.createWallet(WalletNetwork.SIGNET) }
                    .onSuccess {
                        val network = runCatching { engine.getNetwork() }.getOrNull()
                        _state.update {
                            it.copy(isLoading = false, hasWallet = true, network = network)
                        }
                        syncWallet()
                    }
                    .onFailure { exception ->
                        _state.update { it.copy(isLoading = false, errorMessage = exception.message) }
                    }
            }
        }

        fun deleteWallet() {
            viewModelScope.launch {
                runCatching { engine.deleteWallet() }
                _state.value = State(isInitialized = true, hasWallet = false)
            }
        }

        fun importWallet(mnemonic: String) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val mnemonicChars = mnemonic.toCharArray()
                try {
                    engine.importWallet(WalletBackup.Bip39(mnemonicChars, WalletNetwork.SIGNET))
                    val network = runCatching { engine.getNetwork() }.getOrNull()
                    _state.update {
                        it.copy(isLoading = false, hasWallet = true, network = network)
                    }
                    syncWallet()
                } catch (exception: WalletError) {
                    _state.update { it.copy(isLoading = false, errorMessage = exception.message) }
                } catch (exception: Exception) {
                    _state.update { it.copy(isLoading = false, errorMessage = exception.message) }
                } finally {
                    mnemonicChars.fill('\u0000')
                }
            }
        }

        fun loadReceiveAddress() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, receiveAddress = null) }
                runCatching { engine.getNewReceiveAddress() }
                    .onSuccess { address ->
                        _state.update { it.copy(isLoading = false, receiveAddress = address.value) }
                    }
                    .onFailure { exception ->
                        _state.update { it.copy(isLoading = false, errorMessage = exception.message) }
                    }
            }
        }

        fun syncWallet() {
            viewModelScope.launch {
                if (!engine.hasWallet()) return@launch

                _state.update { it.copy(syncProgress = SyncProgress.Syncing(0)) }

                var progressPercent = 0
                val progressJob: Job =
                    viewModelScope.launch {
                        while (progressPercent < 90) {
                            delay(700)
                            progressPercent = minOf(progressPercent + 15, 90)
                            _state.update { it.copy(syncProgress = SyncProgress.Syncing(progressPercent)) }
                        }
                    }

                runCatching {
                    engine.sync { progress ->
                        _state.update { it.copy(syncProgress = progress) }
                    }
                }
                    .also { progressJob.cancel() }
                    .onSuccess {
                        _state.update { it.copy(syncProgress = SyncProgress.Syncing(100)) }
                        loadBalanceAfterSync()
                    }
                    .onFailure { exception ->
                        _state.update {
                            it.copy(syncProgress = SyncProgress.Idle, errorMessage = exception.message)
                        }
                    }
            }
        }

        fun loadTransactions() {
            viewModelScope.launch {
                runCatching { engine.getTransactions() }
                    .onSuccess { transactions ->
                        _state.update { it.copy(transactions = transactions) }
                    }
            }
        }

        /**
         * Read the mnemonic from [com.possatstack.app.wallet.signer.SignerSecretStore]
         * and publish it into [State.mnemonic]. Call [clearMnemonicFromState] when
         * the seed-phrase screen is dismissed so the plaintext doesn't linger in
         * memory.
         */
        fun loadMnemonic() {
            viewModelScope.launch {
                runCatching { engine.exportBackup() }
                    .onSuccess { backup ->
                        val bip39 = backup as? WalletBackup.Bip39 ?: return@launch
                        val mnemonicString = String(bip39.mnemonic)
                        bip39.mnemonic.fill('\u0000')
                        _state.update { it.copy(mnemonic = mnemonicString) }
                    }
                    .onFailure { exception ->
                        _state.update { it.copy(errorMessage = exception.message) }
                    }
            }
        }

        fun clearMnemonicFromState() {
            _state.update { it.copy(mnemonic = null) }
        }

        private suspend fun loadBalanceAfterSync() {
            val balance = runCatching { engine.getBalance() }.getOrNull()
            val transactions = runCatching { engine.getTransactions() }.getOrElse { emptyList() }
            _state.update {
                it.copy(
                    syncProgress = SyncProgress.Idle,
                    balanceSats = balance?.totalSats,
                    transactions = transactions,
                )
            }
        }

        fun clearError() {
            _state.update { it.copy(errorMessage = null) }
        }
    }
