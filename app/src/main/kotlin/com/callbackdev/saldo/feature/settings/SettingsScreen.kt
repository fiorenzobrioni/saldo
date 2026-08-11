@file:Suppress("TooManyFunctions") // One small composable per settings row/section/dialog.

package com.callbackdev.saldo.feature.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.BuildConfig
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.FirstDayOfWeek
import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.designsystem.component.SettingsEntry
import com.callbackdev.saldo.core.designsystem.component.SettingsGroup
import com.callbackdev.saldo.core.designsystem.component.SettingsSectionHeader
import com.callbackdev.saldo.core.designsystem.component.SettingsSwitchRow
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import com.callbackdev.saldo.feature.widget.SaldoQuickAddWidgetReceiver
import com.callbackdev.saldo.feature.widget.SaldoQuickBarWidgetReceiver
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToRecurrences: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToSavingsGoals: () -> Unit,
    onNavigateToCounterparties: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToRates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val renewalReminder by viewModel.renewalReminderPreferences.collectAsStateWithLifecycle()
    val dashboardCards by viewModel.dashboardCardPreferences.collectAsStateWithLifecycle()
    val balanceAccountsExpandedDefault by viewModel.balanceAccountsExpandedByDefault
        .collectAsStateWithLifecycle()
    val primaryCurrency by viewModel.primaryCurrencyOverride.collectAsStateWithLifecycle()
    val currencyConversionEnabled by viewModel.currencyConversionEnabled.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val defaultAccountId by viewModel.defaultAccountId.collectAsStateWithLifecycle()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }
    var showDefaultAccountDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = { Text(stringResource(R.string.nav_settings)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_preferences))
            SettingsGroup {
                SettingsEntry(
                    title = stringResource(R.string.settings_primary_currency),
                    hint = primaryCurrency?.label()
                        ?: stringResource(R.string.settings_primary_currency_auto),
                    icon = Icons.Outlined.Payments,
                    onClick = { showCurrencyDialog = true },
                )
                // The switch is also where the app declares its only network
                // use outside backup/export (ADR 40): the hint says what
                // travels and what never does.
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_currency_conversion),
                    hint = stringResource(R.string.settings_currency_conversion_hint),
                    checked = currencyConversionEnabled,
                    onCheckedChange = viewModel::onCurrencyConversionChanged,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_exchange_rates),
                    hint = stringResource(R.string.settings_exchange_rates_hint),
                    icon = Icons.Outlined.CurrencyExchange,
                    onClick = onNavigateToRates,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_default_account),
                    hint = activeAccounts.firstOrNull { it.id == defaultAccountId }?.name
                        ?: stringResource(R.string.settings_default_account_auto),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    onClick = { showDefaultAccountDialog = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_first_day_of_week)) },
                    supportingContent = { Text(stringResource(R.string.settings_first_day_of_week_hint)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                FirstDayOfWeekSelector(
                    selected = firstDayOfWeek,
                    onSelected = viewModel::onFirstDayOfWeekSelected,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsGroup {
                ThemeModeSelector(
                    selected = themePreferences.mode,
                    onSelected = viewModel::onThemeModeSelected,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    hint = stringResource(R.string.settings_dynamic_color_hint),
                    checked = themePreferences.useDynamicColor,
                    onCheckedChange = viewModel::onDynamicColorChanged,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_dashboard))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_accounts_expanded),
                    hint = stringResource(R.string.settings_dashboard_accounts_expanded_hint),
                    checked = balanceAccountsExpandedDefault,
                    onCheckedChange = viewModel::onBalanceAccountsExpandedDefaultChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_sts),
                    hint = stringResource(R.string.settings_dashboard_show_sts_hint),
                    checked = dashboardCards.showSafeToSpend,
                    onCheckedChange = viewModel::onShowSafeToSpendChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_budget),
                    hint = stringResource(R.string.settings_dashboard_show_budget_hint),
                    checked = dashboardCards.showBudget,
                    onCheckedChange = viewModel::onShowBudgetCardChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_savings),
                    hint = stringResource(R.string.settings_dashboard_show_savings_hint),
                    checked = dashboardCards.showSavingsGoals,
                    onCheckedChange = viewModel::onShowSavingsGoalsChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_counterparties),
                    hint = stringResource(R.string.settings_dashboard_show_counterparties_hint),
                    checked = dashboardCards.showCounterparties,
                    onCheckedChange = viewModel::onShowCounterpartiesChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_upcoming),
                    hint = stringResource(R.string.settings_dashboard_show_upcoming_hint),
                    checked = dashboardCards.showUpcoming,
                    onCheckedChange = viewModel::onShowUpcomingChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_recurring),
                    hint = stringResource(R.string.settings_dashboard_show_recurring_hint),
                    checked = dashboardCards.showRecurring,
                    onCheckedChange = viewModel::onShowRecurringChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_recent),
                    hint = stringResource(R.string.settings_dashboard_show_recent_hint),
                    checked = dashboardCards.showRecentTransactions,
                    onCheckedChange = viewModel::onShowRecentTransactionsChanged,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dashboard_show_recap_teaser),
                    hint = stringResource(R.string.settings_dashboard_show_recap_teaser_hint),
                    checked = dashboardCards.showRecapTeaser,
                    onCheckedChange = viewModel::onShowRecapTeaserChanged,
                )
            }

            // Nobody browses the launcher's widget picker looking for Saldo:
            // the offer lives here, where the user already is, and the launcher
            // shows its own add dialog. Hidden entirely on launchers that do
            // not support pinning, where the entries could only fail.
            val widgetContext = LocalContext.current
            val canPinWidgets = remember(widgetContext) {
                runCatching {
                    AppWidgetManager.getInstance(widgetContext).isRequestPinAppWidgetSupported
                }.getOrDefault(false)
            }
            if (canPinWidgets) {
                val pinScope = rememberCoroutineScope()
                SettingsSectionHeader(stringResource(R.string.settings_section_widgets))
                SettingsGroup {
                    SettingsEntry(
                        title = stringResource(R.string.settings_widget_pin_grid),
                        hint = stringResource(R.string.settings_widget_pin_grid_hint),
                        icon = Icons.Outlined.Widgets,
                        onClick = {
                            pinScope.launch {
                                runCatching {
                                    GlanceAppWidgetManager(widgetContext)
                                        .requestPinGlanceAppWidget(SaldoQuickAddWidgetReceiver::class.java)
                                }
                            }
                        },
                    )
                    SettingsEntry(
                        title = stringResource(R.string.settings_widget_pin_bar),
                        hint = stringResource(R.string.settings_widget_pin_bar_hint),
                        icon = Icons.Outlined.ViewAgenda,
                        onClick = {
                            pinScope.launch {
                                runCatching {
                                    GlanceAppWidgetManager(widgetContext)
                                        .requestPinGlanceAppWidget(SaldoQuickBarWidgetReceiver::class.java)
                                }
                            }
                        },
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
            SettingsGroup {
                // The permission is asked contextually here (and in onboarding),
                // never cold at launch: turning the reminder on is the moment it
                // makes sense.
                val context = LocalContext.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* the reminder stays on either way; a denial just mutes it */ }
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_renewal_reminder),
                    hint = stringResource(R.string.settings_renewal_reminder_hint),
                    checked = renewalReminder.enabled,
                    onCheckedChange = { enabled ->
                        viewModel.onRenewalReminderChanged(enabled)
                        if (enabled) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher
                                    .launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    },
                )
                if (renewalReminder.enabled) {
                    RenewalLeadDaysSelector(
                        selected = renewalReminder.leadDays,
                        onSelected = viewModel::onRenewalLeadDaysSelected,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_management))
            SettingsGroup {
                SettingsEntry(
                    title = stringResource(R.string.settings_accounts),
                    hint = stringResource(R.string.settings_accounts_hint),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    onClick = onNavigateToAccounts,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_recurrences),
                    hint = stringResource(R.string.settings_recurrences_hint),
                    icon = Icons.Outlined.EventRepeat,
                    onClick = onNavigateToRecurrences,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_budgets),
                    hint = stringResource(R.string.settings_budgets_hint),
                    icon = Icons.Outlined.Savings,
                    onClick = onNavigateToBudgets,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_savings),
                    hint = stringResource(R.string.settings_savings_hint),
                    icon = Icons.Outlined.Flag,
                    onClick = onNavigateToSavingsGoals,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_counterparties),
                    hint = stringResource(R.string.settings_counterparties_hint),
                    icon = Icons.Outlined.Handshake,
                    onClick = onNavigateToCounterparties,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_categories),
                    hint = stringResource(R.string.settings_categories_hint),
                    icon = Icons.Outlined.Category,
                    onClick = onNavigateToCategories,
                )
                SettingsEntry(
                    title = stringResource(R.string.settings_tags),
                    hint = stringResource(R.string.settings_tags_hint),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onClick = onNavigateToTags,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_security))
            SettingsGroup {
                SettingsEntry(
                    title = stringResource(R.string.settings_security),
                    hint = stringResource(
                        if (appLockEnabled) {
                            R.string.settings_security_hint_enabled
                        } else {
                            R.string.settings_security_hint_disabled
                        },
                    ),
                    icon = Icons.Outlined.Lock,
                    onClick = onNavigateToSecurity,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_data))
            SettingsGroup {
                SettingsEntry(
                    title = stringResource(R.string.settings_backup),
                    hint = stringResource(R.string.settings_backup_hint),
                    icon = Icons.Outlined.SettingsBackupRestore,
                    onClick = onNavigateToBackup,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            SettingsGroup {
                SettingsEntry(
                    title = stringResource(R.string.settings_about),
                    hint = stringResource(R.string.settings_about_hint, BuildConfig.VERSION_NAME),
                    icon = Icons.Outlined.Info,
                    onClick = onNavigateToAbout,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCurrencyDialog) {
        PrimaryCurrencyDialog(
            selected = primaryCurrency,
            onSelected = { currency ->
                viewModel.onPrimaryCurrencySelected(currency)
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false },
        )
    }

    if (showDefaultAccountDialog) {
        DefaultAccountDialog(
            accounts = activeAccounts,
            selectedId = defaultAccountId,
            onSelected = { accountId ->
                viewModel.onDefaultAccountSelected(accountId)
                showDefaultAccountDialog = false
            },
            onDismiss = { showDefaultAccountDialog = false },
        )
    }
}

/**
 * Radio-list picker for the editor's preselected account: "Automatic" (the
 * last used one) first, then the active accounts.
 */
@Composable
private fun DefaultAccountDialog(
    accounts: List<Account>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    // A stale stored id (archived/deleted account) shows as "Automatic",
    // matching what the editor actually does with it.
    val selectedIsActive = accounts.any { it.id == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_account)) },
        text = {
            LazyColumn {
                item(key = "auto") {
                    RadioRow(
                        label = stringResource(R.string.settings_default_account_auto),
                        isSelected = !selectedIsActive,
                        onClick = { onSelected(null) },
                    )
                }
                items(accounts, key = { it.id }) { account ->
                    RadioRow(
                        label = account.name,
                        isSelected = account.id == selectedId,
                        onClick = { onSelected(account.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** "EUR - Euro" in the current locale, for the currency rows and hints. */
@Composable
private fun Currency.label(): String {
    val locale = LocalConfiguration.current.locales[0]
    return "$currencyCode - ${getDisplayName(locale)}"
}

/**
 * Radio-list picker for the primary currency: "Automatic" first (the
 * account-plurality rule), then the supported currencies.
 */
@Composable
private fun PrimaryCurrencyDialog(
    selected: Currency?,
    onSelected: (Currency?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_primary_currency)) },
        text = {
            LazyColumn {
                item(key = "auto") {
                    RadioRow(
                        label = stringResource(R.string.settings_primary_currency_auto),
                        isSelected = selected == null,
                        onClick = { onSelected(null) },
                    )
                }
                items(CurrencyCatalog.supportedCurrencies, key = { it.currencyCode }) { currency ->
                    RadioRow(
                        label = currency.label(),
                        isSelected = currency == selected,
                        onClick = { onSelected(currency) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RadioRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

/** Week-start choice for the "This week" filter; day names come from the locale. */
@Composable
private fun FirstDayOfWeekSelector(
    selected: DayOfWeek,
    onSelected: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        FirstDayOfWeek.options.forEachIndexed { index, day ->
            SegmentedButton(
                selected = selected == day,
                onClick = { onSelected(day) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = FirstDayOfWeek.options.size,
                ),
            ) {
                Text(day.getDisplayName(TextStyle.SHORT, locale))
            }
        }
    }
}

/** Lead-time choice for the pre-renewal reminder, shown only while it is enabled. */
@Composable
private fun RenewalLeadDaysSelector(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = RenewalReminderPreferences.allowedLeadDays
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, days ->
            SegmentedButton(
                selected = selected == days,
                onClick = { onSelected(days) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(pluralStringResource(R.plurals.settings_lead_days_option, days, days))
            }
        }
    }
}

