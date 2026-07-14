package com.callbackdev.saldo.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.util.Currency

/** Card shell shared by the statistics sections. */
@Composable
internal fun StatsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Inline note for a card whose period holds no data. */
@Composable
internal fun NoPeriodData(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.stats_no_data_period),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Category shares of the period's spend: the donut (when provided) or an
 * inline total, then one row per category with avatar, amount, percent, bar.
 */
@Composable
internal fun CategorySharesCard(
    slices: List<CategorySlice>,
    total: BigDecimal,
    currency: Currency,
    modifier: Modifier = Modifier,
    chart: (@Composable () -> Unit)? = null,
    onSliceClick: ((CategorySlice) -> Unit)? = null,
) {
    StatsCard(
        title = stringResource(R.string.stats_categories_title),
        modifier = modifier,
    ) {
        if (slices.isEmpty()) {
            NoPeriodData()
            return@StatsCard
        }
        if (chart != null) {
            chart()
        } else {
            Text(
                text = MoneyFormatter.format(total, currency),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            )
            Text(
                text = stringResource(R.string.stats_total_spent_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        slices.forEach { slice ->
            // A null category is the uncategorized bucket: generic glyph,
            // neutral grey, localized label.
            val avatarColor = CategoryVisuals.color(slice.category?.color)
            ShareRow(
                avatar = {
                    StatsAvatar(
                        icon = { size ->
                            Icon(
                                imageVector = CategoryVisuals.icon(slice.category?.icon),
                                contentDescription = null,
                                tint = contentColorOn(avatarColor),
                                modifier = Modifier.size(size),
                            )
                        },
                        color = avatarColor,
                    )
                },
                name = slice.category?.name
                    ?: stringResource(R.string.transaction_uncategorized),
                amount = MoneyFormatter.format(slice.amount, currency),
                percentLabel = stringResource(R.string.stats_percent, slice.percent),
                fraction = slice.fraction,
                barColor = avatarColor,
                onClick = onSliceClick?.let { { it(slice) } },
            )
        }
    }
}

/** Per-account spend of the period, with bars relative to the top spender. */
@Composable
internal fun AccountSpendsCard(
    spends: List<AccountSpend>,
    currency: Currency,
    modifier: Modifier = Modifier,
    onAccountClick: ((AccountSpend) -> Unit)? = null,
) {
    StatsCard(
        title = stringResource(R.string.stats_accounts_title),
        modifier = modifier,
    ) {
        if (spends.isEmpty()) {
            NoPeriodData()
            return@StatsCard
        }
        spends.forEach { spend ->
            val avatarColor = AccountVisuals.color(spend.account.color)
            ShareRow(
                avatar = {
                    StatsAvatar(
                        icon = { size ->
                            Icon(
                                imageVector = AccountVisuals.icon(spend.account.icon),
                                contentDescription = null,
                                tint = contentColorOn(avatarColor),
                                modifier = Modifier.size(size),
                            )
                        },
                        color = avatarColor,
                    )
                },
                name = spend.account.name,
                amount = MoneyFormatter.format(spend.amount, currency),
                percentLabel = null,
                fraction = spend.fraction,
                barColor = avatarColor,
                onClick = onAccountClick?.let { { it(spend) } },
            )
        }
    }
}

@Composable
private fun StatsAvatar(
    icon: @Composable (iconSize: Dp) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AVATAR_SIZE)
            .clip(AvatarShape)
            .background(color),
    ) {
        icon(AVATAR_ICON_SIZE)
    }
}

/** One row of a share list: avatar, name, amount/percent, proportional bar. */
@Composable
private fun ShareRow(
    avatar: @Composable () -> Unit,
    name: String,
    amount: String,
    percentLabel: String?,
    fraction: Float,
    barColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier.padding(vertical = 8.dp),
    ) {
        avatar()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(
                    fraction = fraction,
                    color = barColor,
                    modifier = Modifier.weight(1f),
                )
                if (percentLabel != null) {
                    Text(
                        text = percentLabel,
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Thin proportional bar; [fraction] in 0..1. */
@Composable
private fun ShareBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(BAR_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

private val AVATAR_SIZE = 40.dp
private val AVATAR_ICON_SIZE = 20.dp
private val BAR_HEIGHT = 6.dp

/** Accessible mini-legend: colored dot plus label per series (never color-only). */
@Composable
internal fun ChartLegend(
    entries: List<Pair<Color, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(LEGEND_DOT)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private val LEGEND_DOT = 10.dp
