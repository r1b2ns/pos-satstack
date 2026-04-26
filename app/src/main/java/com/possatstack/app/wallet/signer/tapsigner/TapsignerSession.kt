package com.possatstack.app.wallet.signer.tapsigner

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.signer.SigningContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Drives a single TAPSIGNER interaction from the CVC prompt through the
 * ECDH handshake and PSBT signing. Exposes progress as
 * [TapsignerStep] values for the UI dialog.
 *
 * The session does not own NFC resources directly — it receives a
 * [TapsignerClient] from a [NfcSessionLauncher] and is responsible for
 * cleaning up by calling [TapsignerClient.close] when it finishes.
 *
 * The PSBT signing pipeline (input digest extraction, signature assembly
 * back into the PSBT) is intentionally bounded by the
 * [TapsignerCrypto] interface — see the companion notes. Until the
 * secp256k1 wiring lands, [run] will fail at the handshake step with
 * [TapsignerError.HostError] and surface that through [state].
 */
internal class TapsignerSession(
    private val crypto: TapsignerCrypto,
    private val random: SecureRandom = SecureRandom(),
) {
    private val stateFlow: MutableStateFlow<TapsignerStep> =
        MutableStateFlow(TapsignerStep.AwaitingCvc)

    val state: Flow<TapsignerStep> = stateFlow.asStateFlow()

    fun currentStep(): TapsignerStep = stateFlow.value

    /** Update the observable state; used by the UI dialog + signer. */
    fun advance(step: TapsignerStep) {
        stateFlow.value = step
    }

    /**
     * Drive the full sign flow. The caller supplies the CVC (already
     * validated) and a freshly-connected [TapsignerClient].
     */
    suspend fun run(
        client: TapsignerClient,
        cvc: Cvc,
        psbt: UnsignedPsbt,
        context: SigningContext,
    ): SignedPsbt {
        try {
            advance(TapsignerStep.Exchanging)

            val statusMap = client.send(TapsignerCommand.Status())
            val status = TapsignerResponse.parseStatus(statusMap)
            AppLogger.info(
                TAG,
                "TAPSIGNER status proto=${status.proto} slots=${status.slots} testnet=${status.isTestnet}",
            )
            val cardPubkey =
                status.cardPubkey
                    ?: throw TapsignerError.ProtocolError("status did not include card pubkey")

            val ephemeral = crypto.generateEphemeralKeyPair()
            val sessionKey = crypto.deriveSharedSecret(ephemeral.privateKey, cardPubkey)

            val hostNonce = ByteArray(HOST_NONCE_LENGTH).also { random.nextBytes(it) }
            val xcvc =
                crypto.encryptCvc(
                    cvc = cvc,
                    cardNonce = status.cardNonce,
                    ourNonce = hostNonce,
                    sessionKey = sessionKey,
                )

            // NOTE: the PSBT → per-input digest → sign → reinject-signature pipeline
            // belongs here. It depends on the signing context ([context.recipients],
            // [context.network], [context.changeAddress]) to cross-check each input
            // against what the user approved, and on [TapsignerCrypto.verifyCardSignature]
            // to validate each returned signature before splicing it back into the
            // PSBT. That wiring is the hardware-QA follow-up tracked in docs/phase-5.md.
            throw TapsignerError.HostError(
                "TAPSIGNER sign pipeline requires hardware-QA follow-up (see docs/phase-5.md)",
            )
        } catch (error: TapsignerError) {
            advance(TapsignerStep.Failed(error))
            throw error
        } finally {
            try {
                client.close()
            } catch (exception: Exception) {
                AppLogger.warning(TAG, "client close raised: ${exception.message}")
            }
        }
    }

    private companion object {
        const val TAG = "TapsignerSession"
        const val HOST_NONCE_LENGTH = 16
    }
}
