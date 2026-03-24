package com.possatstack.app.wallet.bitcoin

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Wallet
import org.bitcoindevkit.WordCount

/**
 * BDK 2.x implementation of [WalletRepository].
 *
 * The wallet is kept in-memory only. Persistence of the [WalletDescriptor]
 * (and therefore the ability to recover funds) is the responsibility of the caller.
 *
 * This class must not be referenced anywhere outside the DI wiring layer —
 * all other code must depend on [WalletRepository] only.
 */
class BdkWalletRepository : WalletRepository {

    private var wallet: Wallet? = null

    override suspend fun createWallet(network: WalletNetwork): WalletDescriptor =
        withContext(Dispatchers.IO) {
            val bdkNetwork = network.toBdkNetwork()
            val mnemonic = Mnemonic(WordCount.WORDS12)
            val rootKey = DescriptorSecretKey(bdkNetwork, mnemonic, null)

            val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
            val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

            val descriptor = WalletDescriptor(
                externalDescriptor = externalDescriptor.toStringWithSecret(),
                internalDescriptor = internalDescriptor.toStringWithSecret(),
                network = network,
            )

            initWallet(descriptor)
            descriptor
        }

    override suspend fun loadWallet(descriptor: WalletDescriptor): Unit =
        withContext(Dispatchers.IO) {
            initWallet(descriptor)
        }

    override suspend fun getNewReceiveAddress(): BitcoinAddress =
        withContext(Dispatchers.IO) {
            val w = requireNotNull(wallet) {
                "Wallet not initialised. Call createWallet or loadWallet first."
            }
            val addressInfo = w.revealNextAddress(KeychainKind.EXTERNAL)
            BitcoinAddress(addressInfo.address.toString())
        }

    private fun initWallet(descriptor: WalletDescriptor) {
        val bdkNetwork = descriptor.network.toBdkNetwork()
        val persister = Persister.newInMemory()
        val external = Descriptor(descriptor.externalDescriptor, bdkNetwork)
        val internal = Descriptor(descriptor.internalDescriptor, bdkNetwork)
        wallet = Wallet(external, internal, bdkNetwork, persister)
    }

    private fun WalletNetwork.toBdkNetwork(): Network =
        when (this) {
            WalletNetwork.MAINNET -> Network.BITCOIN
            WalletNetwork.TESTNET -> Network.TESTNET
            WalletNetwork.SIGNET -> Network.SIGNET
            WalletNetwork.REGTEST -> Network.REGTEST
        }
}
