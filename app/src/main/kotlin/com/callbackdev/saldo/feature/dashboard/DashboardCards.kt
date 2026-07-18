@file:Suppress("TooManyFunctions") // A collection of dashboard card composables.

package com.callbackdev.saldo.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.TransactionRowContent
import com.callbackdev.saldo.feature.transactions.compactDayLabel
import com.callbackdev.saldo.feature.transactions.localDate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency

/**
 * A warm, time-of-day greeting as the screen's title. The [band] and [roll] are
 * fixed once per app-open in the ViewModel, so the message is stable across
 * recomposition and rotation and only changes on a fresh open. Messages are
 * written to fit one line at the default font scale; a second line is allowed
 * so larger accessibility font sizes never truncate the text.
 */
@Composable
internal fun DashboardHeader(band: GreetingBand, roll: Float, modifier: Modifier = Modifier) {
    val greetings = stringArrayResource(band.greetingsArrayRes())
    val greeting = greetings.getOrElse((roll * greetings.size).toInt()) { greetings.firstOrNull().orEmpty() }
    Text(
        text = greeting,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The uniform card header: leading icon in the primary tint, [title] in
 * titleMedium and an optional trailing slot (the balance card's date). Every
 * dashboard card opens its detail on tap, so no chevron: the convention is
 * carried by the cards themselves, not per-card affordances.
 */
@Composable
internal fun DashboardCardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke()
    }
}

@androidx.annotation.ArrayRes
private fun GreetingBand.greetingsArrayRes(): Int = when (this) {
    GreetingBand.NIGHT -> R.array.dashboard_greetings_night
    GreetingBand.MORNING -> R.array.dashboard_greetings_morning
    GreetingBand.AFTERNOON -> R.array.dashboard_greetings_afternoon
    GreetingBand.EVENING -> R.array.dashboard_greetings_evening
}

/**
 * Full localized weekday date in the locale's own casing: Italian dates are
 * lowercase ("venerdì 10 luglio"), English weekday/month names are proper
 * nouns ("Friday, July 10"); see [withLocaleDateCasing].
 */
@Composable
private fun fullWeekdayDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}

/**
 * Hero card: the total balance with the per-account breakdown always in view.
 * The whole card is tappable and opens account management, with
 * [R.string.dashboard_manage_accounts] as the spoken affordance. The higher
 * tonal color and the larger shape single it out as the screen's primary
 * card, keeping the whole dashboard flat.
 */
@Composable
internal fun BalanceCard(
    totalBalance: BigDecimal,
    currency: Currency,
    accounts: List<AccountWithBalance>,
    date: LocalDate,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manageAccountsLabel = stringResource(R.string.dashboard_manage_accounts)
    SaldoCard(
        onClick = onManageAccounts,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = manageAccountsLabel, action = null) },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = stringResource(R.string.dashboard_balance_total),
                trailingContent = {
                    Text(
                        text = fullWeekdayDate(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.format(totalBalance, currency),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = if (totalBalance.signum() < 0) {
                    MaterialTheme.moneyColors.negative
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = HERO_MONEY_MIN, maxFontSize = HERO_MONEY_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                accounts.forEach { item ->
                    AccountBreakdownRow(item = item, primaryCurrency = currency)
                }
            }
        }
    }
}

/**
 * One line of the balance breakdown: avatar, name, then (only when relevant)
 * small markers explaining why the row does not feed the headline total, and
 * the balance. An account that does not contribute to the total (its flag is
 * off or it is in a non-primary currency) has its balance muted, so the eye
 * sees at a glance which rows are not part of the big number.
 */
