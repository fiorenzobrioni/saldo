package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared enter/exit for form sections that appear and disappear with a
 * selection (movement type, toggles): fade + vertical expand/shrink, snapping
 * to the final state when system animations are off.
 */
@Composable
fun AnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motionEnabled = rememberMotionEnabled()
    AnimatedVisibility(
        visible = visible,
        enter = if (motionEnabled) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (motionEnabled) fadeOut() + shrinkVertically() else ExitTransition.None,
        modifier = modifier,
        content = content,
    )
}
