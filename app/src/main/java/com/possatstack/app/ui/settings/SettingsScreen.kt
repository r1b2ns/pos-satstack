package com.possatstack.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.possatstack.app.R
import com.possatstack.app.ui.theme.PosTheme

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.coming_soon))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PosTheme { SettingsScreen() }
}
