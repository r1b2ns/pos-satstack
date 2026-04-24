package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.SignedPsbt
import com.possatstack.app.wallet.UnsignedPsbt

class FakeSigner : Signer {
    override val id: String = "fake-seed"
    override val kind: SignerKind = SignerKind.SOFTWARE_SEED

    var signResult: Result<SignedPsbt> = Result.success(SignedPsbt("cHNidP//signed"))

    var signCount = 0
    var lastPsbt: UnsignedPsbt? = null
    var lastContext: SigningContext? = null

    override suspend fun signPsbt(
        psbt: UnsignedPsbt,
        context: SigningContext,
    ): SignedPsbt {
        signCount++
        lastPsbt = psbt
        lastContext = context
        return signResult.getOrThrow()
    }
}
