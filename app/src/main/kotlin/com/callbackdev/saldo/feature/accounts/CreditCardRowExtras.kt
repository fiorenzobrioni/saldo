package com.callbackdev.saldo.feature.accounts

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Warning threshold (80%) for the card utilisation bar, mirroring the budget levels. */
private const val UTILISATION_WARNING_FRACTION = 0.8f
private const val PERCENT = 100

/**
 * Credit-card-only extras rendered under an account row: the utilisation bar
 * (used vs limit / fido) and the "pay statement" call to action when a
 * statement is due. Renders nothing for any other account, or for a credit card
 * with neither a limit nor a due statement.
 */
@Composable
internal fun CreditCardRowExtras(
    item: AccountWithBalance,
    dueStatement: DueStatement?,
    onSettleStatement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = item.account.creditCard ?: return
    val limit = config.creditLimit
    if (limit == null && dueStatement == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SaldoDimens.rowPaddingHorizontal,
                end = SaldoDimens.rowPaddingHorizontal,
                bottom = SaldoDimens.rowPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (limit != null && limit.signum() > 0) {
            UtilisationBar(
                debt = item.balance.negate().max(BigDecimal.ZERO),
                limit = limit,
                currency = item.account.currency,
            )
        }
        if (dueStatement != null) {
            StatementCallToAction(dueStatement = dueStatement, onSettle = onSettleStatement)
        }
    }
}

@Composable
private fun UtilisationBar(
    debt: BigDecimal,
    limit: BigDecimal,
    currency: java.util.Currency,
    modifier: Modifier = Modifier,
) {
    val fraction = debt.divide(limit, FRACTION_SCALE, RoundingMode.HALF_UP).toFloat()
    val percent = debt.multiply(BigDecimal(PERCENT)).divide(limit, 0, RoundingMode.FLOOR).toInt()
    val overLimit = fraction >= 1f
    val color: Color = when {
        overLimit -> MaterialTheme.colorScheme.error
        fraction >= UTILISATION_WARNING_FRACTION -> MaterialTheme.moneyColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_cc_utilisation_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.account_cc_utilisation_value,
                    MoneyFormatter.format(debt, currency),
                    MoneyFormatter.format(limit, currency),
                    percent,
                ),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThresholdProgressBar(
            fraction = fraction,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatementCallToAction(
    dueStatement: DueStatement,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.account_cc_statement_due),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.account_cc_statement_charge_date,
                    formatChargeDate(dueStatement.cycle.paymentDue),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onSettle) {
            Icon(
                imageVector = Icons.Outlined.Payments,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                stringResource(
                    R.string.account_cc_pay_statement,
                    MoneyFormatter.format(dueStatement.amount, dueStatement.currency),
                ),
            )
        }
    }
}

@Composable
private fun formatChargeDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}

private const val FRACTION_SCALE = 4
