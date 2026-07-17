package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.callbackdev.saldo.core.domain.usecase.SafeToSpend
import java.util.Currency
import kotlin.math.roundToInt

/**
 * The proactive hero figure, right under the balance: what can still be spent
 * today while staying on the monthly plan (budget minus spend, pending and
 * upcoming recurring charges, spread over the days left). Turns alarming when
 * the plan is blown, with an explicit icon and wording, never color alone.
 * Rendered only when an overall budget exists. Tapping the card expands the
 * math behind the figure (the only composite number of the app whose formula
 * is not visible anywhere else); the budgets screen stays one tap away via
 * the link inside the breakdown.
 */
@Composable
internal fun SafeToSpendCard(
    safeToSpend: SafeToSpend,
    currency: Currency,
    onManageBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val over = safeToSpend.remaining.signum() < 0
    var expanded by rememberSaveable { mutableStateOf(false) }
    val contentColor = if (over) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (over) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(
                    horizontal = SaldoDimens.cardPaddingLarge,
                    vertical = SaldoDimens.cardPaddingVertical,
                ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.Payments,
                title = stringResource(R.string.dashboard_sts_title),
                iconTint = if (over) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trailingContent = {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f,
                        label = "stsChevron",
                    )
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.dashboard_sts_collapse else R.string.dashboard_sts_expand,
                        ),
                        tint = contentColor,
                        modifier = Modifier.rotate(rotation),
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            when {
                over -> SafeToSpendOverContent(safeToSpend, currency)
                safeToSpend.perDay == null -> Text(
                    text = stringResource(R.string.dashboard_sts_exhausted),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                else -> SafeToSpendContent(safeToSpend, currency)
            }
            if (expanded) {
                SafeToSpendBreakdown(
                    safeToSpend = safeToSpend,
                    currency = currency,
                    contentColor = contentColor,
                    onManageBudgets = onManageBudgets,
                )
            }
        }
    }
}

/**
 * The math behind the safe-to-spend figure, one row per leg: the month's
 * budget minus what is already spent, committed (pending confirmations) and
 * still due (fixed recurring charges before month end). Legs at zero are
 * omitted; the remainder row closes the sum. Ends with the link to the
 * budgets screen, which the card's tap no longer opens directly.
 */
@Composable
private fun SafeToSpendBreakdown(
    safeToSpend: SafeToSpend,
    currency: Currency,
    contentColor: Color,
    onManageBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = contentColor.copy(alpha = DIVIDER_ALPHA))
        Spacer(Modifier.height(12.dp))
        BreakdownRow(
            label = stringResource(R.string.dashboard_sts_detail_budget),
            amount = MoneyFormatter.format(safeToSpend.budget, currency),
            contentColor = contentColor,
        )
        BreakdownRow(
            label = stringResource(R.string.dashboard_sts_detail_spent),
            amount = MoneyFormatter.formatSigned(safeToSpend.spent.negate(), currency),
            contentColor = contentColor,
        )
        if (safeToSpend.pendingCommitted.signum() > 0) {
            BreakdownRow(
                label = stringResource(R.string.dashboard_sts_detail_pending),
                amount = MoneyFormatter.formatSigned(safeToSpend.pendingCommitted.negate(), currency),
                contentColor = contentColor,
            )
        }
        if (safeToSpend.upcomingRecurring.signum() > 0) {
            BreakdownRow(
                label = stringResource(R.string.dashboard_sts_detail_upcoming),
                amount = MoneyFormatter.formatSigned(safeToSpend.upcomingRecurring.negate(), currency),
                contentColor = contentColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = contentColor.copy(alpha = DIVIDER_ALPHA))
        Spacer(Modifier.height(6.dp))
        BreakdownRow(
            label = stringResource(R.string.dashboard_sts_detail_remaining),
            amount = MoneyFormatter.format(safeToSpend.remaining, currency),
            contentColor = contentColor,
            emphasized = true,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onManageBudgets,
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.dashboard_sts_manage_budgets))
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasized) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) contentColor else contentColor.copy(alpha = LABEL_ALPHA),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = if (emphasized) {
                MaterialTheme.typography.bodyMedium.tabularNumbers()
            } else {
                MaterialTheme.typography.bodySmall.tabularNumbers()
            },
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun SafeToSpendContent(
    safeToSpend: SafeToSpend,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = MoneyFormatter.format(checkNotNull(safeToSpend.perDay), currency),
            style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = HERO_MONEY_MIN, maxFontSize = HERO_MONEY_MAX),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = pluralStringResource(
                R.plurals.dashboard_sts_month_left,
                safeToSpend.daysLeft,
                MoneyFormatter.format(safeToSpend.remaining, currency),
                safeToSpend.daysLeft,
            ),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val committed = safeToSpend.upcomingRecurring.add(safeToSpend.pendingCommitted)
        if (committed.signum() > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.dashboard_sts_upcoming,
                    MoneyFormatter.format(committed, currency),
                ),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SafeToSpendOverContent(
    safeToSpend: SafeToSpend,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.budgets_overspent_by,
                    MoneyFormatter.format(safeToSpend.remaining.abs(), currency),
                ),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = COMPACT_MONEY_MIN, maxFontSize = COMPACT_MONEY_MAX),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.dashboard_sts_over_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Dashboard summary of the month's budgets: the overall budget with its
 * remaining amount and bar, then the category budgets closest to their cap.
 * With no budgets in the primary currency it shows an invitation to create
 * one, mirroring the recurring card; the whole card navigates to the budgets
 * screen.
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
            DashboardCardHeader(
                icon = Icons.Outlined.Savings,
                title = stringResource(R.string.dashboard_budget_title),
            )
            if (budgets.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_budget_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = COMPACT_MONEY_MIN, maxFontSize = COMPACT_MONEY_MAX),
                    modifier = Modifier.weight(1f),
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
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = COMPACT_MONEY_MIN, maxFontSize = COMPACT_MONEY_MAX),
                modifier = Modifier.fillMaxWidth(),
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
private const val CHEVRON_EXPANDED_DEGREES = 180f
private const val DIVIDER_ALPHA = 0.25f
private const val LABEL_ALPHA = 0.75f
