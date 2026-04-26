package com.possatstack.app.wallet.signer

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletError
import com.possatstack.app.wallet.signer.tapsigner.Cvc
import com.possatstack.app.wallet.signer.tapsigner.NfcSessionLauncher
import com.possatstack.app.wallet.signer.tapsigner.TapsignerCrypto
import com.possatstack.app.wallet.signer.tapsigner.TapsignerError
import com.possatstack.app.wallet.signer.tapsigner.TapsignerSession
import com.possatstack.app.wallet.signer.tapsigner.TapsignerStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Signer] that signs PSBTs with a TAPSIGNER card over NFC.
 *
 * Because a TAPSIGNER session is interactive (the user must type the CVC
 * and tap the card), [signPsbt] publishes progress to [sessionState] for
 * the Compose dialog. The dialog in turn calls [submitCvc] to feed the
 * typed code back to the signer, or [cancelSession] to abort.
 *
 * Only one session is allowed at a time — [signPsbt] serialises behind
 * [mutex]. The UI is expected to drive the flow single-threaded.
 */
@Singleton
class TapsignerNfcSigner
    @Inject
    internal constructor(
        private val launcher: NfcSessionLauncher,
        private val crypto: TapsignerCrypto,
    ) : Signer {
        override val id: String = "tapsigner"
        override val kind: SignerKind = SignerKind.TAPSIGNER_NFC

        private val mutex = Mutex()

        private val sessionStateFlow: MutableStateFlow<TapsignerStep> =
            MutableStateFlow(TapsignerStep.AwaitingCvc)
        val sessionState: Flow<TapsignerStep> = sessionStateFlow.asStateFlow()

        @Volatile
        private var pendingCvc: CompletableDeferred<Cvc>? = null

        @Volatile
        private var currentSession: TapsignerSession? = null

        /** Feed the CVC typed by the user back to the active session. */
        fun submitCvc(cvc: Cvc) {
            val pending =
                pendingCvc
                    ?: run {
                        AppLogger.warning(TAG, "submitCvc with no session awaiting")
                        return
                    }
            pending.complete(cvc)
        }

        /** Cancel the active session (if any). Safe to call from the UI dismiss path. */
        fun cancelSession() {
            pendingCvc?.completeExceptionally(TapsignerError.UserCancelled)
            launcher.cancel()
        }

        override suspend fun signPsbt(
            psbt: UnsignedPsbt,
            context: SigningContext,
        ): SignedPsbt =
            mutex.withLock {
                val session = TapsignerSession(crypto)
                currentSession = session

                val cvcDeferred = CompletableDeferred<Cvc>()
                pendingCvc = cvcDeferred

                // Driver: mirror the session's state into our public flow so the
                // UI sees every transition even if it only subscribes to the
                // signer (not the internal session).
                session.advance(TapsignerStep.AwaitingCvc)
                sessionStateFlow.value = TapsignerStep.AwaitingCvc

                try {
                    val cvc =
                        try {
                            cvcDeferred.await()
                        } catch (cancel: TapsignerError.UserCancelled) {
                            throw cancel
                        } catch (exception: Exception) {
                            throw TapsignerError.UserCancelled
                        }

                    publish(session, TapsignerStep.AwaitingTap)
                    val client = launcher.awaitClient()

                    publish(session, TapsignerStep.Exchanging)
                    val signed = session.run(client, cvc, psbt, context)
                    cvc.wipe()

                    publish(session, TapsignerStep.Done(signed))
                    signed
                } catch (error: TapsignerError) {
                    publish(session, TapsignerStep.Failed(error))
                    throw error.toWalletError()
                } catch (exception: Exception) {
                    val wrapped = TapsignerError.ProtocolError("unexpected: ${exception.message}", exception)
                    publish(session, TapsignerStep.Failed(wrapped))
                    throw wrapped.toWalletError()
                } finally {
                    pendingCvc = null
                    currentSession = null
                }
            }

        private fun publish(
            session: TapsignerSession,
            step: TapsignerStep,
        ) {
            session.advance(step)
            sessionStateFlow.value = step
        }

        private fun TapsignerError.toWalletError(): WalletError =
            when (this) {
                is TapsignerError.UserCancelled ->
                    WalletError.SigningFailed("User cancelled TAPSIGNER session")
                is TapsignerError.WrongCvc ->
                    WalletError.SigningFailed("Wrong CVC")
                is TapsignerError.RateLimited ->
                    WalletError.SigningFailed("TAPSIGNER rate-limited (${waitSeconds}s)")
                is TapsignerError.CardRemoved ->
                    WalletError.SigningFailed("Card removed before signing completed")
                is TapsignerError.Timeout ->
                    WalletError.SigningFailed("TAPSIGNER session timed out")
                is TapsignerError.NotATapsigner ->
                    WalletError.SigningFailed("Tag is not a TAPSIGNER card")
                is TapsignerError.NotSetUp ->
                    WalletError.SigningFailed("TAPSIGNER has not been set up")
                is TapsignerError.HostError ->
                    WalletError.SigningFailed("Host NFC error: $reason")
                is TapsignerError.ProtocolError ->
                    WalletError.SigningFailed("Protocol error: $reason")
            }

        private companion object {
            const val TAG = "TapsignerNfcSigner"
        }
    }
