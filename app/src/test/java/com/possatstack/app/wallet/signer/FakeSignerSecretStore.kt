package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork

class FakeSignerSecretStore : SignerSecretStore {
    private var mnemonic: CharArray? = null
    private var network: WalletNetwork? = null

    var saveCount = 0
    var readCount = 0
    var readSeedCount = 0
    var wipeCount = 0

    override fun hasMnemonic(): Boolean = mnemonic != null

    override suspend fun saveMnemonic(
        mnemonic: CharArray,
        network: WalletNetwork,
    ) {
        saveCount++
        this.mnemonic = mnemonic.copyOf()
        this.network = network
    }

    override suspend fun readMnemonic(): CharArray {
        readCount++
        return mnemonic?.copyOf() ?: throw WalletError.NoWallet
    }

    override suspend fun readSeedBytes(passphrase: CharArray): ByteArray {
        readSeedCount++
        return mnemonic?.joinToString("").orEmpty().toByteArray()
    }

    override fun storedNetwork(): WalletNetwork? = network

    override suspend fun wipe() {
        wipeCount++
        mnemonic?.fill(Char(0))
        mnemonic = null
        network = null
    }

    override fun securityPosture(): SecurityPosture =
        SecurityPosture(
            hardwareBacked = true,
            strongBoxBacked = false,
            deviceSecure = true,
        )

    fun preload(
        mnemonic: CharArray,
        network: WalletNetwork,
    ) {
        this.mnemonic = mnemonic.copyOf()
        this.network = network
    }
}
