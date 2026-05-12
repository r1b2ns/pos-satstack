package com.possatstack.app.wallet.payment

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.FeePolicy
import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.PsbtRecipient
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.WalletNetwork
import com.possatstack.app.wallet.signer.tapsigner.IsoDepCkTransport
import com.possatstack.app.wallet.signer.tapsigner.NfcSessionLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bitcoindevkit.DescriptorPublicKey
import org.bitcoindevkit.cktap.CkTapCard
import org.bitcoindevkit.cktap.toCktap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the end-to-end "tap-to-pay" flow for a charge:
 *
 *  1. Asks for the TAPSIGNER CVC (via [AwaitingCvc] → caller submits via [submitCvc]).
 *  2. Listens for an NFC tap ([AwaitingTap]) using [NfcSessionLauncher].
 *  3. Talks to the card via rust-cktap ([IsoDepCkTransport] → [toCktap]).
 *  4. Reads the customer's BIP-84 account xpub + master fingerprint.
 *  5. Asks [OnChainWalletEngine.buildPsbtFromExternalSigner] for an
 *     unsigned PSBT that spends from the TAPSIGNER's deposit address
 *     into the merchant's charge address.
 *  6. Hands the PSBT to `TapSigner.signPsbt`. Card returns the signed PSBT.
 *  7. Broadcasts via [OnChainWalletEngine.broadcast].
 *
 * Single-session at a time — guarded by [mutex] and an active-job check.
 *
 * This is experimental scaffolding for ChargeDetailsScreen — it sidesteps
 * the in-house [com.possatstack.app.wallet.signer.TapsignerNfcSigner]
 * because rust-cktap already implements the full TAPSIGNER protocol.
 */
@Singleton
internal class TapToPayController
    @Inject
    constructor(
        private val nfcLauncher: NfcSessionLauncher,
        private val engine: OnChainWalletEngine,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _state = MutableStateFlow<TapToPayState>(TapToPayState.Idle)
        val state: StateFlow<TapToPayState> = _state.asStateFlow()

        private val mutex = Mutex()

        @Volatile
        private var pendingCvc: CompletableDeferred<String>? = null

        @Volatile
        private var activeJob: Job? = null

        /**
         * Kick off a tap-to-pay session. Idempotent: a no-op if a session
         * is already running (a `Failed`/`Success` state must be cleared
         * via [reset] before a new session can start).
         */
        fun start(
            chargeAddress: BitcoinAddress,
            chargeAmountSats: Long,
            network: WalletNetwork,
        ) {
            if (activeJob?.isActive == true) {
                AppLogger.warning(TAG, "start() while session is already active; ignoring")
                return
            }
            activeJob = scope.launch { run(chargeAddress, chargeAmountSats, network) }
        }

        /** Feed the CVC typed by the user back to the active session. */
        fun submitCvc(cvc: String) {
            val pending = pendingCvc
            if (pending == null) {
                AppLogger.warning(TAG, "submitCvc with no pending session")
                return
            }
            pending.complete(cvc)
        }

        /** Abort an in-flight session and return to [TapToPayState.Idle]. */
        fun cancel() {
            pendingCvc?.completeExceptionally(CancellationException("User cancelled"))
            nfcLauncher.cancel()
            activeJob?.cancel()
            activeJob = null
            _state.value = TapToPayState.Idle
        }

        /** Clear a terminal state ([Success]/[Failed]) so the UI returns to idle. */
        fun reset() {
            if (activeJob?.isActive == true) return
            _state.value = TapToPayState.Idle
        }

        private suspend fun run(
            chargeAddress: BitcoinAddress,
            chargeAmountSats: Long,
            network: WalletNetwork,
        ) = mutex.withLock {
            try {
                val cvc = awaitCvc()
                _state.value = TapToPayState.AwaitingTap
                val tag = nfcLauncher.awaitTag()

                _state.value = TapToPayState.Working(TapToPayState.Step.ReadingCard)
                val transport = IsoDepCkTransport(tag)
                try {
                    val card = toCktap(transport)
                    val tapsigner =
                        (card as? CkTapCard.TapSigner)?.v1
                            ?: error("Detected card is not a TAPSIGNER")

                    val coinType: UInt = if (network == WalletNetwork.MAINNET) 0u else 1u
                    val accountPath =
                        listOf(
                            HARDENED_BIT or 84u,
                            HARDENED_BIT or coinType,
                            HARDENED_BIT or 0u,
                        )
                    tapsigner.derive(accountPath, cvc)
                    val accountXpub = tapsigner.xpub(master = false, cvc = cvc)
                    val masterXpubStr = tapsigner.xpub(master = true, cvc = cvc)
                    val masterFingerprint =
                        DescriptorPublicKey.fromString(masterXpubStr)
                            .use { it.masterFingerprint() }

                    _state.value = TapToPayState.Working(TapToPayState.Step.BuildingPsbt)
                    val unsigned =
                        engine.buildPsbtFromExternalSigner(
                            accountXpub = accountXpub,
                            masterFingerprint = masterFingerprint,
                            accountDerivationPath = "84'/$coinType'/0'",
                            network = network,
                            recipient = PsbtRecipient(chargeAddress, chargeAmountSats),
                            feePolicy = FeePolicy.TargetBlocks(EXTERNAL_FEE_TARGET_BLOCKS),
                        )

                    _state.value = TapToPayState.Working(TapToPayState.Step.Signing)
                    val signedBase64 = tapsigner.signPsbt(unsigned.base64, cvc)

                    _state.value = TapToPayState.Working(TapToPayState.Step.Broadcasting)
                    val txid = engine.broadcast(SignedPsbt(signedBase64))

                    _state.value = TapToPayState.Success(txid.hex)
                } finally {
                    transport.close()
                }
            } catch (cancellation: CancellationException) {
                _state.value = TapToPayState.Idle
                throw cancellation
            } catch (exception: Exception) {
                AppLogger.error(TAG, "Tap-to-pay failed", exception)
                _state.value = TapToPayState.Failed(exception.message ?: exception::class.simpleName.orEmpty())
            } finally {
                activeJob = null
            }
        }

        private suspend fun awaitCvc(): String {
            val deferred = CompletableDeferred<String>()
            pendingCvc = deferred
            _state.value = TapToPayState.AwaitingCvc
            return try {
                deferred.await()
            } finally {
                pendingCvc = null
            }
        }

        private companion object {
            const val TAG = "TapToPayController"
            const val HARDENED_BIT: UInt = 0x80000000u
            const val EXTERNAL_FEE_TARGET_BLOCKS = 6
        }
    }
