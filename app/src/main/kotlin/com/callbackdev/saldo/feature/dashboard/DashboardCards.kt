package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency

/** Capitalized localized date, e.g. "Martedì 8 luglio". */
@Composable
internal fun DashboardDate(date: LocalDate, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val text = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Hero card: the total balance with an expandable per-account breakdown. */
@Composable
internal fun BalanceCard(
    totalBalance: BigDecimal,
    currency: Currency,
    accounts: List<AccountWithBalance>,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_balance_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.format(totalBalance, currency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (totalBalance.signum() < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { expanded = !expanded },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_accounts_count,
                                accounts.size,
                                accounts.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            contentDescription = stringResource(
                                if (expanded) {
                                    R.string.dashboard_accounts_collapse
                                } else {
                                    R.string.dashboard_accounts_expand
                                },
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        accounts.forEach { item ->
                            AccountBreakdownRow(item = item)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(
                onClick = onManageAccounts,
                color = Color.Transparent,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_manage_accounts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountBreakdownRow(item: AccountWithBalance, modifier: Modifier = Modifier) {
    val account = item.account
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AccountVisuals.color(account.color)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AccountVisuals.icon(account.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = MoneyFormatter.format(item.balance, account.currency),
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.balance.signum() < 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * A "Today" / "This month" summary card: expenses, income and net (or balance)
 * as three stats, with an optional month-over-month comparison line.
 */
@Composable
internal fun PeriodCard(
    title: String,
    netLabel: String,
    flow: PeriodFlow,
    currency: Currency,
    modifier: Modifier = Modifier,
    comparison: BigDecimal? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = stringResource(R.string.dashboard_stat_expenses),
                    value = MoneyFormatter.format(flow.spend, currency),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = stringResource(R.string.dashboard_stat_incomes),
                    value = MoneyFormatter.formatSigned(flow.income, currency),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = netLabel,
                    value = MoneyFormatter.formatSigned(flow.net, currency),
                    color = netColor(flow.net),
                    modifier = Modifier.weight(1f),
                )
            }
            if (comparison != null) {
                Spacer(Modifier.height(16.dp))
                ComparisonRow(delta = comparison, currency = currency)
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComparisonRow(
    delta: BigDecimal,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val spentMore = delta.signum() > 0
    val spentLess = delta.signum() < 0
    val icon = if (spentMore) {
        Icons.AutoMirrored.Outlined.TrendingUp
    } else {
        Icons.AutoMirrored.Outlined.TrendingDown
    }
    val tint = when {
        spentMore -> MaterialTheme.colorScheme.onSurfaceVariant
        spentLess -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        spentMore -> stringResource(
            R.string.dashboard_month_more,
            MoneyFormatter.format(delta.abs(), currency),
        )

        spentLess -> stringResource(
            R.string.dashboard_month_less,
            MoneyFormatter.format(delta.abs(), currency),
        )

        else -> stringResource(R.string.dashboard_month_same)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Teaser card for the subscriptions view, which lands with Phase 6 (recurrences). */
@Composable
internal fun SubscriptionsTeaser(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Subscriptions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_subscriptions_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.dashboard_subscriptions_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun netColor(value: BigDecimal): Color = when {
    value.signum() > 0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}
