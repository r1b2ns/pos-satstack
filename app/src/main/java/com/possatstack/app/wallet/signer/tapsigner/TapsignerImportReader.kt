package com.possatstack.app.wallet.signer.tapsigner

import com.possatstack.app.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bitcoindevkit.DescriptorPublicKey
import org.bitcoindevkit.cktap.CardException
import org.bitcoindevkit.cktap.CkTapCard
import org.bitcoindevkit.cktap.CkTapException
import org.bitcoindevkit.cktap.toCktap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a TAPSIGNER's account-level xpub + master fingerprint over NFC.
 *
 * Mirrors [com.possatstack.app.wallet.signer.TapsignerNfcSigner]'s flow
 * but instead of producing a signed PSBT it produces a
 * [TapsignerWalletInfo] suitable for a watch-only wallet import.
 *
 * State machine: [TapsignerImportStep.AwaitingCvc] → AwaitingTap →
 * Exchanging → Done(info) or Failed(error).
 */
@Singleton
class TapsignerImportReader
    @Inject
    internal constructor(
        private val launcher: NfcSessionLauncher,
    ) {
        private val mutex = Mutex()

        private val stateFlow: MutableStateFlow<TapsignerImportStep> =
            MutableStateFlow(TapsignerImportStep.AwaitingCvc)
        val state: Flow<TapsignerImportStep> = stateFlow.asStateFlow()

        @Volatile
        private var pendingCvc: CompletableDeferred<Cvc>? = null

        fun submitCvc(cvc: Cvc) {
            val pending =
                pendingCvc
                    ?: run {
                        AppLogger.warning(TAG, "submitCvc with no session awaiting")
                        return
                    }
            pending.complete(cvc)
        }

        fun cancel() {
            pendingCvc?.completeExceptionally(TapsignerError.UserCancelled)
            launcher.cancel()
        }

        suspend fun fetchAccountXpub(): TapsignerWalletInfo =
            mutex.withLock {
                val cvcDeferred = CompletableDeferred<Cvc>()
                pendingCvc = cvcDeferred
                stateFlow.value = TapsignerImportStep.AwaitingCvc

                try {
                    val cvc =
                        try {
                            cvcDeferred.await()
                        } catch (cancel: TapsignerError.UserCancelled) {
                            throw cancel
                        } catch (exception: Exception) {
                            throw TapsignerError.UserCancelled
                        }

                    stateFlow.value = TapsignerImportStep.AwaitingTap
                    val transport = launcher.awaitTransport()

                    stateFlow.value = TapsignerImportStep.Exchanging
                    val info =
                        try {
                            readXpubInfo(transport, cvc)
                        } finally {
                            cvc.wipe()
                        }
                    stateFlow.value = TapsignerImportStep.Done(info)
                    info
                } catch (error: TapsignerError) {
                    stateFlow.value = TapsignerImportStep.Failed(error)
                    throw error
                } catch (exception: Exception) {
                    val wrapped = TapsignerError.ProtocolError("unexpected: ${exception.message}", exception)
                    stateFlow.value = TapsignerImportStep.Failed(wrapped)
                    throw wrapped
                } finally {
                    pendingCvc = null
                }
            }

        private suspend fun readXpubInfo(
            transport: IsoDepCkTransport,
            cvc: Cvc,
        ): TapsignerWalletInfo {
            var card: CkTapCard? = null
            try {
                card =
                    try {
                        toCktap(transport)
                    } catch (exception: CkTapException) {
                        throw exception.toImportError()
                    } catch (exception: CardException) {
                        throw exception.toImportError()
                    }

                val tapSigner =
                    (card as? CkTapCard.TapSigner)?.v1
                        ?: throw TapsignerError.NotATapsigner

                val cvcString = String(cvc.asBytes(), Charsets.US_ASCII)
                try {
                    val masterXpub =
                        try {
                            tapSigner.xpub(master = true, cvc = cvcString)
                        } catch (exception: CardException) {
                            throw exception.toImportError()
                        } catch (exception: CkTapException) {
                            throw exception.toImportError()
                        }

                    val accountXpub =
                        try {
                            tapSigner.xpub(master = false, cvc = cvcString)
                        } catch (exception: CardException) {
                            throw exception.toImportError()
                        } catch (exception: CkTapException) {
                            throw exception.toImportError()
                        }

                    val masterFingerprint =
                        try {
                            DescriptorPublicKey.fromString(masterXpub).masterFingerprint()
                        } catch (exception: Exception) {
                            throw TapsignerError.ProtocolError("Cannot derive fingerprint from master xpub", exception)
                        }

                    return TapsignerWalletInfo(
                        masterFingerprint = masterFingerprint,
                        accountXpub = accountXpub,
                    )
                } finally {
                    // Best-effort wipe of the temporary CVC string copy; the JVM
                    // string pool may still hold a reference, but the byte array
                    // we extracted does not.
                    cvcString.toByteArray(Charsets.US_ASCII).fill(0)
                }
            } finally {
                try {
                    card?.destroy()
                } catch (exception: Exception) {
                    AppLogger.warning(TAG, "card destroy raised: ${exception.message}")
                }
                try {
                    transport.close()
                } catch (exception: Exception) {
                    AppLogger.warning(TAG, "transport close raised: ${exception.message}")
                }
            }
        }

        private companion object {
            const val TAG = "TapsignerImportReader"
        }
    }

private fun CardException.toImportError(): TapsignerError =
    when (this) {
        is CardException.BadAuth -> TapsignerError.WrongCvc(attemptsLeft = null)
        is CardException.NeedsAuth -> TapsignerError.WrongCvc(attemptsLeft = null)
        is CardException.RateLimited -> TapsignerError.RateLimited(waitSeconds = 0)
        is CardException.InvalidState -> TapsignerError.NotSetUp
        else -> TapsignerError.ProtocolError(this::class.simpleName ?: "CardException", this)
    }

private fun CkTapException.toImportError(): TapsignerError =
    when (this) {
        is CkTapException.Transport -> TapsignerError.CardRemoved
        is CkTapException.UnknownCardType -> TapsignerError.NotATapsigner
        else -> TapsignerError.ProtocolError(this::class.simpleName ?: "CkTapException", this)
    }
