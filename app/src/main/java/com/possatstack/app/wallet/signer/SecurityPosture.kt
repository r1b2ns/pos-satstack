package com.possatstack.app.wallet.signer

/**
 * Describes how much hardware isolation the device gave us for the mnemonic
 * master key. Exposed via [SignerSecretStore.securityPosture] so ops can log
 * it and surface a warning when a POS device falls through to software-only
 * Keystore.
 */
data class SecurityPosture(
    val hardwareBacked: Boolean,
    val strongBoxBacked: Boolean,
    val deviceSecure: Boolean,
) {
    companion object {
        val UnknownSoftware =
            SecurityPosture(
                hardwareBacked = false,
                strongBoxBacked = false,
                deviceSecure = false,
            )
    }
}
