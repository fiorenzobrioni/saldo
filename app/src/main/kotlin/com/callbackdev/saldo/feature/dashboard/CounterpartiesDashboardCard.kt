package com.callbackdev.saldo.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import java.math.BigDecimal
import java.util.Currency

/** People previewed on the card before the "+N more" line. */
private const val PREVIEW_PEOPLE = 2

/**
 * Dashboard card for credits and debts (ADR 34): the two totals kept apart,
 * then the people with something still open. Unlike the budget and savings
 * cards it has no empty invitation - the caller hides it when nothing is open,
 * because a card about lent money is noise for someone who never lends any.
 */
@Composable
internal fun CounterpartiesCard(
    ledger: CounterpartyLedger,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.Handshake,
                title = stringResource(R.string.dashboard_counterparties_title),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TotalBlock(
                    label = stringResource(R.string.counterparties_owed_to_you),
                    amount = ledger.owedToYou,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.income,
                    modifier = Modifier.weight(1f),
                )
                TotalBlock(
                    label = stringResource(R.string.counterparties_you_owe),
                    amount = ledger.youOwe,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            val open = ledger.entries.filterNot { it.isSettled }
            val preview = open.take(PREVIEW_PEOPLE)
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview.forEach { entry ->
                        val amount = entry.amounts.firstOrNull { it.amount.signum() != 0 }
                            ?: return@forEach
                        PersonPreviewRow(
                            name = entry.name,
                            amount = amount.amount.abs(),
                            currency = amount.currency,
                            isCredit = amount.amount.signum() < 0,
                        )
                    }
                }
            }
            val remaining = open.size - preview.size
            if (remaining > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_counterparties_more, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalBlock(
    label: String,
    amount: BigDecimal,
    currency: Currency,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = if (amount.signum() > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonPreviewRow(
    name: String,
    amount: BigDecimal,
    currency: Currency,
    isCredit: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            fontWeight = FontWeight.Medium,
            color = if (isCredit) {
                MaterialTheme.moneyColors.income
            } else {
                MaterialTheme.moneyColors.expense
            },
        )
    }
}
