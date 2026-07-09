package com.callbackdev.saldo.feature.recurring

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val AVATAR_TINT_ALPHA = 0.16f

/**
 * A single subscription row: tinted avatar, name (with an imminent-charge badge),
 * frequency/next-charge/account subtitle, and the monthly-equivalent cost on the
 * right (labelled "equiv. / month" for non-monthly rules).
 */
@Composable
internal fun SubscriptionRowContent(
    item: SubscriptionItem,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val rule = item.rule
    val color = CategoryVisuals.color(rule.color)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AvatarShape)
                .background(color.copy(alpha = AVATAR_TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = CategoryVisuals.icon(rule.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ChargeBadge(nextCharge = item.nextCharge, today = today)
            }
            Text(
                text = subscriptionSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormatter.format(item.monthlyEquivalent, rule.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (rule.frequency != RecurrenceFrequency.MONTHLY) {
                Text(
                    text = stringResource(R.string.subscriptions_monthly_equivalent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Today"/"Tomorrow" pill for a charge that is imminent; nothing otherwise. */
@Composable
private fun ChargeBadge(nextCharge: LocalDate?, today: LocalDate, modifier: Modifier = Modifier) {
    val label = when (nextCharge) {
        today -> stringResource(R.string.subscriptions_charge_today)
        today.plusDays(1) -> stringResource(R.string.subscriptions_charge_tomorrow)
        else -> return
    }
    Box(
        modifier = modifier
            .padding(start = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * "Monthly · charged on 7 Jul · Visa Card"; for non-monthly rules the actual
 * charge is prefixed to the frequency, e.g. "Semi-annual 96,00 € · charged on…".
 */
@Composable
private fun subscriptionSubtitle(item: SubscriptionItem): String {
    val rule = item.rule
    val frequency = stringResource(rule.frequency.labelRes())
    val leading = if (rule.frequency == RecurrenceFrequency.MONTHLY || rule.amount == null) {
        frequency
    } else {
        "$frequency ${MoneyFormatter.format(rule.amount, rule.currency)}"
    }
    return buildList {
        add(leading)
        item.nextCharge?.let { add(stringResource(R.string.subscriptions_charged_on, shortDate(it))) }
        item.account?.let { add(it.name) }
    }.joinToString(separator = " · ")
}

/** Localized compact charge date, e.g. "7 Jul". */
@Composable
private fun shortDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
