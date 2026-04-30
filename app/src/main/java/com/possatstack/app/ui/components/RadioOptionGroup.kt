package com.possatstack.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.possatstack.app.ui.theme.BitcoinOrange
import kotlinx.coroutines.launch

/**
 * Single option inside [RadioOptionGroup].
 *
 * @param infoTooltip When non-null, a small "?" icon is shown next to the
 *   title. Tapping it reveals this text in a tooltip — useful for quickly
 *   explaining nuance without bloating the subtitle.
 */
data class RadioOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val infoTooltip: String? = null,
)

/**
 * Reusable single-select group of card-style radio options. Selection is
 * controlled by the caller — this composable is purely presentational.
 */
@Composable
fun <T> RadioOptionGroup(
    options: List<RadioOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = BitcoinOrange,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { option ->
            RadioOptionCard(
                option = option,
                isSelected = option.value == selected,
                onSelect = { onSelect(option.value) },
                accent = accent,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> RadioOptionCard(
    option: RadioOption<T>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    accent: Color,
) {
    val borderColor = if (isSelected) accent else Color(0xFFE0E0E0)
    val containerColor = if (isSelected) accent.copy(alpha = 0.06f) else Color.White

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = accent,
                        unselectedColor = Color(0xFF9E9E9E),
                    ),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                    if (option.infoTooltip != null) {
                        Spacer(Modifier.width(4.dp))
                        InfoTooltip(text = option.infoTooltip, accent = accent)
                    }
                }
                if (option.subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = option.subtitle,
                        fontSize = 13.sp,
                        color = Color(0xFF555555),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTooltip(
    text: String,
    accent: Color,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = Color.Black,
                contentColor = Color.White,
            ) {
                Text(text, fontSize = 13.sp)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = { scope.launch { tooltipState.show() } },
            modifier = Modifier.size(24.dp),
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp).background(Color.Transparent),
                )
            }
        }
    }
}
