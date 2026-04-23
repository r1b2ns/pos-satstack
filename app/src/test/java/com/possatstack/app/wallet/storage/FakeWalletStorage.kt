package com.possatstack.app.wallet.storage

import com.possatstack.app.wallet.WalletDescriptor

class FakeWalletStorage : WalletStorage {

    private var stored: WalletDescriptor? = null
    private var fullScanDone: Boolean = false

    var saveCount = 0
    var clearCount = 0
    var markFullScanDoneCount = 0

    override fun save(descriptor: WalletDescriptor) {
        saveCount++
        stored = descriptor
    }

    override fun load(): WalletDescriptor? = stored

    override fun clear() {
        clearCount++
        stored = null
        fullScanDone = false
    }

    override fun markFullScanDone() {
        markFullScanDoneCount++
        fullScanDone = true
    }

    override fun isFullScanDone(): Boolean = fullScanDone

    fun preload(descriptor: WalletDescriptor) {
        stored = descriptor
    }

    fun preloadFullScanDone() {
        fullScanDone = true
    }
}
