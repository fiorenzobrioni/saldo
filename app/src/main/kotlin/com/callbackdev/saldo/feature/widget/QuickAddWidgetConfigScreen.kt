package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * The widget's optional setup: which account it adds to, whether it starts on
 * expense or income, whether the categories adapt to use, and whether today's
 * total is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddWidgetConfigScreen(
    state: QuickAddWidgetConfigUiState,
    theme: QuickAddWidgetTheme,
    onAccountSelected: (Long?) -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onShowTodayTotalChanged: (Boolean) -> Unit,
    onShowAppShortcutChanged: (Boolean) -> Unit,
    onUseMostUsedChanged: (Boolean) -> Unit,
    onCategoryToggled: (Long) -> Unit,
    onAppearanceSelected: (WidgetAppearance) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            EditorSaveButton(
                // Always "update": the launcher has already created the widget
                // by the time this screen opens, so even the first visit is
                // editing something that exists.
                text = stringResource(R.string.widget_config_update),
                onClick = onConfirm,
                enabled = !state.isLoading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                // The preview leads: every control below changes what is drawn
                // here, so the choice is made by looking rather than by placing
                // the widget and coming back.
                QuickAddWidgetPreview(
                    theme = theme,
                    categories = state.categories,
                    showAppShortcut = state.config.showAppShortcut,
                )
            }
            item {
                Section(stringResource(R.string.widget_config_appearance)) {
                    AppearanceSelector(state.config.appearance, onAppearanceSelected)
                }
            }
            item {
                Section(stringResource(R.string.widget_config_type)) {
                    TypeSelector(state.config.type, onTypeSelected)
                }
            }
            item {
                Section(stringResource(R.string.widget_config_account)) {
                    AccountChips(state.accounts, state.config.accountId, onAccountSelected)
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.widget_config_most_used),
                    subtitle = stringResource(R.string.widget_config_most_used_caption),
                    checked = state.config.usesMostUsed,
                    onCheckedChange = onUseMostUsedChanged,
                )
            }
            if (!state.config.usesMostUsed) {
                item {
                    Section(stringResource(R.string.widget_config_pinned)) {
                        CategoryChips(state.categories, state.config.pinnedCategoryIds, onCategoryToggled)
                    }
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.widget_config_app_shortcut),
                    subtitle = stringResource(R.string.widget_config_app_shortcut_caption),
                    checked = state.config.showAppShortcut,
                    onCheckedChange = onShowAppShortcutChanged,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.widget_config_today_total),
                    subtitle = stringResource(R.string.widget_config_today_total_caption),
                    checked = state.config.showTodayTotal,
                    onCheckedChange = onShowTodayTotalChanged,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(
        TransactionType.EXPENSE to stringResource(R.string.widget_quick_add_expense),
        TransactionType.INCOME to stringResource(R.string.widget_quick_add_income),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountChips(accounts: List<Account>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The first chip is the honest default: follow whatever the app itself
        // considers the default account, so changing it in Settings moves the
        // widget too.
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.widget_config_account_default)) },
        )
        accounts.forEach { account ->
            FilterChip(
                selected = account.id == selectedId,
                onClick = { onSelect(account.id) },
                label = { Text(account.name, maxLines = 1) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    pinnedIds: List<Long>,
    onToggle: (Long) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEach { category ->
            val selected = category.id in pinnedIds
            FilterChip(
                selected = selected,
                onClick = { onToggle(category.id) },
                label = { Text(category.name, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSelector(selected: WidgetAppearance, onSelect: (WidgetAppearance) -> Unit) {
    val options = listOf(
        WidgetAppearance.SYSTEM to stringResource(R.string.widget_config_appearance_system),
        WidgetAppearance.LIGHT to stringResource(R.string.widget_config_appearance_light),
        WidgetAppearance.DARK to stringResource(R.string.widget_config_appearance_dark),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (appearance, label) ->
            SegmentedButton(
                selected = appearance == selected,
                onClick = { onSelect(appearance) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

