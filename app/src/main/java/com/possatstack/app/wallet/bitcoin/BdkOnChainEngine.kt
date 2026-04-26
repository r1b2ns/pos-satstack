package com.possatstack.app.wallet.bitcoin

import android.content.Context
import com.possatstack.app.BuildConfig
import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.Balance
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.FeeEstimate
import com.possatstack.app.wallet.FeePolicy
import com.possatstack.app.wallet.FeeTarget
import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.PsbtRecipient
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.SyncProgress
import com.possatstack.app.wallet.Txid
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletBackup
import com.possatstack.app.wallet.WalletDescriptor
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.WalletTransaction
import com.possatstack.app.wallet.chain.ChainDataSource
import com.possatstack.app.wallet.chain.ChainSyncProvider
import com.possatstack.app.wallet.signer.SignerSecretStore
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Address
import org.bitcoindevkit.Amount
import org.bitcoindevkit.BumpFeeTxBuilder
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet
import org.bitcoindevkit.WordCount
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.bitcoindevkit.Txid as BdkTxid

/**
 * BDK 2.x implementation of [OnChainWalletEngine].
 *
 * Responsibilities:
 *  - Owns the in-memory BDK [Wallet] + [Persister] with a [Mutex] serialising
 *    every mutation so concurrent callers never collide inside the Rust FFI.
 *  - Persists secret descriptors in [WalletStorage] and the mnemonic in
 *    [SignerSecretStore]. The two are deliberately decoupled so the mnemonic
 *    storage can tighten its policy (StrongBox, biometric gate) without
 *    touching the descriptor path.
 *  - Delegates broadcast and fee-estimation to the injected [ChainDataSource]
 *    so swapping Esplora → Kyoto → Floresta later only changes that binding.
 *  - Delegates the full-scan / incremental-sync pipeline to the injected
 *    [ChainSyncProvider]. The provider owns whichever BDK client (Esplora
 *    HTTP, CBF light client, …) the active backend needs; this engine
 *    only forwards the wallet handle and the `fullScan` decision.
 *  - Translates every `org.bitcoindevkit.*` exception into a [WalletError]
 *    before re-throwing (see [toWalletError]).
 *
 * SQLite lives in `noBackupFilesDir/bdk/bdk_wallet.sqlite3`. Segregating under
 * `bdk/` keeps future LDK Node state (`noBackupFilesDir/ldk/`) isolated —
 * the wipe-on-backend-swap logic only touches the `bdk/` subdirectory.
 */
