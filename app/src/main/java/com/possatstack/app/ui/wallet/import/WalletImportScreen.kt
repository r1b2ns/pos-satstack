package com.possatstack.app.ui.wallet.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.ui.wallet.WalletViewModel

@Composable
fun WalletImportScreen(
    onImported: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mnemonic by remember { mutableStateOf("") }

    val wordCount = remember(mnemonic) {
        mnemonic.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
    }
    val isValid = wordCount == 12 || wordCount == 24

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.wallet_import),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = mnemonic,
            onValueChange = { mnemonic = it },
            label = { Text(stringResource(R.string.enter_seed_phrase)) },
            placeholder = { Text(stringResource(R.string.enter_seed_phrase_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            minLines = 4,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    text = "$wordCount / 12 or 24 words",
                    color = if (isValid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Button(
            onClick = {
                viewModel.importWallet(mnemonic.trim())
                onImported()
            },
            enabled = isValid && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_wallet))
        }
    }
}
