package com.possatstack.app.ui.wallet.seedphrase

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.possatstack.app.R
import com.possatstack.app.ui.components.PrimaryButton
import com.possatstack.app.ui.theme.BitcoinOrange
import com.possatstack.app.ui.wallet.WalletViewModel
import com.possatstack.app.util.generateQrBitmap

private const val QR_SIZE_PX = 720

private enum class BackupTab { Seeds, QrCode }

/**
 * Backup screen for an existing wallet's BIP-39 mnemonic.
 *
 * @param onContinue When non-null, the screen renders a "I have backed up my
 *   seeds" checkbox plus a Continue button at the bottom — the typical
 *   onboarding flow. When null (Settings entry point), no acknowledgment is
 *   required and the seeds are simply displayed.
 */
@Composable
fun WalletSeedPhraseScreen(
    viewModel: WalletViewModel = hiltViewModel(),
    onContinue: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadMnemonic()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearMnemonicFromState() }
    }

    val mnemonic = state.mnemonic
    val words = mnemonic?.split(" ") ?: emptyList()
    var selectedTab by remember { mutableStateOf(BackupTab.Seeds) }
    var hasBackedUp by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        BackupTabs(
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WarningCard()
            Spacer(Modifier.height(24.dp))

            when {
                words.isEmpty() ->
                    Text(
                        text = stringResource(R.string.seed_phrase_not_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                selectedTab == BackupTab.Seeds -> SeedsGrid(words = words)
                selectedTab == BackupTab.QrCode -> SeedsQrCode(mnemonic = mnemonic.orEmpty())
            }
        }

        if (onContinue != null) {
            Spacer(Modifier.height(16.dp))
            ConfirmAndContinue(
                checked = hasBackedUp,
                onCheckedChange = { hasBackedUp = it },
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun BackupTabs(
    selected: BackupTab,
    onSelect: (BackupTab) -> Unit,
) {
    val selectedIndex = if (selected == BackupTab.Seeds) 0 else 1
    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.White,
        contentColor = BitcoinOrange,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = BitcoinOrange,
                )
            }
        },
    ) {
        Tab(
            selected = selected == BackupTab.Seeds,
            onClick = { onSelect(BackupTab.Seeds) },
            selectedContentColor = BitcoinOrange,
            unselectedContentColor = Color(0xFF777777),
            text = {
                Text(
                    text = stringResource(R.string.wallet_backup_tab_seeds),
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
        Tab(
            selected = selected == BackupTab.QrCode,
            onClick = { onSelect(BackupTab.QrCode) },
            selectedContentColor = BitcoinOrange,
            unselectedContentColor = Color(0xFF777777),
            text = {
                Text(
                    text = stringResource(R.string.wallet_backup_tab_qr),
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}

@Composable
private fun WarningCard() {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.seed_phrase_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SeedsGrid(words: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        words.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { colIndex, word ->
                    val wordIndex = rowIndex * 2 + colIndex + 1
                    SeedWord(
                        index = wordIndex,
                        word = word,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SeedWord(
    index: Int,
    word: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        Text(
            text = word,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeedsQrCode(mnemonic: String) {
    val qrBitmap = remember(mnemonic) { generateQrBitmap(mnemonic, QR_SIZE_PX) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.qr_code_description),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.wallet_backup_qr_caption),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ConfirmAndContinue(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = BitcoinOrange,
                        uncheckedColor = Color(0xFF9E9E9E),
                    ),
            )
            Text(
                text = stringResource(R.string.wallet_backup_confirm),
                fontWeight = FontWeight.Medium,
                color = Color.Black,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = stringResource(R.string.welcome_continue),
            enabled = checked,
            onClick = onContinue,
        )
    }
}
