package com.callbackdev.saldo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategories: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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

            SettingsSectionHeader(stringResource(R.string.settings_section_management))
            SettingsEntry(
                title = stringResource(R.string.settings_subscriptions),
                hint = stringResource(R.string.settings_subscriptions_hint),
                icon = Icons.Outlined.Subscriptions,
                onClick = onNavigateToSubscriptions,
            )
            SettingsEntry(
                title = stringResource(R.string.settings_categories),
                hint = stringResource(R.string.settings_categories_hint),
                icon = Icons.Outlined.Category,
                onClick = onNavigateToCategories,
            )
        }
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