@Singleton
class BdkOnChainEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val walletStorage: WalletStorage,
        private val signerStore: SignerSecretStore,
        private val chainDataSource: ChainDataSource,
        private val chainSyncProvider: ChainSyncProvider,
    ) : OnChainWalletEngine {
        private val mutex = Mutex()
        private var wallet: Wallet? = null
        private var persister: Persister? = null

        private val dbDir: File
            get() = File(context.noBackupFilesDir, "bdk").apply { if (!exists()) mkdirs() }

        private val dbPath: String
            get() = File(dbDir, DB_FILE_NAME).absolutePath

        /**
         * Pre-Fase-1 installs stored the SQLite directly under `noBackupFilesDir/`.
         * Delete it on first touch so the new path (`bdk/bdk_wallet.sqlite3`) is
         * the only source of truth. The wallet's descriptor/mnemonic are in prefs
         * and survive; users just re-sync once.
         */
        private fun migrateLegacyDbPath() {
            val legacy = File(context.noBackupFilesDir, DB_FILE_NAME)
            if (legacy.exists()) {
                AppLogger.info(TAG, "Deleting legacy SQLite at ${legacy.absolutePath}")
                legacy.delete()
                walletStorage.markFullScanUndone()
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        //  Lifecycle
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun hasWallet(): Boolean =
            withContext(Dispatchers.IO) {
                walletStorage.load() != null
            }

        override suspend fun loadWallet(): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    if (wallet != null) return@withLock
                    migrateLegacyDbPath()
                    val descriptor = walletStorage.load() ?: throw WalletError.NoWallet
                    AppLogger.info(TAG, "Loading wallet from persistence (network=${descriptor.network})")
                    try {
                        val bdkNetwork = descriptor.network.toBdkNetwork()
                        val persisterInstance = Persister.newSqlite(dbPath)
                        wallet =
                            Wallet.load(
                                descriptor = Descriptor(descriptor.externalDescriptor, bdkNetwork),
                                changeDescriptor = Descriptor(descriptor.internalDescriptor, bdkNetwork),
                                persister = persisterInstance,
                            )
                        persister = persisterInstance
                        chainDataSource.configureFor(descriptor.network)
                        AppLogger.info(TAG, "Wallet loaded")
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        override suspend fun createWallet(network: WalletNetwork): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    AppLogger.info(TAG, "Creating new wallet (network=$network)")
                    migrateLegacyDbPath()
                    resetDatabaseAndState()

                    try {
                        val bdkNetwork = network.toBdkNetwork()
                        val mnemonic = Mnemonic(WordCount.WORDS12)
                        val rootKey = DescriptorSecretKey(bdkNetwork, mnemonic, null)

                        val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
                        val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

                        val descriptorRecord =
                            WalletDescriptor(
                                externalDescriptor = externalDescriptor.toStringWithSecret(),
                                internalDescriptor = internalDescriptor.toStringWithSecret(),
                                network = network,
                            )

                        val mnemonicChars = mnemonic.toString().toCharArray()
                        try {
                            AppLogger.info(TAG, "Persisting mnemonic to signer secret store")
                            signerStore.saveMnemonic(mnemonicChars, network)
                            AppLogger.info(
                                TAG,
                                "Mnemonic persisted; security posture=${signerStore.securityPosture()}",
                            )
                        } catch (mnemonicException: Exception) {
                            AppLogger.error(TAG, "saveMnemonic failed during createWallet", mnemonicException)
                            throw mnemonicException
                        } finally {
                            mnemonicChars.fill('\u0000')
                        }

                        walletStorage.save(descriptorRecord)
                        walletStorage.markChainBackend(BuildConfig.CHAIN_BACKEND)

                        val persisterInstance = Persister.newSqlite(dbPath)
                        wallet =
                            Wallet(
                                descriptor = Descriptor(descriptorRecord.externalDescriptor, bdkNetwork),
                                changeDescriptor = Descriptor(descriptorRecord.internalDescriptor, bdkNetwork),
                                network = bdkNetwork,
                                persister = persisterInstance,
                            )
                        persister = persisterInstance
                        chainDataSource.configureFor(network)
                        AppLogger.info(TAG, "Wallet created")
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        override suspend fun importWallet(backup: WalletBackup): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    AppLogger.info(
                        TAG,
                        "Importing wallet (kind=${backup::class.simpleName}, network=${backup.network})",
                    )
                    migrateLegacyDbPath()
                    val bip39 =
                        backup as? WalletBackup.Bip39
                            ?: throw WalletError.Unknown(
                                IllegalArgumentException("Backup kind ${backup::class.simpleName} not supported yet"),
                            )

                    resetDatabaseAndState()

                    try {
                        val bdkNetwork = bip39.network.toBdkNetwork()
                        val mnemonicString = String(bip39.mnemonic)
                        val parsedMnemonic =
                            try {
                                Mnemonic.fromString(mnemonicString)
                            } finally {
                                // We built a String which lingers in the String pool; we
                                // cannot wipe it. Caller is responsible for zeroing the
                                // original CharArray they passed in.
                                @Suppress("UNUSED_EXPRESSION")
                                mnemonicString
                            }
                        val rootKey = DescriptorSecretKey(bdkNetwork, parsedMnemonic, null)

                        val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
                        val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

                        val descriptorRecord =
                            WalletDescriptor(
                                externalDescriptor = externalDescriptor.toStringWithSecret(),
                                internalDescriptor = internalDescriptor.toStringWithSecret(),
                                network = bip39.network,
                            )

                        signerStore.saveMnemonic(bip39.mnemonic, bip39.network)
                        walletStorage.save(descriptorRecord)
                        walletStorage.markChainBackend(BuildConfig.CHAIN_BACKEND)

                        val persisterInstance = Persister.newSqlite(dbPath)
                        wallet =
                            Wallet(
                                descriptor = Descriptor(descriptorRecord.externalDescriptor, bdkNetwork),
                                changeDescriptor = Descriptor(descriptorRecord.internalDescriptor, bdkNetwork),
                                network = bdkNetwork,
                                persister = persisterInstance,
                            )
                        persister = persisterInstance
                        chainDataSource.configureFor(bip39.network)
                        AppLogger.info(TAG, "Wallet imported")
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        override suspend fun deleteWallet(): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    AppLogger.info(TAG, "Deleting wallet")
                    resetDatabaseAndState()
                    walletStorage.clear()
                    signerStore.wipe()
                }
            }

        override suspend fun exportBackup(): WalletBackup =
            withContext(Dispatchers.IO) {
                val descriptor = walletStorage.load() ?: throw WalletError.NoWallet
                val mnemonicChars = signerStore.readMnemonic()
                WalletBackup.Bip39(mnemonicChars, descriptor.network)
            }

        // ─────────────────────────────────────────────────────────────────────
        //  Receiving
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun getNewReceiveAddress(): BitcoinAddress =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    val activePersister =
                        requireNotNull(persister) {
                            "Persister not initialised"
                        }
                    try {
                        val addressInfo = activeWallet.revealNextAddress(KeychainKind.EXTERNAL)
                        activeWallet.persist(activePersister)
                        BitcoinAddress(addressInfo.address.toString())
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        // ─────────────────────────────────────────────────────────────────────
        //  PSBT build / sign / broadcast / bump
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun createUnsignedPsbt(
            recipients: List<PsbtRecipient>,
            feePolicy: FeePolicy,
        ): UnsignedPsbt =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    val network = requireNotNull(walletStorage.load()?.network) { "Network unknown" }.toBdkNetwork()
                    try {
                        var builder = TxBuilder()
                        for (recipient in recipients) {
                            val bdkAddress = Address(recipient.address.value, network)
                            val amount = Amount.fromSat(recipient.amountSats.toULong())
                            builder = builder.addRecipient(bdkAddress.scriptPubkey(), amount)
                        }
                        builder = builder.applyFee(feePolicy)
                        val psbt = builder.finish(activeWallet)
                        UnsignedPsbt(base64 = psbt.serialize(), fingerprint = walletFingerprint())
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        override suspend fun broadcast(signed: SignedPsbt): Txid =
            withContext(Dispatchers.IO) {
                try {
                    val bdkPsbt = Psbt(signed.base64)
                    val rawBytes = bdkPsbt.extractTx().serialize()
                    chainDataSource.broadcastRaw(rawBytes)
                } catch (exception: Exception) {
                    throw exception.toWalletError()
                }
            }

        override suspend fun bumpFee(
            txid: Txid,
            newPolicy: FeePolicy,
        ): UnsignedPsbt =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    try {
                        val bdkTxid = BdkTxid.fromString(txid.hex)
                        val rate =
                            when (newPolicy) {
                                is FeePolicy.SatsPerVb -> FeeRate.fromSatPerVb(newPolicy.rate.toUInt().toULong())
                                is FeePolicy.TargetBlocks -> {
                                    val estimate = chainDataSource.estimateFees(FeeTarget(newPolicy.blocks))
                                    FeeRate.fromSatPerVb(estimate.satsPerVb.toUInt().toULong())
                                }
                                is FeePolicy.Absolute ->
                                    throw WalletError.CannotBumpFee(
                                        "Absolute fee not supported by bumpFee — use SatsPerVb",
                                    )
                            }
                        val builder = BumpFeeTxBuilder(bdkTxid, rate)
                        val psbt = builder.finish(activeWallet)
                        UnsignedPsbt(base64 = psbt.serialize(), fingerprint = walletFingerprint())
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        // ─────────────────────────────────────────────────────────────────────
        //  Fees
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun estimateFees(target: FeeTarget): FeeEstimate = chainDataSource.estimateFees(target)

        // ─────────────────────────────────────────────────────────────────────
        //  State
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun getBalance(): Balance =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    val balance = activeWallet.balance()
                    Balance(
                        confirmedSats = balance.confirmed.toSat().toLong(),
                        trustedPendingSats = balance.trustedPending.toSat().toLong(),
                        untrustedPendingSats = balance.untrustedPending.toSat().toLong(),
                    )
                }
            }

        override suspend fun getTransactions(): List<WalletTransaction> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    try {
                        activeWallet.transactions().map { canonicalTx ->
                            val transaction = canonicalTx.transaction
                            val amounts = activeWallet.sentAndReceived(transaction)
                            val chainPosition = canonicalTx.chainPosition
                            val isConfirmed = chainPosition is ChainPosition.Confirmed
                            val confirmationTime =
                                (chainPosition as? ChainPosition.Confirmed)
                                    ?.confirmationBlockTime?.confirmationTime?.toLong()
                            val blockHeight =
                                (chainPosition as? ChainPosition.Confirmed)
                                    ?.confirmationBlockTime?.blockId?.height?.toLong()
                            WalletTransaction(
                                txid = transaction.computeTxid().toString(),
                                sentSats = amounts.sent.toSat().toLong(),
                                receivedSats = amounts.received.toSat().toLong(),
                                feeSats = null,
                                confirmationTime = confirmationTime,
                                blockHeight = blockHeight,
                                isConfirmed = isConfirmed,
                            )
                        }.sortedWith(
                            compareBy<WalletTransaction> { it.isConfirmed }
                                .thenByDescending { it.confirmationTime ?: Long.MAX_VALUE },
                        )
                    } catch (exception: Exception) {
                        throw exception.toWalletError()
                    }
                }
            }

        override suspend fun getNetwork(): WalletNetwork? = walletStorage.load()?.network

        // ─────────────────────────────────────────────────────────────────────
        //  Sync (delegated to ChainSyncProvider so the chosen backend —
        //  Esplora, Kyoto, Floresta — owns the actual scan logic)
        // ─────────────────────────────────────────────────────────────────────

        override suspend fun sync(onProgress: (SyncProgress) -> Unit): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val activeWallet = requireLoaded()
                    val activePersister = requireNotNull(persister) { "Persister not initialised" }
                    val network = requireNotNull(walletStorage.load()?.network) { "Network unknown" }

                    val isFullScan =
                        !walletStorage.isFullScanDone() ||
                            walletStorage.storedChainBackend() != BuildConfig.CHAIN_BACKEND

                    AppLogger.info(
                        TAG,
                        "Sync start (fullScan=$isFullScan, network=$network, backend=${BuildConfig.CHAIN_BACKEND})",
                    )

                    try {
                        chainSyncProvider.sync(
                            wallet = activeWallet,
                            network = network,
                            fullScan = isFullScan,
                            onProgress = onProgress,
                        )
                        activeWallet.persist(activePersister)
                        walletStorage.markFullScanDone()
                        walletStorage.markChainBackend(BuildConfig.CHAIN_BACKEND)
                        AppLogger.info(TAG, "Sync complete")
                    } catch (exception: Exception) {
                        onProgress(SyncProgress.Idle)
                        throw exception.toWalletError()
                    }
                }
            }

        // ─────────────────────────────────────────────────────────────────────
        //  Helpers
        // ─────────────────────────────────────────────────────────────────────

        private fun requireLoaded(): Wallet = wallet ?: throw WalletError.NoWallet

        /**
         * Stable identifier used as [UnsignedPsbt.fingerprint]. The external
         * descriptor starts with `[fingerprint/path]...` — we extract the
         * fingerprint bracket if present, otherwise fall back to a truncated hash
         * of the descriptor string. Good enough for Signer sanity checks.
         */
        private fun walletFingerprint(): String {
            val descriptor = walletStorage.load()?.externalDescriptor.orEmpty()
            val bracket =
                descriptor.substringAfter('[', missingDelimiterValue = "")
                    .substringBefore('/', missingDelimiterValue = "")
            return bracket.ifEmpty { descriptor.hashCode().toString(16) }
        }

        private fun TxBuilder.applyFee(feePolicy: FeePolicy): TxBuilder =
            when (feePolicy) {
                is FeePolicy.SatsPerVb -> feeRate(FeeRate.fromSatPerVb(feePolicy.rate.toUInt().toULong()))
                is FeePolicy.TargetBlocks -> {
                    // chainDataSource.estimateFees is a suspend function; we are inside
                    // a Dispatchers.IO coroutine and call it via runBlocking to stay
                    // compatible with the builder's synchronous API. The call is
                    // small (single HTTP GET) and already isolated in this worker.
                    val estimate =
                        kotlinx.coroutines.runBlocking {
                            chainDataSource.estimateFees(FeeTarget(feePolicy.blocks))
                        }
                    feeRate(FeeRate.fromSatPerVb(estimate.satsPerVb.toUInt().toULong()))
                }
                is FeePolicy.Absolute -> feeAbsolute(Amount.fromSat(feePolicy.totalSats.toULong()))
            }

        private fun resetDatabaseAndState() {
            wallet = null
            persister = null
            File(dbPath).delete()
        }

        private fun WalletNetwork.toBdkNetwork(): Network =
            when (this) {
                WalletNetwork.MAINNET -> Network.BITCOIN
                WalletNetwork.TESTNET -> Network.TESTNET
                WalletNetwork.SIGNET -> Network.SIGNET
                WalletNetwork.REGTEST -> Network.REGTEST
            }

        private companion object {
            const val TAG = "BdkOnChainEngine"
            const val DB_FILE_NAME = "bdk_wallet.sqlite3"
        }
    }
