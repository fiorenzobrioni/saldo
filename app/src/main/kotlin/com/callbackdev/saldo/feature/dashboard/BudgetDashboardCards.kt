package com.callbackdev.saldo.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.indicatorColor
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import kotlin.math.roundToInt

/**
 * Dashboard summary of the month's budgets: the overall budget with its
 * remaining amount and bar, then the category budgets closest to their cap.
 * Rendered only when at least one budget exists in the primary currency; the
 * whole card navigates to the budgets screen.
 */
@Composable
internal fun BudgetCard(
    budgets: List<BudgetProgress>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overall = budgets.firstOrNull { it.budget.isOverall }
    val categories = budgets.filterNot { it.budget.isOverall }.take(CATEGORY_PREVIEW_COUNT)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_budget_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overall != null) {
                Spacer(Modifier.height(12.dp))
                OverallBudgetSummary(overall)
            }
            categories.forEach { progress ->
                Spacer(Modifier.height(12.dp))
                CategoryBudgetLine(progress)
            }
        }
    }
}

@Composable
private fun OverallBudgetSummary(progress: BudgetProgress, modifier: Modifier = Modifier) {
    val currency = progress.budget.currency
    val remaining = progress.budget.amount.subtract(progress.spent)
    Column(modifier = modifier.fillMaxWidth()) {
        if (progress.level == BudgetLevel.OVER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.budgets_overspent_by,
                        MoneyFormatter.format(remaining.abs(), currency),
                    ),
                    style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                text = stringResource(
                    R.string.budgets_remaining,
                    MoneyFormatter.format(remaining, currency),
                ),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        ThresholdProgressBar(
            fraction = progress.fraction,
            color = progress.level.indicatorColor(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.budgets_spent_of,
                    MoneyFormatter.format(progress.spent, currency),
                    MoneyFormatter.format(progress.budget.amount, currency),
                ),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PercentLabel(progress)
        }
    }
}

@Composable
private fun CategoryBudgetLine(progress: BudgetProgress, modifier: Modifier = Modifier) {
    val category = progress.category ?: return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            PercentLabel(progress)
        }
        Spacer(Modifier.height(6.dp))
        ThresholdProgressBar(
            fraction = progress.fraction,
            color = progress.level.indicatorColor(),
            height = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Percentage with, past the limit, an explicit icon: never color alone. */
@Composable
private fun PercentLabel(progress: BudgetProgress, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (progress.level == BudgetLevel.OVER) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = stringResource(R.string.budgets_over_a11y),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = stringResource(R.string.stats_percent, (progress.fraction * PERCENT).roundToInt()),
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            fontWeight = FontWeight.Medium,
            color = progress.level.indicatorColor(),
        )
    }
}

private const val CATEGORY_PREVIEW_COUNT = 3
private const val PERCENT = 100
