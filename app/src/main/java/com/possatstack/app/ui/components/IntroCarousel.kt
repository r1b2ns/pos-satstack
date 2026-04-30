package com.possatstack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.possatstack.app.ui.theme.BitcoinOrange

/**
 * One slide of [IntroCarousel].
 *
 * @param actionLabel If non-null, the slide renders a primary action button
 *   below the description. Used by the last slide of an intro flow to advance
 *   to the next module.
 */
data class IntroStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Reusable swipe-paged carousel. Each step shows a centered icon, title and
 * description on a black background using the app's bitcoin-orange accent.
 * Designed for intro/welcome flows but generic enough for any onboarding-style
 * sequence.
 */
@Composable
fun IntroCarousel(
    steps: List<IntroStep>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    accent: Color = BitcoinOrange,
    onAccent: Color = Color.White,
    titleColor: Color = Color.Black,
    descriptionColor: Color = Color(0xFF666666),
) {
    val pagerState = rememberPagerState(pageCount = { steps.size })

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(background),
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            IntroStepContent(
                step = steps[page],
                accent = accent,
                onAccent = onAccent,
                titleColor = titleColor,
                descriptionColor = descriptionColor,
            )
        }

        PageIndicator(
            count = steps.size,
            selected = pagerState.currentPage,
            accent = accent,
            inactive = Color(0xFFE0E0E0),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
        )
    }
}

@Composable
private fun IntroStepContent(
    step: IntroStep,
    accent: Color,
    onAccent: Color,
    titleColor: Color,
    descriptionColor: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(140.dp)
                    .background(accent, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(72.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = step.title,
            color = titleColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = step.description,
            color = descriptionColor,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (step.actionLabel != null && step.onAction != null) {
            Spacer(Modifier.height(48.dp))
            PrimaryButton(
                label = step.actionLabel,
                onClick = step.onAction,
            )
        }
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    selected: Int,
    accent: Color,
    inactive: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isSelected = index == selected
            Box(
                modifier =
                    Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .background(
                            color = if (isSelected) accent else inactive,
                            shape = CircleShape,
                        ),
            )
        }
    }
}
