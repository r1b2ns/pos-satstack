package com.possatstack.app.ui.charge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.possatstack.app.R
import com.possatstack.app.ui.theme.BitcoinOrange
import com.possatstack.app.ui.theme.PosTheme
import com.possatstack.app.util.SatsFormatter

private val KeypadTextColor = Color.Black
private val KeypadDividerColor = Color(0xFFEEEEEE)
private val BackspaceIconColor = Color(0xFFE53935)
private val ChargeButtonDisabledColor = Color(0xFFE0E0E0)
private val ChargeButtonDisabledContent = Color(0xFF9E9E9E)

private const val MAX_SATS: Long = 100_000_000L

@Composable
fun ChargeScreen() {
    var amountSats by rememberSaveable { mutableStateOf("0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        AmountDisplay(
            amountSats = amountSats,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Keypad(
            showEditKeys = amountSats != "0",
            onDigit = { digit ->
                val next = if (amountSats == "0") digit.toString() else amountSats + digit
                val nextValue = next.toLongOrNull()
                if (nextValue != null && nextValue <= MAX_SATS) {
                    amountSats = next
                }
            },
            onClear = { amountSats = "0" },
            onBackspace = {
                amountSats = if (amountSats.length > 1) amountSats.dropLast(1) else "0"
            },
        )

        Spacer(Modifier.height(24.dp))

        ChargeButton(
            enabled = amountSats != "0",
            onClick = { /* TODO: create payment request */ },
        )
    }
}

@Composable
private fun AmountDisplay(
    amountSats: String,
    modifier: Modifier = Modifier,
) {
    val displayText = "₿${SatsFormatter.format(amountSats)}"
    var amountFontSize by remember(displayText) { mutableStateOf(64.sp) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = displayText,
            fontSize = amountFontSize,
            fontWeight = FontWeight.Bold,
            color = KeypadTextColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                if (result.didOverflowWidth && amountFontSize.value > 24f) {
                    amountFontSize = (amountFontSize.value * 0.9f).sp
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.charge_sats_label),
            fontSize = 18.sp,
            color = KeypadTextColor.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun Keypad(
    showEditKeys: Boolean,
    onDigit: (Int) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KeypadRow {
            DigitKey(1, onDigit)
            KeypadColumnDivider()
            DigitKey(2, onDigit)
            KeypadColumnDivider()
            DigitKey(3, onDigit)
        }
        KeypadRowDivider()
        KeypadRow {
            DigitKey(4, onDigit)
            KeypadColumnDivider()
            DigitKey(5, onDigit)
            KeypadColumnDivider()
            DigitKey(6, onDigit)
        }
        KeypadRowDivider()
        KeypadRow {
            DigitKey(7, onDigit)
            KeypadColumnDivider()
            DigitKey(8, onDigit)
            KeypadColumnDivider()
            DigitKey(9, onDigit)
        }
        KeypadRowDivider()
        KeypadRow {
            if (showEditKeys) {
                TextKey(label = stringResource(R.string.charge_key_clear), onClick = onClear)
            } else {
                EmptyKey()
            }
            KeypadColumnDivider()
            DigitKey(0, onDigit)
            KeypadColumnDivider()
            if (showEditKeys) {
                BackspaceKey(onClick = onBackspace)
            } else {
                EmptyKey()
            }
        }
    }
}

@Composable
private fun RowScope.EmptyKey() {
    Box(modifier = Modifier.weight(1f))
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun KeypadRowDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = KeypadDividerColor,
    )
}

@Composable
private fun KeypadColumnDivider() {
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        thickness = 1.dp,
        color = KeypadDividerColor,
    )
}

@Composable
private fun RowScope.DigitKey(digit: Int, onDigit: (Int) -> Unit) {
    TextKey(label = digit.toString(), onClick = { onDigit(digit) })
}

@Composable
private fun RowScope.TextKey(label: String, onClick: () -> Unit) {
    KeyContainer(onClick = onClick) {
        Text(
            text = label,
            fontSize = 32.sp,
            color = KeypadTextColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RowScope.BackspaceKey(onClick: () -> Unit) {
    KeyContainer(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.charge_key_backspace),
            tint = BackspaceIconColor,
        )
    }
}

@Composable
private fun RowScope.KeyContainer(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun ChargeButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = BitcoinOrange,
            contentColor = Color.White,
            disabledContainerColor = ChargeButtonDisabledColor,
            disabledContentColor = ChargeButtonDisabledContent,
        ),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = stringResource(R.string.charge),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChargeScreenPreview() {
    PosTheme { ChargeScreen() }
}
