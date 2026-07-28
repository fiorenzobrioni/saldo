@file:Suppress("TooManyFunctions") // One small composable per card/row/section.

package com.callbackdev.saldo.feature.counterparties

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.CounterpartyAmount
import com.callbackdev.saldo.core.domain.model.CounterpartyBalance
import com.callbackdev.saldo.core.domain.model.CounterpartyLedger
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.navigation.FilteredTransactionsRoute
import com.callbackdev.saldo.navigation.TransactionEditorRoute
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency

/**
 * Credits and debts toward people (ADR 34): a hero with the two totals kept
 * apart ("they owe you" and "you owe"), then one row per person with what is
 * still open, how many movements it took and when the last one was. A row opens
 * that person's movements; its trailing action prefills the repayment in the
 * opposite direction.
 *
 * Nothing here is a register of its own: every figure is the signed sum of
 * ordinary movements, which is why a repayment is recorded in the editor like
 * any other movement rather than settled with a button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterpartiesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDrillDown: (FilteredTransactionsRoute) -> Unit,
    onNavigateToEditor: (TransactionEditorRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CounterpartiesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.counterparties_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.isEmpty) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.counterparties_add)) },
                    icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                    onClick = { onNavigateToEditor(newLoanRoute()) },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> ListSkeleton(modifier = Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyState(
                icon = Icons.Outlined.Handshake,
                title = stringResource(R.string.counterparties_empty_title),
                body = stringResource(R.string.counterparties_empty_body),
                actionLabel = stringResource(R.string.counterparties_empty_cta),
                onAction = { onNavigateToEditor(newLoanRoute()) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> CounterpartiesContent(
                ledger = uiState.ledger,
                onOpen = { onNavigateToDrillDown(drillDownRoute(it)) },
                onSettle = { entry -> settlementRoute(entry)?.let(onNavigateToEditor) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CounterpartiesContent(
    ledger: CounterpartyLedger,
    onOpen: (CounterpartyBalance) -> Unit,
    onSettle: (CounterpartyBalance) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        item(key = "summary") { CounterpartiesSummaryCard(ledger = ledger) }
        items(ledger.entries, key = { it.name }) { entry ->
            CounterpartyCard(
                entry = entry,
                currency = ledger.currency,
                onClick = { onOpen(entry) },
                onSettle = { onSettle(entry) },
            )
        }
    }
}

/**
 * The two totals side by side, never netted: 200 lent to one person and 200
 * borrowed from another is not the same as owing nobody anything, and a single
 * net figure would say exactly that.
 */
@Composable
private fun CounterpartiesSummaryCard(ledger: CounterpartyLedger, modifier: Modifier = Modifier) {
    SaldoCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SaldoDimens.cardPaddingLarge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SummaryHalf(
                    label = stringResource(R.string.counterparties_owed_to_you),
                    amount = ledger.owedToYou,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.income,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.height(SummaryDividerHeight),
                )
                SummaryHalf(
                    label = stringResource(R.string.counterparties_you_owe),
                    amount = ledger.youOwe,
                    currency = ledger.currency,
                    color = MaterialTheme.moneyColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!ledger.hasOpenPositions) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.counterparties_all_settled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ledger.hasOtherCurrencies) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.counterparties_other_currencies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryHalf(
    label: String,
    amount: BigDecimal,
    currency: Currency,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            // A zero total is not news: it stays quiet instead of shouting a color.
            color = if (amount.signum() > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One person: avatar, name, activity line, open position and the repayment action. */
@Composable
private fun CounterpartyCard(
    entry: CounterpartyBalance,
    currency: Currency,
    onClick: () -> Unit,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = SaldoDimens.cardPadding,
                end = 4.dp,
                top = SaldoDimens.cardPaddingVertical,
                bottom = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            CounterpartyAvatar(name = entry.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activityLine(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            CounterpartyPosition(entry = entry, currency = currency)
            if (!entry.isSettled) {
                IconButton(onClick = onSettle) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.AssignmentReturn,
                        contentDescription = stringResource(
                            R.string.counterparties_settle_action,
                            entry.name,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                // Keeps the amounts of settled and open rows on the same column.
                Spacer(Modifier.width(SettledTrailingWidth))
            }
        }
    }
}

/**
 * What is open, as a magnitude plus the direction in words. The sign lives in
 * the label rather than in a minus: "-50 euro" reads as a loss on a screen where
 * it means money that is coming back.
 */
@Composable
private fun CounterpartyPosition(
    entry: CounterpartyBalance,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val primary = entry.amountIn(currency)
    val open = if (primary != null && primary.signum() != 0) {
        CounterpartyAmount(currency, primary)
    } else {
        entry.amounts.firstOrNull { it.amount.signum() != 0 }
    }
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        if (open == null) {
            Text(
                text = stringResource(R.string.counterparties_settled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val isCredit = open.amount.signum() < 0
        Text(
            text = MoneyFormatter.format(open.amount.abs(), open.currency),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = if (isCredit) {
                MaterialTheme.moneyColors.income
            } else {
                MaterialTheme.moneyColors.expense
            },
            maxLines = 1,
        )
        Text(
            text = stringResource(
                if (isCredit) {
                    R.string.counterparties_direction_credit
                } else {
                    R.string.counterparties_direction_debt
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Initials on a tint derived from the name itself. No color is stored for a
 * counterparty (it is a name, not an entity to configure), but a stable tint
 * makes a list of people scannable, and the same person keeps the same one.
 */
@Composable
private fun CounterpartyAvatar(name: String, modifier: Modifier = Modifier) {
    val color = CategoryVisuals.color(avatarColorKey(name))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(AvatarShape)
            .background(color),
    ) {
        Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColorOn(color),
        )
    }
}

@Composable
private fun activityLine(entry: CounterpartyBalance): String {
    val count = pluralStringResource(
        R.plurals.counterparties_movement_count,
        entry.movementCount,
        entry.movementCount,
    )
    return stringResource(R.string.counterparties_activity_line, count, formatDate(entry.lastActivity))
}

@Composable
private fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).withLocaleDateCasing(locale)
    }
}

/** A new loan out: an expense with the counterparty section open and no name yet. */
private fun newLoanRoute(): TransactionEditorRoute = TransactionEditorRoute(
    initialTypeName = TransactionType.EXPENSE.name,
    initialCounterparty = "",
)

/** Up to two initials, from the first two words of the name. */
internal fun initialsOf(name: String): String = name
    .trim()
    .split(' ', '\t')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString(separator = "")
    .ifEmpty { "?" }

/** Stable palette index for a name, from its own characters (never persisted). */
internal fun avatarColorKey(name: String): Int {
    val sum = name.trim().lowercase().sumOf { it.code }
    return CategoryVisuals.colors[sum % CategoryVisuals.colors.size]
}

/** Height of the rule between the two hero totals. */
private val SummaryDividerHeight = 44.dp

/** Width of the icon button a settled row does not show. */
private val SettledTrailingWidth = 48.dp
