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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.TransactionRowContent
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
 * nouns ("Friday, July 10"). Stock CLDR data gets this right, but some OEM
 * ICU builds ship day/month names titlecased for standalone contexts, so for
 * Italian the string is normalized to lowercase explicitly.
 */
@Composable
private fun fullWeekdayDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        val formatted = date.format(DateTimeFormatter.ofPattern(pattern, locale))
        if (locale.language == "it") formatted.lowercase(locale) else formatted
    }
}

/**
 * Hero card: the total balance with the per-account breakdown always in view.
 * The whole card is tappable and opens account management; the chevron next to
 * the date is the visual affordance, [R.string.dashboard_manage_accounts] the
 * spoken one. A slight shadow on top of the tonal color singles it out as the
 * screen's primary card; every other card stays flat.
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
    Card(
        onClick = onManageAccounts,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = manageAccountsLabel, action = null) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = HERO_CARD_ELEVATION),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPaddingLarge,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_balance_total),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = fullWeekdayDate(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.format(totalBalance, currency),
                style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = if (totalBalance.signum() < 0) {
                    MaterialTheme.moneyColors.negative
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                accounts.forEach { item -> AccountBreakdownRow(item = item) }
            }
        }
    }
}

@Composable
private fun AccountBreakdownRow(item: AccountWithBalance, modifier: Modifier = Modifier) {
    val account = item.account
    val color = AccountVisuals.color(account.color)
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
        Text(
            text = MoneyFormatter.format(item.balance, account.currency),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            color = if (item.balance.signum() < 0) {
                MaterialTheme.moneyColors.negative
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** The "Today" and current-month cards, side by side and equal height. */
@Composable
internal fun PeriodCardsRow(
    date: LocalDate,
    today: PeriodTotals,
    month: PeriodTotals,
    currency: Currency,
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
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        PeriodCompactCard(
            title = monthTitle,
            icon = Icons.Outlined.CalendarMonth,
            flow = month,
            currency = currency,
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.formatSigned(flow.net, currency),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = netColor(flow.net),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
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
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.EventRepeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.dashboard_recurring_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun NextRecurringEventLine(next: NextRecurringEvent, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(next.date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
        next.date.format(DateTimeFormatter.ofPattern(pattern, locale))
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
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
private val HERO_CARD_ELEVATION = 3.dp
