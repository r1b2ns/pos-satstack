package com.possatstack.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletRepository
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.hilt.android.lifecycle.HiltViewModel
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
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        initWallet()
    }

    /** Loads the wallet from storage into memory if one exists. */
    private fun initWallet() {
        viewModelScope.launch {
            val descriptor = walletStorage.load() ?: run {
                _state.update { it.copy(hasWallet = false) }
                return@launch
            }
            _state.update { it.copy(isLoading = true) }
            runCatching { walletRepository.loadWallet(descriptor) }
                .onSuccess { _state.update { it.copy(isLoading = false, hasWallet = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.message) } }
        }
    }

    fun createWallet() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { walletRepository.createWallet(WalletNetwork.SIGNET) }
                .onSuccess { descriptor ->
                    walletStorage.save(descriptor)
                    _state.update { it.copy(isLoading = false, hasWallet = true) }
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
                    _state.update { it.copy(isLoading = false, hasWallet = true) }
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

    fun getMnemonic(): String? = walletStorage.load()?.mnemonic

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
