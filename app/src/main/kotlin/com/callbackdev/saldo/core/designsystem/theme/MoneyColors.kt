package com.callbackdev.saldo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's single source of truth for coloring money. Every amount on screen
 * picks its color from one of these roles, never from the color scheme directly:
 *
 * - [income]: money coming in (incomes, positive nets).
 * - [expense]: money going out. Deliberately neutral: expenses are the bulk of
 *   a ledger and coloring them all would shout; the minus sign and the leading
 *   icon carry the distinction (also for color-blind users).
 * - [neutral]: money that is neither gain nor loss (transfers, adjustments).
 * - [negative]: balances below zero, a warning rather than a flow direction.
 */
@Immutable
data class MoneyColors(
    val income: Color,
    val expense: Color,
    val neutral: Color,
    val negative: Color,
)

internal fun moneyColors(colorScheme: ColorScheme): MoneyColors = MoneyColors(
    income = colorScheme.tertiary,
    expense = colorScheme.onSurface,
    neutral = colorScheme.onSurfaceVariant,
    negative = colorScheme.error,
)

internal val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(
        income = Color.Unspecified,
        expense = Color.Unspecified,
        neutral = Color.Unspecified,
        negative = Color.Unspecified,
    )
}

/** Access point: `MaterialTheme.moneyColors.income`. */
val MaterialTheme.moneyColors: MoneyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMoneyColors.current
