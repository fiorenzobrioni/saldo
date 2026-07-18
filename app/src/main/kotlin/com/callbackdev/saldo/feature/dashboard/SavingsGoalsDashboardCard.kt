package com.callbackdev.saldo.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import java.math.BigDecimal
import java.util.Currency
import kotlin.math.roundToInt

/** Number of goals previewed on the dashboard card before the "+N more" line. */
private const val PREVIEW_GOALS = 3

/**
 * Dashboard card for savings goals: the total saved across the primary-currency
 * goals with an overall bar, then the goals closest to completion. Tap opens the
 * savings goals screen. Shown only when at least one goal exists (caller guards).
 */
@Composable
internal fun SavingsGoalsCard(
    goals: List<SavingsGoalProgress>,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSaved = goals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.saved.max(BigDecimal.ZERO)) }
    val totalTarget = goals.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.goal.targetAmount) }
    val overallFraction = if (totalTarget.signum() > 0) totalSaved.toFloat() / totalTarget.toFloat() else 0f
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.dashboard_savings_title),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.dashboard_savings_saved_of,
                    MoneyFormatter.format(totalSaved, currency),
                    MoneyFormatter.format(totalTarget, currency),
                ),
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            ThresholdProgressBar(
                fraction = overallFraction,
                color = MaterialTheme.moneyColors.income,
                height = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            val preview = goals.sortedByDescending { it.fraction }.take(PREVIEW_GOALS)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.forEach { GoalPreviewRow(progress = it) }
            }
            val remaining = goals.size - preview.size
            if (remaining > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_savings_more, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoalPreviewRow(progress: SavingsGoalProgress, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = progress.goal.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.stats_percent, (progress.fraction * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.moneyColors.income,
        )
    }
}
