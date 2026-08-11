package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Speed-dial FAB shared by the screens that create movements: the toggle
 * expands into a stack of labelled action pills. While open, the toggle morphs
 * from the FAB's rounded square to a filled primary circle and its plus
 * rotates into a close cross - the Material 3 Expressive FAB-menu shape
 * language, on gently bouncy spatial springs (plain Compose springs: on the
 * stable BOM the M3 MotionScheme API is still internal, see SaldoTheme). The
 * caller owns [expanded] so it can also dim the content behind the dial (see
 * [SpeedDialScrim]).
 */
@Composable
fun SpeedDialFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<SpeedDialAction>,
    toggleDescription: String,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) TOGGLE_ROTATION_DEGREES else 0f,
        animationSpec = spatialSpring(),
        label = "speedDialRotation",
    )
    val corner by animateDpAsState(
        targetValue = if (expanded) TOGGLE_CORNER_OPEN else TOGGLE_CORNER_CLOSED,
        animationSpec = spatialSpring(),
        label = "speedDialCorner",
    )
    val container by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = COLOR_MORPH_MILLIS),
        label = "speedDialContainer",
    )
    val content by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = COLOR_MORPH_MILLIS),
        label = "speedDialContent",
    )
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() +
                scaleIn(spatialSpring(), transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() +
                scaleOut(spatialSpring(), transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ACTION_SPACING),
                modifier = Modifier.padding(bottom = ACTIONS_BOTTOM_GAP),
            ) {
                actions.forEach { action -> SpeedDialActionPill(action) }
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(
                    if (expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                onToggle()
            },
            shape = RoundedCornerShape(corner),
            containerColor = container,
            contentColor = content,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = toggleDescription,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

/**
 * One quick action rendered as a single pill (leading icon + label in one
 * rounded container), so the dial reads as a compact stack of pills rather than
 * detached label/button pairs. Colours come from the app palette. Flat on
 * purpose, like every card surface: the scrim already separates the pills from
 * the content, and an elevation shadow scales visibly with the entrance layer
 * only to all but vanish once the animation settles.
 */
@Composable
private fun SpeedDialActionPill(action: SpeedDialAction, modifier: Modifier = Modifier) {
    Surface(
        onClick = action.onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ACTION_ICON_GAP),
            modifier = Modifier.padding(
                start = ACTION_PADDING_START,
                end = ACTION_PADDING_END,
                top = ACTION_PADDING_VERTICAL,
                bottom = ACTION_PADDING_VERTICAL,
            ),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(ACTION_ICON_SIZE),
            )
            Text(text = action.label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * The scrim dimming a screen's content while its [SpeedDialFab] is open. The
 * caller places it as the last child of the content container so it covers
 * everything but the dial itself; tapping anywhere closes the dial.
 */
@Composable
fun SpeedDialScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

/**
 * The dial's spatial spring: a light bounce at a brisk stiffness, the
 * expressive-motion feel for movement and shape (never used on colors).
 */
private fun <T> spatialSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** The toggle's plus rotated into a close cross while the dial is open. */
private const val TOGGLE_ROTATION_DEGREES = 45f

/** Container/content color morph of the toggle; colors never bounce. */
private const val COLOR_MORPH_MILLIS = 200

// The closed corner matches the default FAB shape (shapes.large); the open one
// is half the 56dp container, i.e. a full circle.
private val TOGGLE_CORNER_CLOSED = 16.dp
private val TOGGLE_CORNER_OPEN = 28.dp

private val ACTION_SPACING = 12.dp
private val ACTIONS_BOTTOM_GAP = 16.dp
private val ACTION_ICON_SIZE = 22.dp
private val ACTION_ICON_GAP = 12.dp
private val ACTION_PADDING_START = 20.dp
private val ACTION_PADDING_END = 24.dp
private val ACTION_PADDING_VERTICAL = 14.dp

private const val SCRIM_ALPHA = 0.32f
