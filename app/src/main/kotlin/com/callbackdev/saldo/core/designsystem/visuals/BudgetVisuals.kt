package com.callbackdev.saldo.core.designsystem.visuals

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.domain.model.BudgetLevel

/**
 * The single color mapping for budget progress, shared by every screen that
 * draws one. Color is never the only signal: callers always pair it with the
 * percentage text and, past the limit, an explicit icon or wording.
 */
@Composable
@ReadOnlyComposable
fun BudgetLevel.indicatorColor(): Color = when (this) {
    BudgetLevel.UNDER -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARNING -> MaterialTheme.moneyColors.warning
    BudgetLevel.OVER -> MaterialTheme.colorScheme.error
}
