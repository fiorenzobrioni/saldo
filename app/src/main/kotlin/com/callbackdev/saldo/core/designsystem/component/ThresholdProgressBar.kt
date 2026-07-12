package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Determinate progress bar for values measured against a cap (budgets):
 * a full-pill track with an animated fill. [fraction] may exceed 1 (the fill
 * clamps, overshoot is the caller's text to tell); [color] carries the state
 * and comes from the caller, which must never rely on it alone (pair it with
 * a percentage or an icon, accessibility rule).
 */
@Composable
fun ThresholdProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = DefaultHeight,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "thresholdProgress",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedFraction)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private val DefaultHeight = 8.dp
