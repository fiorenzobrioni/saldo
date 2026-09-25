@file:Suppress("TooManyFunctions") // The guide's prose kit: one small composable per shape of text.

package com.callbackdev.saldo.feature.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.ThresholdProgressBar
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * The guide, in Chiaro's shape as Passo has it (ADR 52): a tour of the four
 * screens, what each one answers, and the things a screen cannot say out loud
 * (that a transfer is not a spend, that the balance is computed, that the
 * Dashboard and the Statistics read the same month two ways, that a future
 * movement waits for its day), closing on where the data lives. Re-openable
 * forever from Settings: a definition offered before the user has met the
 * thing does not stick.
 *
 * Chiaro's two rules hold it in shape. It **never teaches a control**: it says
 * what a screen is for, never which button to press, because a control that
 * needs explaining is a bug. And it **never justifies an absence**: it says what
 * Saldo does, not what it is not. It teaches by showing the app's own pieces (a
 * few movements, a budget bar), each captioned as an example, in the currency of
 * the phone's region, so no sample reads as the user's data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.guide_title)) },
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
    ) { padding ->
        GuideContent(Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun GuideContent(modifier: Modifier) {
    val currency = remember { sampleCurrency() }
    Column(
        modifier = modifier
            .testTag(GuideTags.CONTENT)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Paragraph(R.string.guide_intro)

        // The map first: the four glyphs of the bottom bar, so every chapter is
        // placed before it starts.
        Chapter(null, R.string.guide_map_title)
        TabMap()
        Paragraph(R.string.guide_map_note)

        Chapter(Icons.Outlined.SwapHoriz, R.string.guide_movements_title)
        Paragraph(R.string.guide_movements_p1)
        MovementsSample(currency)
        Caption(R.string.guide_movements_caption)
        Feature(R.string.guide_movements_transfer_title, R.string.guide_movements_transfer_body)
        Feature(R.string.guide_movements_adjustment_title, R.string.guide_movements_adjustment_body)
        Feature(R.string.guide_movements_future_title, R.string.guide_movements_future_body)
        Feature(R.string.guide_movements_people_title, R.string.guide_movements_people_body)

        Chapter(Icons.Outlined.AccountBalanceWallet, R.string.guide_balance_title)
        Paragraph(R.string.guide_balance_p1)
        Feature(R.string.guide_balance_today_title, R.string.guide_balance_today_body)
        Feature(R.string.guide_balance_debts_title, R.string.guide_balance_debts_body)
        Feature(R.string.guide_balance_switches_title, R.string.guide_balance_switches_body)
        Feature(R.string.guide_balance_currencies_title, R.string.guide_balance_currencies_body)

        Chapter(Icons.Outlined.SpaceDashboard, R.string.guide_dashboard_title)
        Paragraph(R.string.guide_dashboard_p1)
        Feature(R.string.guide_dashboard_two_readings_title, R.string.guide_dashboard_two_readings_body)
        Feature(R.string.guide_dashboard_spendable_title, R.string.guide_dashboard_spendable_body)
        Feature(R.string.guide_dashboard_forecast_title, R.string.guide_dashboard_forecast_body)

        Chapter(Icons.Outlined.Insights, R.string.guide_stats_title)
        Paragraph(R.string.guide_stats_p1)
        Feature(R.string.guide_stats_refunds_title, R.string.guide_stats_refunds_body)
        Feature(R.string.guide_stats_recap_title, R.string.guide_stats_recap_body)

        Chapter(Icons.Outlined.Flag, R.string.guide_budget_title)
        Paragraph(R.string.guide_budget_p1)
        BudgetSample(currency)
        Caption(R.string.guide_budget_caption)
        Feature(R.string.guide_budget_alerts_title, R.string.guide_budget_alerts_body)
        Feature(R.string.guide_budget_goals_title, R.string.guide_budget_goals_body)

        Chapter(Icons.Outlined.EventRepeat, R.string.guide_recurring_title)
        Paragraph(R.string.guide_recurring_p1)
        Feature(R.string.guide_recurring_modes_title, R.string.guide_recurring_modes_body)
        Feature(R.string.guide_recurring_pause_title, R.string.guide_recurring_pause_body)
        Feature(R.string.guide_recurring_upcoming_title, R.string.guide_recurring_upcoming_body)

        Chapter(Icons.Outlined.Widgets, R.string.guide_quick_title)
        Paragraph(R.string.guide_quick_p1)
        Feature(R.string.guide_quick_text_title, R.string.guide_quick_text_body)
        Feature(R.string.guide_quick_widget_title, R.string.guide_quick_widget_body)

        Chapter(Icons.Outlined.Notifications, R.string.guide_notifications_title)
        Paragraph(R.string.guide_notifications_p1)
        Feature(R.string.guide_notifications_expand_title, R.string.guide_notifications_expand_body)
        Feature(R.string.guide_notifications_night_title, R.string.guide_notifications_night_body)

        Chapter(Icons.Outlined.Lock, R.string.guide_data_title)
        Paragraph(R.string.guide_data_p1)
        Paragraph(R.string.guide_data_p2)
        Paragraph(R.string.guide_data_p3)
    }
}

// The prose kit: Chiaro's, value for value, as Passo carries it.

@Composable
private fun Chapter(icon: ImageVector?, @StringRes text: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        if (icon != null) {
            // Decoration: the title beside it says it all.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(stringResource(text), style = MaterialTheme.typography.titleLarge)
    }
}

/** Prose to be read, not scanned: bodyLarge. */
@Composable
private fun Paragraph(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

/**
 * One thing a screen does: its name, then what it is for. No icon of its own:
 * the chapter's glyph already says which screen this is.
 */
@Composable
private fun Feature(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the sample above it stands for: always said, so no example reads as the user's data. */
@Composable
private fun Caption(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// The samples: the app's own pieces, shown as themselves.

/** The bottom bar spelled out: the same four glyphs, in the same order. */
@Composable
private fun TabMap() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabRow(Icons.Filled.SpaceDashboard, R.string.nav_dashboard, R.string.guide_map_dashboard)
        TabRow(Icons.AutoMirrored.Filled.ReceiptLong, R.string.nav_transactions, R.string.guide_map_transactions)
        TabRow(Icons.Filled.Insights, R.string.nav_stats, R.string.guide_map_stats)
        TabRow(Icons.Filled.Settings, R.string.nav_settings, R.string.guide_map_settings)
    }
}

@Composable
private fun TabRow(icon: ImageVector, @StringRes name: Int, @StringRes body: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Column {
            Text(stringResource(name), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Three movements as the ledger tells them apart: a sign and an icon before
 * any colour, and the transfer in the neutral ink, because it moves money
 * rather than spending or earning it.
 */
@Composable
private fun MovementsSample(currency: Currency) {
    val money = MaterialTheme.moneyColors
    SaldoCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SampleMovement(
                icon = Icons.AutoMirrored.Outlined.TrendingDown,
                label = R.string.guide_sample_expense,
                amount = "−" + MoneyFormatter.format(BigDecimal("12.50"), currency),
                color = money.expense,
            )
            SampleMovement(
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                label = R.string.guide_sample_income,
                amount = "+" + MoneyFormatter.format(BigDecimal("1850.00"), currency),
                color = money.income,
            )
            SampleMovement(
                icon = Icons.Outlined.SwapHoriz,
                label = R.string.guide_sample_transfer,
                amount = MoneyFormatter.format(BigDecimal("200.00"), currency),
                color = money.neutral,
            )
        }
    }
}

@Composable
private fun SampleMovement(icon: ImageVector, @StringRes label: Int, amount: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** A category budget at 82%, with the bar the Budgets screen draws and the words beside it. */
@Composable
private fun BudgetSample(currency: Currency) {
    val spent = MoneyFormatter.format(BigDecimal("412.30"), currency)
    val limit = MoneyFormatter.format(BigDecimal("500.00"), currency)
    SaldoCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.guide_sample_budget_name),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.guide_sample_budget_share, SAMPLE_BUDGET_PERCENT),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.moneyColors.warning,
                )
            }
            ThresholdProgressBar(
                fraction = SAMPLE_BUDGET_PERCENT / PERCENT,
                color = MaterialTheme.moneyColors.warning,
                modifier = Modifier.fillMaxWidth().testTag(GuideTags.BUDGET),
            )
            Text(
                text = stringResource(R.string.guide_sample_budget_amounts, spent, limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The currency the samples are written in: the one of the phone's region,
 * which is where the user most likely keeps their money; the euro where the
 * region has none.
 */
private fun sampleCurrency(): Currency =
    runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull() ?: Currency.getInstance("EUR")

object GuideTags {
    const val CONTENT = "guide_content"
    const val BUDGET = "guide_budget"
}

private const val SAMPLE_BUDGET_PERCENT = 82
private const val PERCENT = 100f
