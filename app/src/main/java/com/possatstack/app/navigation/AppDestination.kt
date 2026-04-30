package com.possatstack.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {
    // ── Root ─────────────────────────────────────────────────────────────────

    @Serializable
    data object Welcome : AppDestination

    @Serializable
    data object OnboardingSetup : AppDestination

    @Serializable
    data object Charge : AppDestination

    /** Details screen for an active charge: QR + payment-status tracking. */
    @Serializable
    data class ChargeDetails(val chargeId: String) : AppDestination

    @Serializable
    data object Settings : AppDestination

    // ── Wallet ───────────────────────────────────────────────────────────────

    @Serializable
    data object Wallet : AppDestination

    @Serializable
    data object WalletImport : AppDestination

    @Serializable
    data object WalletReceive : AppDestination

    @Serializable
    data object WalletSend : AppDestination

    @Serializable
    data object WalletSeedPhrase : AppDestination

    /**
     * Onboarding-only seed-phrase backup screen. Same UI as
     * [WalletSeedPhrase] but with the "I have backed up my seeds" checkbox
     * and a Continue button — gating advancement to the charge screen.
     */
    @Serializable
    data object WalletBackup : AppDestination

    @Serializable
    data object WalletTransactions : AppDestination

    /**
     * Generic QR-scanner module. The caller listens for a result on its own
     * back-stack entry under [SCAN_RESULT_KEY] (set via savedStateHandle by
     * the scan composable in NavGraph before popping).
     */
    @Serializable
    data object ScanQRCode : AppDestination

    companion object {
        const val SCAN_RESULT_KEY: String = "scan_qr_result"
    }
}
