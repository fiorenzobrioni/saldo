@file:Suppress("TooManyFunctions") // A collection of recap page composables.

package com.callbackdev.saldo.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.MonthlyRecap
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * The recap's story pages. Each page is one full-screen thought, centered and
 * merged into a single TalkBack node; amounts always carry explicit signs or
 * labels, never color alone.
 */

/** Localized month title in standalone casing, e.g. "Giugno 2026". */
@Composable
internal fun recapMonthTitle(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(month, locale) { recapMonthTitle(month, locale) }
}

internal fun recapMonthTitle(month: YearMonth, locale: Locale): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy")
    return month.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** Shared column scaffold of one page: centered, padded, semantics-merged. */
@Composable
private fun RecapPage(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_PADDING.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun PageOverline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PageHeroAmount(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.tabularNumbers(),
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** Page 1: the month, its net result and how many movements were tracked. */
@Composable
internal fun RecapHeroPage(recap: MonthlyRecap, modifier: Modifier = Modifier) {
    RecapPage(modifier) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(HERO_ICON.dp),
        )
        Spacer(Modifier.height(16.dp))
        PageOverline(stringResource(R.string.recap_title, recapMonthTitle(recap.month)))
        Spacer(Modifier.height(8.dp))
        PageHeroAmount(
            text = MoneyFormatter.formatSigned(recap.net, recap.currency),
            color = if (recap.net.signum() < 0) {
                MaterialTheme.moneyColors.expense
            } else {
                MaterialTheme.moneyColors.income
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = pluralStringResource(
                R.plurals.recap_movement_count,
                recap.movementCount,
                recap.movementCount,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Page 2: total spend and the comparison against the previous month. */
@Composable
internal fun RecapSpendingPage(recap: MonthlyRecap, modifier: Modifier = Modifier) {
    RecapPage(modifier) {
        PageOverline(stringResource(R.string.recap_expense_title))
        Spacer(Modifier.height(8.dp))
        PageHeroAmount(
            text = MoneyFormatter.format(recap.expenseTotal, recap.currency),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.recap_daily_average,
                MoneyFormatter.format(recap.dailyAverageSpend, recap.currency),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val previous = recap.previousExpenseTotal
        if (previous != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = spendingDeltaLine(recap, previous),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun spendingDeltaLine(recap: MonthlyRecap, previous: BigDecimal): String {
    val previousMonthName = recapMonthTitle(recap.month.minusMonths(1))
    val delta = recap.expenseTotal.subtract(previous)
    return when {
        delta.signum() > 0 -> stringResource(
            R.string.recap_expense_vs_previous_more,
            MoneyFormatter.format(delta, recap.currency),
            previousMonthName,
        )
        delta.signum() < 0 -> stringResource(
            R.string.recap_expense_vs_previous_less,
            MoneyFormatter.format(delta.negate(), recap.currency),
            previousMonthName,
        )
        else -> stringResource(R.string.recap_expense_vs_previous_same, previousMonthName)
    }
}

/** Page 3: top categories with proportional bars. */
@Composable
internal fun RecapTopCategoriesPage(
    recap: MonthlyRecap,
    categoryById: Map<Long, Category>,
    modifier: Modifier = Modifier,
) {
    RecapPage(modifier) {
        PageOverline(stringResource(R.string.recap_top_categories_title))
        Spacer(Modifier.height(24.dp))
        val topFraction = recap.topCategories.maxOfOrNull { it.fraction } ?: 1f
        recap.topCategories.forEach { share ->
            val category = share.categoryId?.let { categoryById[it] }
            RecapCategoryBar(
                name = category?.name ?: stringResource(R.string.transaction_uncategorized),
                colorRgb = category?.color,
                iconKey = category?.icon,
                amountLabel = MoneyFormatter.format(share.amount, recap.currency),
                percent = share.percent,
                barFraction = if (topFraction > 0f) share.fraction / topFraction else 0f,
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RecapCategoryBar(
    name: String,
    colorRgb: Int?,
    iconKey: String?,
    amountLabel: String,
    percent: Int,
    barFraction: Float,
    modifier: Modifier = Modifier,
) {
    val color = CategoryVisuals.color(colorRgb)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(CATEGORY_AVATAR.dp)
                    .clip(AvatarShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryVisuals.icon(iconKey),
                    contentDescription = null,
                    tint = contentColorOn(color),
                    modifier = Modifier.size(CATEGORY_ICON.dp),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
            )
            Text(
                text = stringResource(R.string.recap_category_amount_percent, amountLabel, percent),
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Bars are relative to the biggest category; the percent text carries
        // the exact share, so nothing depends on bar length alone.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                    .height(BAR_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Page 4: the records, biggest expense and busiest day. */
@Composable
internal fun RecapRecordsPage(
    recap: MonthlyRecap,
    categoryById: Map<Long, Category>,
    modifier: Modifier = Modifier,
) {
    RecapPage(modifier) {
        PageOverline(stringResource(R.string.recap_records_title))
        Spacer(Modifier.height(24.dp))
        recap.biggestExpense?.let { expense ->
            RecapRecordBlock(
                label = stringResource(R.string.recap_biggest_expense_label),
                value = MoneyFormatter.format(expense.amount, recap.currency),
                detail = listOfNotNull(
                    expense.description?.takeIf { it.isNotBlank() }
                        ?: expense.categoryId?.let { categoryById[it]?.name },
                    recapDayLabel(expense.date),
                ).joinToString(" - "),
            )
        }
        if (recap.biggestExpense != null && recap.busiestDay != null) {
            Spacer(Modifier.height(32.dp))
        }
        recap.busiestDay?.let { day ->
            RecapRecordBlock(
                label = stringResource(R.string.recap_busiest_day_label),
                value = recapDayLabel(day.date),
                detail = pluralStringResource(
                    R.plurals.recap_busiest_day_value,
                    day.count,
                    day.count,
                    MoneyFormatter.format(day.spend, recap.currency),
                ),
            )
        }
    }
}

/** Full localized day, e.g. "domenica 15 giugno" / "Sunday, June 15". */
@Composable
private fun recapDayLabel(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}

@Composable
private fun RecapRecordBlock(label: String, value: String, detail: String?) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    if (!detail.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Page 5: income vs expenses with proportional bars and the savings rate. */
@Composable
internal fun RecapIncomeExpensePage(recap: MonthlyRecap, modifier: Modifier = Modifier) {
    RecapPage(modifier) {
        PageOverline(stringResource(R.string.recap_income_expense_title))
        Spacer(Modifier.height(24.dp))
        val top = maxOf(recap.incomeTotal, recap.expenseTotal)
        RecapFlowBar(
            label = stringResource(R.string.recap_income_label),
            amountLabel = MoneyFormatter.formatSigned(recap.incomeTotal, recap.currency),
            color = MaterialTheme.moneyColors.income,
            fraction = top.proportionOf(recap.incomeTotal),
        )
        Spacer(Modifier.height(16.dp))
        RecapFlowBar(
            label = stringResource(R.string.recap_expense_label),
            amountLabel = MoneyFormatter.formatSigned(recap.expenseTotal.negate(), recap.currency),
            color = MaterialTheme.moneyColors.expense,
            fraction = top.proportionOf(recap.expenseTotal),
        )
        recap.savingsRatePercent?.let { percent ->
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.recap_savings_rate, percent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Display proportion of [value] against this maximum; geometry, not money math. */
private fun BigDecimal.proportionOf(value: BigDecimal): Float =
    if (signum() <= 0) 0f else (value.toFloat() / toFloat()).coerceIn(0f, 1f)

@Composable
private fun RecapFlowBar(
    label: String,
    amountLabel: String,
    color: androidx.compose.ui.graphics.Color,
    fraction: Float,
) {
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = amountLabel,
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(BAR_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Page 6: what subscriptions and recurring charges actually cost. */
@Composable
internal fun RecapRecurringPage(recap: MonthlyRecap, modifier: Modifier = Modifier) {
    RecapPage(modifier) {
        PageOverline(stringResource(R.string.recap_recurring_title))
        Spacer(Modifier.height(8.dp))
        PageHeroAmount(
            text = MoneyFormatter.format(recap.recurringSpend, recap.currency),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recap_recurring_caption),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Page 7: brand closing with the privacy line; the share CTA lives below the pager. */
@Composable
internal fun RecapClosingPage(recap: MonthlyRecap, modifier: Modifier = Modifier) {
    RecapPage(modifier) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(HERO_ICON.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.recap_closing_title, recapMonthTitle(recap.month)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recap_closing_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val PAGE_PADDING = 32
private const val HERO_ICON = 48
private const val CATEGORY_AVATAR = 34
private const val CATEGORY_ICON = 20
private const val BAR_HEIGHT = 10
