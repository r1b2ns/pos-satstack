package com.possatstack.app.wallet.storage

import com.possatstack.app.wallet.WalletDescriptor

class FakeWalletStorage : WalletStorage {
    private var stored: WalletDescriptor? = null
    private var fullScanDone: Boolean = false
    private var chainBackend: String? = null

    var saveCount = 0
    var clearCount = 0
    var markFullScanDoneCount = 0
    var markFullScanUndoneCount = 0
    var markChainBackendCount = 0

    override fun save(descriptor: WalletDescriptor) {
        saveCount++
        stored = descriptor
    }

    override fun load(): WalletDescriptor? = stored

    override fun clear() {
        clearCount++
        stored = null
        fullScanDone = false
        chainBackend = null
    }

    override fun markFullScanDone() {
        markFullScanDoneCount++
        fullScanDone = true
    }

    override fun markFullScanUndone() {
        markFullScanUndoneCount++
        fullScanDone = false
    }

    override fun isFullScanDone(): Boolean = fullScanDone

    override fun storedChainBackend(): String? = chainBackend

    override fun markChainBackend(backendId: String) {
        markChainBackendCount++
        chainBackend = backendId
    }

    fun preload(descriptor: WalletDescriptor) {
        stored = descriptor
    }

    fun preloadFullScanDone() {
        fullScanDone = true
    }

    fun preloadChainBackend(backendId: String) {
        chainBackend = backendId
    }
}
