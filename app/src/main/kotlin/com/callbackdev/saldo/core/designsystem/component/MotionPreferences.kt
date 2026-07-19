package com.callbackdev.saldo.core.designsystem.component

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Whether decorative animations should play. False when the user disabled or
 * scaled animations to zero at system level (accessibility "remove
 * animations", developer options): callers snap to the final state instead of
 * animating. Read once per composition site; a mid-session change applies on
 * the next screen entry, which is how the system setting behaves elsewhere.
 */
@Composable
fun rememberMotionEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }
