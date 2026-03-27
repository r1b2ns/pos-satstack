package com.possatstack.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {

    // ── Root ─────────────────────────────────────────────────────────────────

    @Serializable
    data object Home : AppDestination

    @Serializable
    data object Charge : AppDestination

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

    @Serializable
    data object WalletTransactions : AppDestination
}
