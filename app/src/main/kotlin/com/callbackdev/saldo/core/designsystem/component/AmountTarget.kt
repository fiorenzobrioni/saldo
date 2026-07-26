package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * The amount field the keypad is typing into: its current raw text, the rules
 * that text obeys and where the result goes. Screens build one of these for the
 * field the user tapped and pass it to [AmountKeypadHost]; a null target there
 * means the keypad is closed.
 */
@Immutable
data class AmountTarget(
    val value: String,
    val fractionDigits: Int,
    val allowNegative: Boolean,
    val onValueChange: (String) -> Unit,
)
