package com.possatstack.app.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.possatstack.app.R
import com.possatstack.app.ui.components.IntroCarousel
import com.possatstack.app.ui.components.IntroStep

@Composable
fun WelcomeCarouselScreen(onContinue: () -> Unit) {
    val steps =
        listOf(
            IntroStep(
                icon = Icons.Default.CurrencyBitcoin,
                title = stringResource(R.string.welcome_step1_title),
                description = stringResource(R.string.welcome_step1_description),
            ),
            IntroStep(
                icon = Icons.Default.MoneyOff,
                title = stringResource(R.string.welcome_step2_title),
                description = stringResource(R.string.welcome_step2_description),
            ),
            IntroStep(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.welcome_step3_title),
                description = stringResource(R.string.welcome_step3_description),
            ),
            IntroStep(
                icon = Icons.Default.Public,
                title = stringResource(R.string.welcome_step4_title),
                description = stringResource(R.string.welcome_step4_description),
                actionLabel = stringResource(R.string.welcome_continue),
                onAction = onContinue,
            ),
        )

    IntroCarousel(steps = steps)
}
