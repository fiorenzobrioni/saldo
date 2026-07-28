package com.callbackdev.saldo.feature.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled

/**
 * The six-dot progress of the PIN entry. Each dot fills with a small scale-in
 * as its digit lands; a rejected PIN turns the row to the error color and
 * shakes it (skipped when system animations are off), with a long-press
 * haptic so the refusal is felt as well as seen.
 *
 * [shakeTick] increments on every rejection: same-error-twice still shakes.
 * The row reads as one TalkBack node ("N of M digits", polite live region)
 * instead of six meaningless circles.
 */
@Composable
fun PinIndicator(
    filled: Int,
    total: Int,
    isError: Boolean,
    shakeTick: Int,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = rememberMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    val shakeDistancePx = with(LocalDensity.current) { ShakeDistance.toPx() }
    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (motionEnabled) {
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = ShakeDurationMillis
                        0f at 0
                        -shakeDistancePx at 50
                        shakeDistancePx at 130
                        -shakeDistancePx / 2 at 210
                        shakeDistancePx / 2 at 280
                        0f at ShakeDurationMillis
                    },
                )
            }
        }
    }

    val progressDescription = stringResource(R.string.pin_progress, filled, total)
    Row(
        horizontalArrangement = Arrangement.spacedBy(DotSpacing),
        modifier = modifier
            .graphicsLayer { translationX = shakeOffset.value }
            .semantics {
                contentDescription = progressDescription
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        repeat(total) { index ->
            PinDot(
                isFilled = index < filled,
                isError = isError,
                motionEnabled = motionEnabled,
            )
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isError: Boolean,
    motionEnabled: Boolean,
) {
    val ringColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val fillColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val fillScale by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0f,
        animationSpec = if (motionEnabled) spring() else snap(),
        label = "pin-dot-fill",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DotSize)
            .border(width = DotRingWidth, color = ringColor, shape = CircleShape),
    ) {
        Box(
            modifier = Modifier
                .size(DotSize)
                .graphicsLayer {
                    scaleX = fillScale
                    scaleY = fillScale
                }
                .background(color = fillColor, shape = CircleShape),
        )
    }
}

private val DotSize = 14.dp
private val DotRingWidth = 2.dp
private val DotSpacing = 14.dp
private val ShakeDistance = 8.dp
private const val ShakeDurationMillis = 350
