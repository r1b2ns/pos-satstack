package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.BitcoinAddress
import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt
import com.possatstack.app.wallet.WalletNetwork

/**
 * Library-agnostic PSBT signer.
 *
 * The [com.possatstack.app.wallet.OnChainWalletEngine] builds an unsigned PSBT
 * and a [Signer] produces the signed version. Separating the two is what makes
 * it possible to use hardware signers (TAPSIGNER, Coldcard, airgap QR) later —
 * the engine stays BDK-internal, the signer speaks only in neutral PSBT bytes.
 *
 * Every [Signer] must validate the [SigningContext] before applying any
 * signature so the user cannot be tricked into signing something different
 * from what the UI displayed (amount, recipients, change address).
 */
interface Signer {
    /** Opaque id for logging / analytics / selection in multi-signer setups. */
    val id: String

    val kind: SignerKind

    /**
     * Produce a fully signed, finalised PSBT. May prompt the user via the
     * platform-specific authenticator (biometric/NFC tap/QR scan).
     *
     * @throws com.possatstack.app.wallet.WalletError.SigningFailed if the
     *         signer cannot produce a valid signature (missing key, PSBT
     *         malformed, user cancelled, hardware not present).
     * @throws com.possatstack.app.wallet.WalletError.SecretStoreUnavailable
     *         if a software signer cannot access its secret store
     *         (biometric cancelled, Keystore key invalidated).
     */
    suspend fun signPsbt(
        psbt: UnsignedPsbt,
        context: SigningContext,
    ): SignedPsbt
}

/**
 * What the Signer needs to validate before signing. The UI builds this from
 * the same data it showed the user, and the Signer cross-checks it against
 * the PSBT internals (outputs, amounts, change) so the user cannot be tricked
 * by a malicious process into signing a different transaction.
 */
data class SigningContext(
    val network: WalletNetwork,
    val recipients: List<SigningRecipient>,
    val totalSendSats: Long,
    val feeSats: Long,
    val changeAddress: BitcoinAddress?,
)

data class SigningRecipient(
    val address: BitcoinAddress,
    val amountSats: Long,
)

enum class SignerKind {
    SOFTWARE_SEED,
    TAPSIGNER_NFC,
    COLDCARD_AIRGAP,
    AIRGAP_QR,
}
