package com.callbackdev.saldo.feature.budgets

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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.indicatorColor
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.BudgetProgress
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlin.math.roundToInt

/**
 * Budget management: the overall monthly budget as a hero card (or a call to
 * action to set one), then the category budgets sorted by how close each is
 * to its cap. Rows navigate to the editor; creation goes through the FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_title)) },
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
            if (!uiState.isLoading) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.budgets_add)) },
                    icon = {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                    },
                    onClick = { onNavigateToEditor(null) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> ListSkeleton(modifier = Modifier.padding(innerPadding))
            uiState.isEmpty -> BudgetsEmptyState(
                onCreate = { onNavigateToEditor(null) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> BudgetsContent(
                uiState = uiState,
                onEdit = { onNavigateToEditor(it.budget.id) },
                onCreate = { onNavigateToEditor(null) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun BudgetsContent(
    uiState: BudgetsUiState,
    onEdit: (BudgetProgress) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item(key = "overall") {
            val overall = uiState.overall
            if (overall != null) {
                OverallBudgetCard(
                    progress = overall,
                    month = uiState.month,
                    onClick = { onEdit(overall) },
                )
            } else {
                SetOverallBudgetCard(onClick = onCreate)
            }
        }
        if (uiState.categoryBudgets.isNotEmpty()) {
            item(key = "categories-header") {
                Text(
                    text = stringResource(R.string.budgets_section_categories),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(uiState.categoryBudgets, key = { it.budget.id }) { progress ->
                CategoryBudgetCard(
                    progress = progress,
                    currency = uiState.currency,
                    onClick = { onEdit(progress) },
                )
            }
        }
    }
}

/** The hero: how much of the month's overall budget is left. */
@Composable
private fun OverallBudgetCard(
    progress: BudgetProgress,
    month: YearMonth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = progress.budget.currency
    val remaining = progress.budget.amount.subtract(progress.spent)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingLarge,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.budgets_overall_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatMonth(month),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (progress.level == BudgetLevel.OVER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.budgets_overspent_by,
                            MoneyFormatter.format(remaining.abs(), currency),
                        ),
                        style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
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
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            ThresholdProgressBar(
                fraction = progress.fraction,
                color = progress.level.indicatorColor(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
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
                Text(
                    text = formatPercent(progress.fraction),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    fontWeight = FontWeight.Medium,
                    color = progress.level.indicatorColor(),
                )
            }
        }
    }
}

/** Hero-slot call to action shown while no overall budget exists. */
@Composable
private fun SetOverallBudgetCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingLarge,
            ),
        ) {
            Text(
                text = stringResource(R.string.budgets_overall_cta_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.budgets_overall_cta_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One category budget: avatar, name, progress bar and percentage. */
@Composable
private fun CategoryBudgetCard(
    progress: BudgetProgress,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = progress.category ?: return
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(CategoryVisuals.color(category.color)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryVisuals.icon(category.icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                ThresholdProgressBar(
                    fraction = progress.fraction,
                    color = progress.level.indicatorColor(),
                    height = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.budgets_spent_of,
                        MoneyFormatter.format(progress.spent, currency),
                        MoneyFormatter.format(progress.budget.amount, currency),
                    ),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (progress.level == BudgetLevel.OVER) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = stringResource(R.string.budgets_over_a11y),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = formatPercent(progress.fraction),
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    color = progress.level.indicatorColor(),
                )
            }
        }
    }
}

@Composable
private fun BudgetsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Savings,
        title = stringResource(R.string.budgets_empty_title),
        body = stringResource(R.string.budgets_empty_body),
        modifier = modifier,
        actionLabel = stringResource(R.string.budgets_empty_cta),
        onAction = onCreate,
    )
}

@Composable
private fun formatMonth(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
internal fun formatPercent(fraction: Float): String =
    stringResource(R.string.stats_percent, (fraction * 100).roundToInt())
