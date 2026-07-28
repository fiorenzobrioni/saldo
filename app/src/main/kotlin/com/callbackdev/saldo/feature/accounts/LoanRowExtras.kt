package com.callbackdev.saldo.feature.accounts

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
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
import com.callbackdev.saldo.core.domain.model.LoanProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PERCENT = 100

/**
 * Loan-only extras rendered under an account row (the credit card's twin,
 * PLANNING ADR 33): the repayment bar in a positive role (share of the initial
 * debt already paid back), the residual debt in evidence, the next installment
 * and the estimated installments left, all read from the linked recurring
 * transfers. Renders nothing for other accounts, for archived loans (the row
 * is history, not a tool) and, per spec, for a loan with no recurring transfer
 * pointing at it: without an installment there is nothing to estimate. The
 * one exception is a paid-off loan, whose state is worth telling even without
 * a rule: the row suggests archiving, never deletion (the history stays).
 */
@Composable
internal fun LoanRowExtras(
    item: AccountWithBalance,
    progress: LoanProgress?,
    modifier: Modifier = Modifier,
) {
    if (item.account.isArchived || progress == null) return
    if (!progress.isPaidOff && !progress.hasLinkedRule) return

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
        if (progress.isPaidOff) {
            PaidOffRow()
        } else {
            RepaymentBar(progress = progress, currency = item.account.currency)
            InstallmentLines(progress = progress, currency = item.account.currency)
        }
    }
}

@Composable
private fun RepaymentBar(
    progress: LoanProgress,
    currency: java.util.Currency,
    modifier: Modifier = Modifier,
) {
    val percent = (progress.fraction * PERCENT).toInt()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_loan_repaid_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.account_loan_percent, percent),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThresholdProgressBar(
            fraction = progress.fraction,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_loan_residual_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MoneyFormatter.format(progress.residual, currency),
                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
            )
        }
    }
}

@Composable
private fun InstallmentLines(
    progress: LoanProgress,
    currency: java.util.Currency,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val amount = progress.nextInstallmentAmount
        val date = progress.nextInstallmentDate
        if (amount != null && date != null) {
            Text(
                text = stringResource(
                    R.string.account_loan_next_installment,
                    MoneyFormatter.format(amount, currency),
                    formatInstallmentDate(date),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val remaining = progress.remainingInstallments
        if (remaining != null) {
            Text(
                text = pluralStringResource(
                    R.plurals.account_loan_remaining_installments,
                    remaining.toInt(),
                    remaining.toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaidOffRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.moneyColors.income,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.account_loan_paid_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatInstallmentDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}
