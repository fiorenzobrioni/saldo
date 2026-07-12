package com.callbackdev.saldo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.BuildConfig
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.RenewalReminderPreferences
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurrences: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val renewalReminder by viewModel.renewalReminderPreferences.collectAsStateWithLifecycle()
    val primaryCurrency by viewModel.primaryCurrencyOverride.collectAsStateWithLifecycle()
    var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_preferences))
            SettingsEntry(
                title = stringResource(R.string.settings_primary_currency),
                hint = primaryCurrency?.label()
                    ?: stringResource(R.string.settings_primary_currency_auto),
                icon = Icons.Outlined.Payments,
                onClick = { showCurrencyDialog = true },
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
            ThemeModeSelector(
                selected = themePreferences.mode,
                onSelected = viewModel::onThemeModeSelected,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_hint)) },
                trailingContent = {
                    Switch(
                        checked = themePreferences.useDynamicColor,
                        onCheckedChange = viewModel::onDynamicColorChanged,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_renewal_reminder)) },
                supportingContent = { Text(stringResource(R.string.settings_renewal_reminder_hint)) },
                trailingContent = {
                    Switch(
                        checked = renewalReminder.enabled,
                        onCheckedChange = viewModel::onRenewalReminderChanged,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (renewalReminder.enabled) {
                RenewalLeadDaysSelector(
                    selected = renewalReminder.leadDays,
                    onSelected = viewModel::onRenewalLeadDaysSelected,
                )
            }

            SettingsSectionHeader(stringResource(R.string.settings_section_management))
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
                title = stringResource(R.string.settings_categories),
                hint = stringResource(R.string.settings_categories_hint),
                icon = Icons.Outlined.Category,
                onClick = onNavigateToCategories,
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_data))
            SettingsEntry(
                title = stringResource(R.string.settings_backup),
                hint = stringResource(R.string.settings_backup_hint),
                icon = Icons.Outlined.SettingsBackupRestore,
                onClick = onNavigateToBackup,
            )

            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            SettingsEntry(
                title = stringResource(R.string.settings_about),
                hint = stringResource(R.string.settings_about_hint, BuildConfig.VERSION_NAME),
                icon = Icons.Outlined.Info,
                onClick = onNavigateToAbout,
            )
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
                    CurrencyRow(
                        label = stringResource(R.string.settings_primary_currency_auto),
                        isSelected = selected == null,
                        onClick = { onSelected(null) },
                    )
                }
                items(CurrencyCatalog.supportedCurrencies, key = { it.currencyCode }) { currency ->
                    CurrencyRow(
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
private fun CurrencyRow(
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
private fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsEntry(
    title: String,
    hint: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}
