package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import java.math.BigDecimal
import java.util.Currency

/**
 * The compact "as of today" figure used by the per-account breakdown rows and
 * the account-type section headers: the same calendar glyph the hero card pairs
 * with the "as of today" balance, followed by the [amount], red only when
 * negative (otherwise muted, so the secondary line keeps a low profile).
 *
 * The glyph carries the "ad oggi" meaning, so the word is dropped: that keeps
 * the trailing column narrow and leaves the account name its room. The spoken
 * label restores the full phrase for TalkBack.
 */
@Composable
fun AsOfTodayAmount(
    amount: BigDecimal,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val amountText = MoneyFormatter.format(amount, currency)
    val description = stringResource(R.string.dashboard_balance_as_of_today, amountText)
    val color = if (amount.signum() < 0) {
        MaterialTheme.moneyColors.negative
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Outlined.Today,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = amountText,
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