@Composable
private fun AccountBreakdownRow(
    item: AccountWithBalance,
    primaryCurrency: Currency,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    val color = AccountVisuals.color(account.color)
    val nonPrimaryCurrency = account.currency != primaryCurrency
    val contributesToTotal = account.isIncludedInTotal && !nonPrimaryCurrency
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AvatarShape)
                .background(color.copy(alpha = AVATAR_TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AccountVisuals.icon(account.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        AccountBreakdownMarkers(
            currency = account.currency,
            showCurrencyCode = nonPrimaryCurrency,
            excludedFromTotal = !account.isIncludedInTotal && !nonPrimaryCurrency,
            excludedFromBudget = !account.isIncludedInBudget,
        )
        Text(
            text = MoneyFormatter.format(item.balance, account.currency),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            color = when {
                !contributesToTotal -> MaterialTheme.colorScheme.onSurfaceVariant
                item.balance.signum() < 0 -> MaterialTheme.moneyColors.negative
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * The negation-only markers shown between an account's name and its balance.
 * A non-primary currency shows its ISO code (which also explains why the row is
 * out of the total, so the total-excluded icon is suppressed in that case); a
 * flag-excluded account shows a "not in total" icon; a budget-excluded account
 * shows a distinct "not in budget" icon. Nothing is drawn when the account is
 * fully included.
 */
@Composable
private fun AccountBreakdownMarkers(
    currency: Currency,
    showCurrencyCode: Boolean,
    excludedFromTotal: Boolean,
    excludedFromBudget: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showCurrencyCode && !excludedFromTotal && !excludedFromBudget) return
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showCurrencyCode -> CurrencyMarker(currency)
            excludedFromTotal -> MarkerIcon(
                icon = Icons.Outlined.RemoveCircleOutline,
                contentDescription = stringResource(R.string.accounts_excluded_from_total),
            )
        }
        if (excludedFromBudget) {
            MarkerIcon(
                icon = Icons.Outlined.MoneyOff,
                contentDescription = stringResource(R.string.accounts_excluded_from_budget),
            )
        }
    }
}

@Composable
private fun MarkerIcon(icon: ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}

/** The ISO code of a non-primary currency, as a subtle pill. */
@Composable
private fun CurrencyMarker(currency: Currency) {
    val description = stringResource(R.string.dashboard_account_other_currency, currency.currencyCode)
    Text(
        text = currency.currencyCode,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
    )
}

/**
 * The "Today" and current-month cards, side by side and equal height. Each
 * opens the filtered-transactions drill-down for its own window: the natural
 * question behind the aggregate is "which movements make it up".
 */
@Composable
internal fun PeriodCardsRow(
    date: LocalDate,
    today: PeriodTotals,
    month: PeriodTotals,
    currency: Currency,
    onTodayClick: () -> Unit,
    onMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthTitle = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("LLLL", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        PeriodCompactCard(
            title = stringResource(R.string.dashboard_today),
            icon = Icons.Outlined.Today,
            flow = today,
            currency = currency,
            onClick = onTodayClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        PeriodCompactCard(
            title = monthTitle,
            icon = Icons.Outlined.CalendarMonth,
            flow = month,
            currency = currency,
            onClick = onMonthClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PeriodCompactCard(
    title: String,
    icon: ImageVector,
    flow: PeriodTotals,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SaldoDimens.cardPadding,
                    vertical = SaldoDimens.cardPaddingVertical,
                ),
        ) {
            DashboardCardHeader(icon = icon, title = title)
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.formatSigned(flow.net, currency),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = netColor(flow.net),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = COMPACT_MONEY_MIN, maxFontSize = COMPACT_MONEY_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            StatLine(
                label = stringResource(R.string.dashboard_stat_expenses),
                value = MoneyFormatter.formatSigned(flow.spend, currency),
            )
            Spacer(Modifier.height(2.dp))
            StatLine(
                label = stringResource(R.string.dashboard_stat_incomes),
                value = MoneyFormatter.formatSigned(flow.income, currency),
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Takes the leftover width so the amount is pushed to the row's end:
            // the two stat rows share a right edge and their tabular figures
            // stay columnar.
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Standalone reference line: how much had been spent by this day last month. */
@Composable
internal fun MonthComparisonRow(
    previousSpend: BigDecimal,
    spentMore: Boolean,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (spentMore) {
                Icons.AutoMirrored.Outlined.TrendingUp
            } else {
                Icons.AutoMirrored.Outlined.TrendingDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(
                R.string.dashboard_month_comparison,
                MoneyFormatter.format(previousSpend, currency),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Attention card shown when recurring movements await confirmation. */
@Composable
internal fun PendingConfirmationCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.dashboard_pending_title, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.dashboard_pending_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Dashboard card for credit card statements waiting to be paid (confirm mode):
 * the amount owed and a tap-through to the accounts screen, where the statement
 * is settled. Auto-post cards never appear here (they are charged on their own).
 */
@Composable
internal fun StatementDueCard(
    statements: List<com.callbackdev.saldo.core.domain.usecase.DueStatement>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (statements.isEmpty()) return
    val single = statements.singleOrNull()
    val total = statements.fold(java.math.BigDecimal.ZERO) { acc, statement -> acc.add(statement.amount) }
    val currency = statements.first().currency
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = if (single != null) {
                        stringResource(R.string.dashboard_statement_title_single, single.cardName)
                    } else {
                        pluralStringResource(R.plurals.dashboard_statement_title, statements.size, statements.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_statement_subtitle,
                        MoneyFormatter.format(total, currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Dashboard card for recurring transactions: monthly expense and income totals
 * side by side, plus the next upcoming charge or credit across both types.
 */
@Composable
internal fun RecurringCard(
    summary: RecurringSummary,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            DashboardCardHeader(
                icon = Icons.Outlined.EventRepeat,
                title = stringResource(R.string.dashboard_recurring_title),
            )
            if (summary.hasRules) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    RecurringMetric(
                        label = stringResource(R.string.dashboard_recurring_expenses_label),
                        value = MoneyFormatter.formatSigned(summary.monthlyExpenses.negate(), currency),
                        color = if (summary.monthlyExpenses.signum() > 0) {
                            MaterialTheme.moneyColors.negative
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RecurringMetric(
                        label = stringResource(R.string.dashboard_recurring_incomes_label),
                        value = MoneyFormatter.formatSigned(summary.monthlyIncomes, currency),
                        color = if (summary.monthlyIncomes.signum() > 0) {
                            MaterialTheme.moneyColors.income
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (summary.monthlyTransfersToSavings.signum() > 0) {
                    Spacer(Modifier.height(8.dp))
                    RecurringSavingsLine(amount = summary.monthlyTransfersToSavings, currency = currency)
                }
                summary.next?.let { next ->
                    Spacer(Modifier.height(8.dp))
                    NextRecurringEventLine(next = next)
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_recurring_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecurringMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Planned-savings line: the monthly-equivalent of recurring transfers into savings. */
@Composable
private fun RecurringSavingsLine(amount: BigDecimal, currency: Currency, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.dashboard_recurring_savings_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.moneyColors.income,
        )
    }
}

@Composable
private fun NextRecurringEventLine(next: NextRecurringEvent, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(next.date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
        next.date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
    Text(
        text = stringResource(
            R.string.dashboard_recurring_next,
            next.name,
            MoneyFormatter.formatSigned(next.amount, next.currency),
            dateText,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Recent movements as a single grouped card with flat, tappable rows. */
@Composable
internal fun RecentMovementsCard(
    items: List<TransactionListItem>,
    onItemClick: (Long) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Surface(
                    onClick = { onItemClick(item.id) },
                    color = Color.Transparent,
                ) {
                    TransactionRowContent(
                        item = item,
                        modifier = Modifier.padding(
                            horizontal = SaldoDimens.rowPaddingHorizontal,
                            vertical = SaldoDimens.rowPaddingVertical,
                        ),
                        dateLabel = compactDayLabel(item.transaction.localDate, today),
                    )
                }
            }
        }
    }
}

@Composable
private fun netColor(value: BigDecimal): Color = when {
    value.signum() > 0 -> MaterialTheme.moneyColors.income
    else -> MaterialTheme.colorScheme.onSurface
}

private const val AVATAR_TINT_ALPHA = 0.16f

// Money figures auto-size within these bounds so large amounts shrink to fit
// instead of truncating, while typical values keep the target size. Hero: the
// balance and safe-to-spend figures on the full-width cards; compact: the
// half-width Today/month and budget figures. Shared by BudgetDashboardCards.
internal val HERO_MONEY_MIN = 20.sp
internal val HERO_MONEY_MAX = 28.sp
internal val COMPACT_MONEY_MIN = 14.sp
internal val COMPACT_MONEY_MAX = 22.sp
