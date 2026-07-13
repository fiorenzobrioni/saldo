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
 * - [warning]: spending approaching a cap (budget between its thresholds).
 *   Amber sits between the calm default and [negative]; M3 has no warning
 *   role, so the two variants are fixed per theme for contrast on surfaces.
 */
@Immutable
data class MoneyColors(
    val income: Color,
    val expense: Color,
    val neutral: Color,
    val negative: Color,
    val warning: Color,
)

@Suppress("MagicNumber") // A palette is literal color values by nature.
private val WarningOnLight = Color(0xFF9A6700)

@Suppress("MagicNumber") // A palette is literal color values by nature.
private val WarningOnDark = Color(0xFFFFB74D)

internal fun moneyColors(colorScheme: ColorScheme, darkTheme: Boolean): MoneyColors = MoneyColors(
    income = colorScheme.tertiary,
    expense = colorScheme.onSurface,
    neutral = colorScheme.onSurfaceVariant,
    negative = colorScheme.error,
    warning = if (darkTheme) WarningOnDark else WarningOnLight,
)

internal val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(
        income = Color.Unspecified,
        expense = Color.Unspecified,
        neutral = Color.Unspecified,
        negative = Color.Unspecified,
        warning = Color.Unspecified,
    )
}

/** Access point: `MaterialTheme.moneyColors.income`. */
val MaterialTheme.moneyColors: MoneyColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMoneyColors.current
