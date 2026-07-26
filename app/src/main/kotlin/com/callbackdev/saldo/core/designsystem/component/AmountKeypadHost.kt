package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.callbackdev.saldo.core.common.money.AmountInputEditor

/**
 * Hosts the in-app keypad for the active amount field (ADR 31). Sits at the
 * bottom of the screen, normally inside [EditorBottomBar] so the save button
 * stays above it and the screen content keeps the right inset.
 *
 * [onHide] closes the panel; pass null on surfaces where the keypad is always
 * on screen (dialogs, sheets) and there is nothing to close.
 */
@Composable
fun AmountKeypadHost(
    target: AmountTarget?,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val symbols = rememberAmountSymbols()
    val motionEnabled = rememberMotionEnabled()
    // The panel has to keep drawing its last target while it slides out.
    val lastTarget = remember { mutableStateOf<AmountTarget?>(null) }
    if (target != null) lastTarget.value = target
    val rendered = target ?: lastTarget.value
    AnimatedVisibility(
        visible = target != null,
        enter = if (motionEnabled) slideInVertically { it } + fadeIn() else EnterTransition.None,
        exit = if (motionEnabled) slideOutVertically { it } + fadeOut() else ExitTransition.None,
        modifier = modifier,
    ) {
        val active = rendered ?: return@AnimatedVisibility
        AmountKeypad(
            onKey = { key ->
                active.onValueChange(
                    AmountInputEditor.apply(
                        current = active.value,
                        key = key,
                        fractionDigits = active.fractionDigits,
                        allowNegative = active.allowNegative,
                        decimalSeparator = symbols.decimal,
                    ),
                )
            },
            decimalSeparator = symbols.decimal,
            showDecimalSeparator = active.fractionDigits > 0,
            onHide = onHide,
            compact = compact,
        )
    }
}
