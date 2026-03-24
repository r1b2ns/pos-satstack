package com.possatstack.app.wallet.bitcoin

import android.content.Context
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import javax.inject.Inject

/**
 * BDK 2.x implementation of [WalletRepository].
 *
 * Wallet state is persisted to a SQLite file in [Context.noBackupFilesDir], which
 * is excluded from Android cloud backups to avoid leaking key material.
 * The [Persister] reference is retained alongside the [Wallet] so that state
 * changes (e.g. new revealed addresses) are flushed to disk after each operation.
 *
 * This class must not be referenced anywhere outside the DI wiring layer —
 * all other code must depend on [WalletRepository] only.
 */
class BdkWalletRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WalletRepository {

    private var wallet: Wallet? = null
    private var persister: Persister? = null

    /** SQLite file path. Using noBackupFilesDir prevents cloud backup of key material. */
    private val dbPath: String
        get() = File(context.noBackupFilesDir, DB_FILE_NAME).absolutePath

    override suspend fun createWallet(network: WalletNetwork): WalletDescriptor =
        withContext(Dispatchers.IO) {
            // BDK throws CreateWithPersistException$DataAlreadyExists if the SQLite file
            // already contains wallet data. Delete it first so a fresh wallet can be created.
            wallet = null
            persister = null
            File(dbPath).delete()

            val bdkNetwork = network.toBdkNetwork()
            val mnemonic = Mnemonic(WordCount.WORDS12)
            val rootKey = DescriptorSecretKey(bdkNetwork, mnemonic, null)

            val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
            val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

            val descriptor = WalletDescriptor(
                externalDescriptor = externalDescriptor.toStringWithSecret(),
                internalDescriptor = internalDescriptor.toStringWithSecret(),
                network = network,
                mnemonic = mnemonic.toString(),
            )

            val p = Persister.newSqlite(dbPath)
            wallet = Wallet(
                descriptor = Descriptor(descriptor.externalDescriptor, bdkNetwork),
                changeDescriptor = Descriptor(descriptor.internalDescriptor, bdkNetwork),
                network = bdkNetwork,
                persister = p,
            )
            persister = p
            descriptor
        }

    override suspend fun loadWallet(descriptor: WalletDescriptor): Unit =
        withContext(Dispatchers.IO) {
            val bdkNetwork = descriptor.network.toBdkNetwork()
            val p = Persister.newSqlite(dbPath)
            wallet = Wallet.load(
                descriptor = Descriptor(descriptor.externalDescriptor, bdkNetwork),
                changeDescriptor = Descriptor(descriptor.internalDescriptor, bdkNetwork),
                persister = p,
            )
            persister = p
        }

    override suspend fun getNewReceiveAddress(): BitcoinAddress =
        withContext(Dispatchers.IO) {
            val w = requireNotNull(wallet) {
                "Wallet not initialised. Call createWallet or loadWallet first."
            }
            val p = requireNotNull(persister) {
                "Persister not initialised. Call createWallet or loadWallet first."
            }
            val addressInfo = w.revealNextAddress(KeychainKind.EXTERNAL)
            // Flush the revealed address index change to SQLite so the
            // same address is not returned on the next app session.
            w.persist(p)
            BitcoinAddress(addressInfo.address.toString())
        }

    private fun WalletNetwork.toBdkNetwork(): Network =
        when (this) {
            WalletNetwork.MAINNET -> Network.BITCOIN
            WalletNetwork.TESTNET -> Network.TESTNET
            WalletNetwork.SIGNET -> Network.SIGNET
            WalletNetwork.REGTEST -> Network.REGTEST
        }

    private companion object {
        const val DB_FILE_NAME = "bdk_wallet.sqlite3"
    }
}
