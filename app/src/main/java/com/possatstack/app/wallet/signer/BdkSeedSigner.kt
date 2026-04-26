package com.possatstack.app.wallet.signer

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.bitcoin.toWalletError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Wallet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Signer] backed by the mnemonic in [SignerSecretStore] and BDK's built-in
 * PSBT signing.
 *
 * Each call reads the mnemonic, reconstructs a fresh in-memory BDK [Wallet]
 * from the BIP-84 descriptors, signs and finalises the PSBT, and lets the
 * wallet fall out of scope so the Rust objects can be GC'd. The mnemonic
 * char array is zeroed before return.
 *
 * The engine ([com.possatstack.app.wallet.bitcoin.BdkOnChainEngine]) no longer
 * touches the mnemonic — separation required for future hardware signers
 * (TAPSIGNER, airgap QR) to slot in as alternative [Signer] implementations.
 */
@Singleton
class BdkSeedSigner
    @Inject
    constructor(
        private val signerStore: SignerSecretStore,
    ) : Signer {
        override val id: String = "seed"
        override val kind: SignerKind = SignerKind.SOFTWARE_SEED

        private val mutex = Mutex()

        override suspend fun signPsbt(
            psbt: UnsignedPsbt,
            context: SigningContext,
        ): SignedPsbt =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    AppLogger.info(
                        TAG,
                        "Signing PSBT (recipients=${context.recipients.size}, total=${context.totalSendSats} sat)",
                    )

                    val mnemonicChars = signerStore.readMnemonic()
                    try {
                        buildWalletAndSign(mnemonicChars, context.network, psbt)
                    } finally {
                        mnemonicChars.fill('\u0000')
                    }
                }
            }

        private fun buildWalletAndSign(
            mnemonicChars: CharArray,
            network: WalletNetwork,
            psbt: UnsignedPsbt,
        ): SignedPsbt {
            val bdkNetwork = network.toBdkNetwork()
            val mnemonic: Mnemonic
            try {
                mnemonic = Mnemonic.fromString(String(mnemonicChars))
            } catch (exception: Exception) {
                throw exception.toWalletError()
            }

            val rootKey = DescriptorSecretKey(bdkNetwork, mnemonic, null)
            val externalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.EXTERNAL, bdkNetwork)
            val internalDescriptor = Descriptor.newBip84(rootKey, KeychainKind.INTERNAL, bdkNetwork)

            val ephemeralPersister = Persister.newInMemory()
            val wallet =
                Wallet(
                    descriptor = externalDescriptor,
                    changeDescriptor = internalDescriptor,
                    network = bdkNetwork,
                    persister = ephemeralPersister,
                )

            try {
                val bdkPsbt = Psbt(psbt.base64)
                val signed = wallet.sign(bdkPsbt, null)
                if (!signed) throw WalletError.SigningFailed("No inputs signed")
                val finalised = wallet.finalizePsbt(bdkPsbt, null)
                if (!finalised) throw WalletError.SigningFailed("PSBT not fully finalised")
                return SignedPsbt(base64 = bdkPsbt.serialize())
            } catch (exception: Exception) {
                throw exception.toWalletError()
            }
        }

        private fun WalletNetwork.toBdkNetwork(): Network =
            when (this) {
                WalletNetwork.MAINNET -> Network.BITCOIN
                WalletNetwork.TESTNET -> Network.TESTNET
                WalletNetwork.SIGNET -> Network.SIGNET
                WalletNetwork.REGTEST -> Network.REGTEST
            }

        private companion object {
            const val TAG = "BdkSeedSigner"
        }
    }
