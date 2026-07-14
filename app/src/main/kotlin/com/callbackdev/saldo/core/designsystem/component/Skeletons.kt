package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Content-shaped placeholders shown while a screen's first data resolves, in
 * place of a bare spinner. Each block traces where real content will land, so
 * the layout does not jump when data arrives; a slow pulse signals "loading"
 * without the harsh spin of an indeterminate indicator.
 *
 * The pulse is a single infinite transition per screen (one animation, shared
 * as a [Color] by every block) rather than one per block, and the whole thing
 * is transient: these compose for a frame or two before the real UI takes over.
 */

/** The animated fill color every skeleton block shares: a soft, breathing tint. */
@Composable
fun rememberSkeletonColor(): Color {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = MIN_ALPHA,
        targetValue = MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
}

/** A single placeholder block: a rounded, softly pulsing rectangle. */
@Composable
fun SkeletonBlock(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = BlockShape,
) {
    Box(modifier.clip(shape).background(color))
}

/**
 * Loading placeholder for the dashboard: a title line, the hero balance card,
 * the two period cards, a wide summary card and a few recent rows, matching the
 * real screen's rhythm.
 */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val color = rememberSkeletonColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(color, Modifier.padding(start = 4.dp).width(200.dp).height(28.dp))
        SkeletonBlock(color, Modifier.fillMaxWidth().height(150.dp), shape = CardShape)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(color, Modifier.weight(1f).height(96.dp), shape = CardShape)
            SkeletonBlock(color, Modifier.weight(1f).height(96.dp), shape = CardShape)
        }
        SkeletonBlock(color, Modifier.fillMaxWidth().height(80.dp), shape = CardShape)
        SkeletonBlock(color, Modifier.padding(start = 4.dp).width(140.dp).height(20.dp))
        repeat(RECENT_ROWS) { SkeletonRow(color) }
    }
}

/**
 * Loading placeholder for list screens (movements, accounts, budgets, ...): a
 * column of leading-avatar rows with two text lines and a trailing amount.
 */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = LIST_ROWS) {
    val color = rememberSkeletonColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) { SkeletonRow(color) }
    }
}

/** One list row placeholder: avatar, two stacked lines, trailing amount block. */
@Composable
private fun SkeletonRow(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(color, Modifier.size(40.dp), shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBlock(color, Modifier.fillMaxWidth(LINE_LONG).height(16.dp))
            SkeletonBlock(color, Modifier.fillMaxWidth(LINE_SHORT).height(12.dp))
        }
        Spacer(Modifier.width(12.dp))
        SkeletonBlock(color, Modifier.width(64.dp).height(16.dp))
    }
}

private val BlockShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(24.dp)

private const val MIN_ALPHA = 0.10f
private const val MAX_ALPHA = 0.28f
private const val PULSE_MILLIS = 900
private const val RECENT_ROWS = 3
private const val LIST_ROWS = 7
private const val LINE_LONG = 0.55f
private const val LINE_SHORT = 0.35f
