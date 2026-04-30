package com.possatstack.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.possatstack.app.R
import com.possatstack.app.ui.components.PrimaryButton
import com.possatstack.app.ui.components.RadioOption
import com.possatstack.app.ui.components.RadioOptionGroup

enum class WalletSetupChoice { ImportPublicKey, CreateWallet }

@Composable
fun OnboardingSetupScreen(
    onContinue: (WalletSetupChoice) -> Unit,
    isLoading: Boolean = false,
) {
    var choice by remember { mutableStateOf<WalletSetupChoice?>(null) }

    val options =
        listOf(
            RadioOption(
                value = WalletSetupChoice.ImportPublicKey,
                title = stringResource(R.string.onboarding_option_import_pubkey_title),
                subtitle = stringResource(R.string.onboarding_option_import_pubkey_subtitle),
                infoTooltip = stringResource(R.string.onboarding_option_import_pubkey_info),
            ),
            RadioOption(
                value = WalletSetupChoice.CreateWallet,
                title = stringResource(R.string.onboarding_option_create_wallet_title),
                subtitle = stringResource(R.string.onboarding_option_create_wallet_subtitle),
            ),
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_setup_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_setup_description),
                fontSize = 14.sp,
                color = Color(0xFF555555),
            )
            Spacer(Modifier.height(24.dp))

            RadioOptionGroup(
                options = options,
                selected = choice,
                onSelect = { choice = it },
            )
        }

        PrimaryButton(
            label = stringResource(R.string.welcome_continue),
            onClick = { choice?.let(onContinue) },
            enabled = choice != null,
            isLoading = isLoading,
        )
    }
}
