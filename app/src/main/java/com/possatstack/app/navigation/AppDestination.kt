package com.possatstack.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {
    @Serializable
    data object Home : AppDestination

    @Serializable
    data object Charge : AppDestination

    @Serializable
    data object Settings : AppDestination
}
