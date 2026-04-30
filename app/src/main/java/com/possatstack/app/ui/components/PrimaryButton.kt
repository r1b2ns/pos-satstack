package com.possatstack.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.possatstack.app.ui.theme.BitcoinOrange

private val DisabledContainer = Color(0xFFE0E0E0)
private val DisabledContent = Color(0xFF9E9E9E)

/**
 * Bottom-of-screen primary call-to-action button. Mirrors the look of the
 * "Charge" button on ChargeScreen so every flow feels consistent.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = BitcoinOrange,
                contentColor = Color.White,
                disabledContainerColor = DisabledContainer,
                disabledContentColor = DisabledContent,
            ),
        shape = RoundedCornerShape(32.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(24.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
