package com.possatstack.app.wallet.signer.tapsigner

/**
 * Carries the public-key material read from a TAPSIGNER during the
 * "import wallet" onboarding flow. Caller is expected to wrap this into a
 * `WalletBackup.XpubWatching` and hand it to the on-chain engine.
 *
 * - [accountXpub] is the BIP-84 account-level xpub (`m/84'/coin'/0'`)
 *   that the card already derived during its `init()`.
 * - [masterFingerprint] is the 8-hex-char identifier of the master key,
 *   computed by BDK from the master xpub. Required for the descriptor
 *   origin annotation so the future PSBT signer can match the input
 *   bip32_derivation entries against this exact card.
 */
data class TapsignerWalletInfo(
    val masterFingerprint: String,
    val accountXpub: String,
)

/**
 * Progress emitted by [TapsignerImportReader.fetchAccountXpub]. Same
 * shape as [TapsignerStep] but the `Done` payload is the xpub material
 * instead of a signed PSBT, so we keep it on a sibling sealed hierarchy.
 */
sealed interface TapsignerImportStep {
    data object AwaitingCvc : TapsignerImportStep

    data object AwaitingTap : TapsignerImportStep

    data object Exchanging : TapsignerImportStep

    data class Done(val info: TapsignerWalletInfo) : TapsignerImportStep

    data class Failed(val error: TapsignerError) : TapsignerImportStep
}
