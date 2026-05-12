package com.possatstack.app.wallet.signer.tapsigner

import com.possatstack.app.util.AppLogger
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.signer.SigningContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bitcoindevkit.cktap.CardException
import org.bitcoindevkit.cktap.CkTapCard
import org.bitcoindevkit.cktap.CkTapException
import org.bitcoindevkit.cktap.SignPsbtException
import org.bitcoindevkit.cktap.toCktap

/**
 * Drives a single TAPSIGNER interaction: identifies the tapped card via
 * `cktap-android`, validates the kind, and delegates PSBT signing to the
 * `TapSigner.signPsbt` UniFFI call. Exposes progress as [TapsignerStep]
 * values so the UI dialog can render the right step.
 *
 * All ECDH/secp256k1 work happens inside the Rust `cktap-ffi` crate —
 * this class only orchestrates the Kotlin side (CVC conversion, step
 * transitions, error mapping, resource cleanup).
 */
internal class TapsignerSession {
    private val stateFlow: MutableStateFlow<TapsignerStep> =
        MutableStateFlow(TapsignerStep.AwaitingCvc)

    val state: Flow<TapsignerStep> = stateFlow.asStateFlow()

    fun currentStep(): TapsignerStep = stateFlow.value

    fun advance(step: TapsignerStep) {
        stateFlow.value = step
    }

    /**
     * Run the full sign flow. The caller supplies the [Cvc] (already
     * validated) and a connected [transport]. The transport is closed and
     * the card disposed before this returns, regardless of outcome.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun run(
        transport: IsoDepCkTransport,
        cvc: Cvc,
        psbt: UnsignedPsbt,
        context: SigningContext,
    ): SignedPsbt {
        var card: CkTapCard? = null
        var cvcString: String? = null
        try {
            advance(TapsignerStep.Exchanging)

            card =
                try {
                    toCktap(transport)
                } catch (exception: CkTapException) {
                    throw exception.toTapsignerError()
                } catch (exception: CardException) {
                    throw exception.toTapsignerError()
                }

            val tapSigner =
                (card as? CkTapCard.TapSigner)?.v1
                    ?: throw TapsignerError.NotATapsigner

            cvcString = String(cvc.asBytes(), Charsets.US_ASCII)

            val signed =
                try {
                    tapSigner.signPsbt(psbt.base64, cvcString)
                } catch (exception: SignPsbtException) {
                    throw exception.toTapsignerError()
                } catch (exception: CardException) {
                    throw exception.toTapsignerError()
                } catch (exception: CkTapException) {
                    throw exception.toTapsignerError()
                }

            return SignedPsbt(signed)
        } catch (error: TapsignerError) {
            advance(TapsignerStep.Failed(error))
            throw error
        } finally {
            cvcString?.toByteArray(Charsets.US_ASCII)?.fill(0)
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
        const val TAG = "TapsignerSession"
    }
}

private fun CardException.toTapsignerError(): TapsignerError =
    when (this) {
        is CardException.BadAuth -> TapsignerError.WrongCvc(attemptsLeft = null)
        is CardException.NeedsAuth -> TapsignerError.WrongCvc(attemptsLeft = null)
        is CardException.RateLimited -> TapsignerError.RateLimited(waitSeconds = 0)
        is CardException.InvalidState -> TapsignerError.NotSetUp
        else -> TapsignerError.ProtocolError(this::class.simpleName ?: "CardException", this)
    }

private fun CkTapException.toTapsignerError(): TapsignerError =
    when (this) {
        is CkTapException.Card -> TapsignerError.ProtocolError("Card error", this)
        is CkTapException.CborDe -> TapsignerError.ProtocolError("CBOR decode", this)
        is CkTapException.CborValue -> TapsignerError.ProtocolError("CBOR value", this)
        is CkTapException.Transport -> TapsignerError.CardRemoved
        is CkTapException.UnknownCardType -> TapsignerError.NotATapsigner
        else -> TapsignerError.ProtocolError(this::class.simpleName ?: "CkTapException", this)
    }

private fun SignPsbtException.toTapsignerError(): TapsignerError =
    when (this) {
        is SignPsbtException.CkTap -> TapsignerError.ProtocolError("CkTap sign error", this)
        else -> TapsignerError.ProtocolError(this::class.simpleName ?: "SignPsbtException", this)
    }
