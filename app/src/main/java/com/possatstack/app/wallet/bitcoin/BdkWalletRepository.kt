package com.possatstack.app.wallet.bitcoin

import android.content.Context
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletRepository
import com.possatstack.app.wallet.WalletTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Bip39Exception
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.ElectrumClient
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
 * A [Mutex] serialises all wallet operations so that concurrent callers
 * (e.g. [WalletSyncService] and [WalletViewModel]) never access the underlying
 * BDK wallet object simultaneously.
 *
 * This class must not be referenced anywhere outside the DI wiring layer —
 * all other code must depend on [WalletRepository] only.
 */
class BdkWalletRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WalletRepository {

    private val mutex = Mutex()
    private var wallet: Wallet? = null
    private var persister: Persister? = null

    /** SQLite file path. Using noBackupFilesDir prevents cloud backup of key material. */
    private val dbPath: String
        get() = File(context.noBackupFilesDir, DB_FILE_NAME).absolutePath

    override suspend fun createWallet(network: WalletNetwork): WalletDescriptor =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                AppLogger.info(TAG, "Creating new wallet (network=$network)")
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
                AppLogger.info(TAG, "Wallet created successfully")
                descriptor
            }
        }

    /**
     * @throws Bip39Exception if [mnemonic] contains invalid or unknown words.
     */
    override suspend fun importWallet(mnemonic: String, network: WalletNetwork): WalletDescriptor =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                AppLogger.info(TAG, "Importing wallet (network=$network)")
                // Wipe existing SQLite file so the new wallet can be persisted cleanly.
                wallet = null
                persister = null
                File(dbPath).delete()

                val bdkNetwork = network.toBdkNetwork()
                val parsedMnemonic = Mnemonic.fromString(mnemonic) // throws Bip39Exception if invalid
                val rootKey = DescriptorSecretKey(bdkNetwork, parsedMnemonic, null)

                val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
                val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

                val descriptor = WalletDescriptor(
                    externalDescriptor = externalDescriptor.toStringWithSecret(),
                    internalDescriptor = internalDescriptor.toStringWithSecret(),
                    network = network,
                    mnemonic = mnemonic,
                )

                val p = Persister.newSqlite(dbPath)
                wallet = Wallet(
                    descriptor = Descriptor(descriptor.externalDescriptor, bdkNetwork),
                    changeDescriptor = Descriptor(descriptor.internalDescriptor, bdkNetwork),
                    network = bdkNetwork,
                    persister = p,
                )
                persister = p
                AppLogger.info(TAG, "Wallet imported successfully")
                descriptor
            }
        }

    override suspend fun loadWallet(descriptor: WalletDescriptor): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (wallet != null) {
                    AppLogger.info(TAG, "Wallet already loaded, skipping")
                    return@withLock
                }
                AppLogger.info(TAG, "Loading wallet from SQLite (network=${descriptor.network})")
                val bdkNetwork = descriptor.network.toBdkNetwork()
                val p = Persister.newSqlite(dbPath)
                wallet = Wallet.load(
                    descriptor = Descriptor(descriptor.externalDescriptor, bdkNetwork),
                    changeDescriptor = Descriptor(descriptor.internalDescriptor, bdkNetwork),
                    persister = p,
                )
                persister = p
                AppLogger.info(TAG, "Wallet loaded successfully")
            }
        }

    override suspend fun getNewReceiveAddress(): BitcoinAddress =
        withContext(Dispatchers.IO) {
            mutex.withLock {
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
        }

    override suspend fun syncWallet(network: WalletNetwork, isFullScan: Boolean): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = requireNotNull(wallet) {
                    "Wallet not initialised. Call createWallet or loadWallet first."
                }
                val p = requireNotNull(persister) {
                    "Persister not initialised. Call createWallet or loadWallet first."
                }

                val url = network.toElectrumUrl()
                    ?: error("No Electrum server configured for network $network")

                AppLogger.info(TAG, "Connecting to Electrum server: $url")
                val client = ElectrumClient(url)
                AppLogger.info(TAG, "Electrum connection established")

                try {
                    val update = if (isFullScan) {
                        AppLogger.info(TAG, "Starting full scan (stopGap=$STOP_GAP, batchSize=$BATCH_SIZE)")
                        val request = w.startFullScan().build()
                        val result = client.fullScan(request, STOP_GAP, BATCH_SIZE, false)
                        AppLogger.info(TAG, "Full scan complete")
                        result
                    } else {
                        AppLogger.info(TAG, "Starting incremental sync (batchSize=$BATCH_SIZE)")
                        val request = w.startSyncWithRevealedSpks().build()
                        val result = client.sync(request, BATCH_SIZE, false)
                        AppLogger.info(TAG, "Incremental sync complete")
                        result
                    }

                    AppLogger.info(TAG, "Applying update to wallet")
                    w.applyUpdate(update)
                    w.persist(p)

                    val balance = w.balance()
                    AppLogger.info(
                        TAG,
                        "Balance after sync — confirmed: ${balance.confirmed.toSat()} sat, " +
                            "trusted pending: ${balance.trustedPending.toSat()} sat, " +
                            "total: ${balance.total.toSat()} sat",
                    )
                } catch (exception: Exception) {
                    AppLogger.error(TAG, "Sync failed: ${exception.message}", exception)
                    throw exception
                } finally {
                    // ElectrumClient is a Rust-backed object; let the finalizer close the TCP
                    // connection when the reference is released.
                    @Suppress("UnusedExpression")
                    client
                }
            }
        }

    override suspend fun getBalance(): Long =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = requireNotNull(wallet) {
                    "Wallet not initialised. Call createWallet or loadWallet first."
                }
                w.balance().total.toSat().toLong()
            }
        }

    override suspend fun getTransactions(): List<WalletTransaction> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val w = requireNotNull(wallet) {
                    "Wallet not initialised. Call createWallet or loadWallet first."
                }
                w.transactions().map { canonicalTx ->
                    val transaction = canonicalTx.transaction
                    val amounts = w.sentAndReceived(transaction)
                    val chainPosition = canonicalTx.chainPosition

                    val isConfirmed = chainPosition is ChainPosition.Confirmed
                    val confirmationTime = (chainPosition as? ChainPosition.Confirmed)
                        ?.confirmationBlockTime?.confirmationTime?.toLong()
                    val blockHeight = (chainPosition as? ChainPosition.Confirmed)
                        ?.confirmationBlockTime?.blockId?.height?.toLong()

                    WalletTransaction(
                        txid = transaction.computeTxid().toString(),
                        sentSats = amounts.sent.toSat().toLong(),
                        receivedSats = amounts.received.toSat().toLong(),
                        feeSats = null, // fee not available from sentAndReceived
                        confirmationTime = confirmationTime,
                        blockHeight = blockHeight,
                        isConfirmed = isConfirmed,
                    )
                }.sortedWith(
                    compareBy<WalletTransaction> { it.isConfirmed }
                        .thenByDescending { it.confirmationTime ?: Long.MAX_VALUE },
                )
            }
        }

    private fun WalletNetwork.toBdkNetwork(): Network =
        when (this) {
            WalletNetwork.MAINNET -> Network.BITCOIN
            WalletNetwork.TESTNET -> Network.TESTNET
            WalletNetwork.SIGNET -> Network.SIGNET
            WalletNetwork.REGTEST -> Network.REGTEST
        }

    /** Returns the Electrum SSL URL for the given network, or null if not supported. */
    private fun WalletNetwork.toElectrumUrl(): String? =
        when (this) {
            WalletNetwork.SIGNET -> "ssl://mempool.space:60602"
            WalletNetwork.MAINNET -> "ssl://electrum.blockstream.info:50002"
            else -> null
        }

    private companion object {
        const val TAG = "BdkWalletRepository"
        const val DB_FILE_NAME = "bdk_wallet.sqlite3"

        /** Gap limit: number of consecutive unused addresses before stopping the scan. */
        const val STOP_GAP = 20UL

        /** Number of script public keys per Electrum batch request. */
        const val BATCH_SIZE = 10UL
    }
}
