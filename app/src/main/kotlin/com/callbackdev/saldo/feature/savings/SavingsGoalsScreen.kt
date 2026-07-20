@file:Suppress("TooManyFunctions") // One small composable per card/row/section.

package com.callbackdev.saldo.feature.savings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.SavingsGoalProgress
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Savings goals list: a hero total across the primary-currency goals, then one
 * card per goal with its avatar, saved-of-target figure, progress bar and a
 * status line (reached, the monthly suggestion for the target date, or the
 * projected date at the current recurring-transfer rate). Rows open the editor;
 * creation goes through the FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavingsGoalsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.savings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.isEmpty) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.savings_add)) },
                    icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                    onClick = { onNavigateToEditor(null) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> ListSkeleton(modifier = Modifier.padding(innerPadding))
            uiState.isEmpty -> SavingsEmptyState(
                onCreate = { onNavigateToEditor(null) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> SavingsContent(
                uiState = uiState,
                onEdit = { onNavigateToEditor(it.goal.id) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SavingsContent(
    uiState: SavingsGoalsUiState,
    onEdit: (SavingsGoalProgress) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        if (uiState.totalTarget.signum() > 0) {
            item(key = "summary") {
                SavingsSummaryCard(uiState = uiState)
            }
        }
        items(uiState.goals, key = { it.goal.id }) { progress ->
            SavingsGoalCard(progress = progress, onClick = { onEdit(progress) })
        }
    }
}

/** Hero: total saved across the primary-currency goals, against their combined target. */
@Composable
private fun SavingsSummaryCard(uiState: SavingsGoalsUiState, modifier: Modifier = Modifier) {
    val currency = uiState.primaryCurrency
    val fraction = if (uiState.totalTarget.signum() > 0) {
        (uiState.totalSaved.toFloat() / uiState.totalTarget.toFloat())
    } else {
        0f
    }
    SaldoCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingLarge,
            ),
        ) {
            Text(
                text = stringResource(R.string.savings_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = MoneyFormatter.format(uiState.totalSaved, currency),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.moneyColors.income,
            )
            Spacer(Modifier.height(12.dp))
            ThresholdProgressBar(
                fraction = fraction,
                color = MaterialTheme.moneyColors.income,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.savings_saved_of,
                    MoneyFormatter.format(uiState.totalSaved, currency),
                    MoneyFormatter.format(uiState.totalTarget, currency),
                ),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.hasOtherCurrencies) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.savings_other_currencies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One goal: avatar, name, saved-of-target, progress bar, percentage and status line. */
@Composable
private fun SavingsGoalCard(
    progress: SavingsGoalProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = progress.goal
    val currency = goal.currency
    val barColor = if (progress.isReached) MaterialTheme.colorScheme.primary else MaterialTheme.moneyColors.income
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalAvatar(colorKey = goal.color, icon = goal.icon)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPercent(progress.fraction),
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    color = barColor,
                )
            }
            Spacer(Modifier.height(12.dp))
            ThresholdProgressBar(
                fraction = progress.fraction,
                color = barColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.savings_saved_of,
                    MoneyFormatter.format(progress.saved.max(BigDecimal.ZERO), currency),
                    MoneyFormatter.format(goal.targetAmount, currency),
                ),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            GoalStatusLine(progress = progress)
        }
    }
}

/** The one status line under a goal: reached, suggestion, projection, or remaining. */
@Composable
private fun GoalStatusLine(progress: SavingsGoalProgress, modifier: Modifier = Modifier) {
    if (progress.isReached) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.moneyColors.income,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.savings_reached),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.moneyColors.income,
            )
        }
        return
    }
    val currency = progress.goal.currency
    val text = when {
        progress.suggestedMonthly != null && progress.goal.targetDate != null -> stringResource(
            R.string.savings_by_date_suggestion,
            formatFullDate(progress.goal.targetDate),
            MoneyFormatter.format(progress.suggestedMonthly, currency),
        )

        progress.goal.targetDate != null -> stringResource(
            R.string.savings_by_date,
            formatFullDate(progress.goal.targetDate),
        )

        progress.projectedDate != null -> stringResource(
            R.string.savings_projected,
            formatMonthYear(progress.projectedDate),
        )

        else -> stringResource(
            R.string.savings_remaining,
            MoneyFormatter.format(progress.remaining, currency),
        )
    }
    val color = when (progress.onTrack) {
        true -> MaterialTheme.moneyColors.income
        false -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun GoalAvatar(colorKey: Int?, icon: String?, modifier: Modifier = Modifier) {
    val color = CategoryVisuals.color(colorKey)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(AvatarShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryVisuals.icon(icon),
            contentDescription = null,
            tint = contentColorOn(color),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SavingsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Savings,
        title = stringResource(R.string.savings_empty_title),
        body = stringResource(R.string.savings_empty_body),
        modifier = modifier,
        actionLabel = stringResource(R.string.savings_empty_cta),
        onAction = onCreate,
    )
}

@Composable
private fun formatPercent(fraction: Float): String =
    stringResource(R.string.stats_percent, (fraction * 100).roundToInt())

@Composable
private fun formatFullDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}

@Composable
private fun formatMonthYear(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMy")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}
