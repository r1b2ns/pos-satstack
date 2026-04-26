package com.possatstack.app.ui.signer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.possatstack.app.R
import com.possatstack.app.wallet.signer.Signer
import com.possatstack.app.wallet.signer.SignerKind

/**
 * Bottom sheet that lets the user pick between registered [Signer]s.
 *
 * Callers feed [signers] from
 * [com.possatstack.app.wallet.signer.SignerRegistry.all] and receive the
 * chosen signer through [onPicked]. When only one signer is registered,
 * callers should skip showing this sheet entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignerPickerSheet(
    signers: List<Signer>,
    onPicked: (Signer) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.signer_picker_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.signer_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            signers.forEachIndexed { index, signer ->
                ListItem(
                    headlineContent = { Text(text = labelFor(signer.kind)) },
                    supportingContent = { Text(text = signer.id) },
                    leadingContent = {
                        Icon(
                            imageVector = iconFor(signer.kind),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onPicked(signer) },
                )
                if (index < signers.lastIndex) HorizontalDivider()
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun labelFor(kind: SignerKind): String =
    when (kind) {
        SignerKind.SOFTWARE_SEED -> stringResource(R.string.signer_kind_seed)
        SignerKind.TAPSIGNER_NFC -> stringResource(R.string.signer_kind_tapsigner)
        SignerKind.COLDCARD_AIRGAP -> stringResource(R.string.signer_kind_coldcard)
        SignerKind.AIRGAP_QR -> stringResource(R.string.signer_kind_qr)
    }

private fun iconFor(kind: SignerKind): ImageVector =
    when (kind) {
        SignerKind.SOFTWARE_SEED -> Icons.Filled.Key
        SignerKind.TAPSIGNER_NFC -> Icons.Filled.Contactless
        SignerKind.COLDCARD_AIRGAP -> Icons.Filled.Usb
        SignerKind.AIRGAP_QR -> Icons.Filled.QrCode2
    }
