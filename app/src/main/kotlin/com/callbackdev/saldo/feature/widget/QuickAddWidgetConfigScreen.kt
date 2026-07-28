@file:Suppress("TooManyFunctions") // One small composable per settings row/section, as in SettingsScreen.

package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.ReorderableListState
import com.callbackdev.saldo.core.designsystem.component.rememberReorderableListState
import com.callbackdev.saldo.core.designsystem.component.reorderableHandle
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first

/**
 * The widget's optional setup, in two flavors served by the same activity: the
 * grid's (account, starting type, categories) and the bar's (account, buttons,
 * app shortcut). Appearance is common. One screen per flavor rather than one
 * screen with captions explaining which option applies at which size - the
 * option that does not apply is simply not there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddWidgetConfigScreen(
    state: QuickAddWidgetConfigUiState,
    isBar: Boolean,
    theme: QuickAddWidgetTheme,
    onAccountSelected: (Long?) -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onShowAppShortcutChanged: (Boolean) -> Unit,
    onButtonsSelected: (WidgetActionButtons) -> Unit,
    onCustomCategoriesChanged: (Boolean) -> Unit,
    onCategoryToggled: (Long) -> Unit,
    onPinnedReordered: (List<Long>) -> Unit,
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

        // The pinned rows are reorderable in place, inside this same list. The
        // live order is a local mirror (the drag mutates it synchronously, the
        // ViewModel hears about it once, at drop), and every mapping between
        // the drag's list indices and the pinned categories goes through the
        // rows' stable keys: the rows sit among other settings items, so a
        // positional offset would break the day a section is added above them.
        val listState = rememberLazyListState()
        val pinnedIds = remember { state.config.pinnedCategoryIds.toMutableStateList() }
        val reorderState = rememberReorderableListState(
            listState = listState,
            onMove = { from, to ->
                val fromId = listState.pinnedIdAt(from)
                val toId = listState.pinnedIdAt(to)
                if (fromId != null && toId != null) {
                    val fromIndex = pinnedIds.indexOf(fromId)
                    val toIndex = pinnedIds.indexOf(toId)
                    if (fromIndex >= 0 && toIndex >= 0) {
                        pinnedIds.add(toIndex, pinnedIds.removeAt(fromIndex))
                    }
                }
            },
            onSettle = { onPinnedReordered(pinnedIds.toList()) },
            canMove = { from, to ->
                listState.pinnedIdAt(from) != null && listState.pinnedIdAt(to) != null
            },
        )
        // Re-adopt the source of truth (a removal, an addition, the seed) once
        // any in-flight drag has settled; mid-drag the local order is the truth.
        LaunchedEffect(state.config.pinnedCategoryIds) {
            snapshotFlow { reorderState.isDragging }.first { !it }
            if (pinnedIds.toList() != state.config.pinnedCategoryIds) {
                pinnedIds.clear()
                pinnedIds.addAll(state.config.pinnedCategoryIds)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "preview") {
                // The preview leads: every control below changes what is drawn
                // here, so the choice is made by looking rather than by placing
                // the widget and coming back.
                QuickAddWidgetPreview(
                    theme = theme,
                    categories = state.categories,
                    showAppShortcut = state.config.showAppShortcut,
                    bar = isBar,
                )
            }
            item(key = "appearance") {
                Section(stringResource(R.string.widget_config_appearance)) {
                    AppearanceSelector(state.config.appearance, onAppearanceSelected)
                }
            }
            if (!isBar) {
                item(key = "type") {
                    Section(stringResource(R.string.widget_config_type)) {
                        TypeSelector(state.config.type, onTypeSelected)
                    }
                }
            }
            item(key = "account") {
                Section(stringResource(R.string.widget_config_account)) {
                    AccountChips(state.accounts, state.config.accountId, onAccountSelected)
                }
            }
            if (isBar) {
                item(key = "buttons") {
                    Section(stringResource(R.string.widget_config_buttons)) {
                        ButtonsSelector(state.config.buttons, onButtonsSelected)
                    }
                }
                item(key = "shortcut") {
                    SwitchRow(
                        title = stringResource(R.string.widget_config_app_shortcut),
                        subtitle = stringResource(R.string.widget_config_app_shortcut_caption),
                        checked = state.config.showAppShortcut,
                        onCheckedChange = onShowAppShortcutChanged,
                    )
                }
            } else {
                item(key = "custom-categories") {
                    SwitchRow(
                        title = stringResource(R.string.widget_config_custom_categories),
                        subtitle = stringResource(R.string.widget_config_custom_categories_caption),
                        checked = state.config.usesCustomCategories,
                        onCheckedChange = onCustomCategoriesChanged,
                    )
                }
                if (state.config.usesCustomCategories) {
                    item(key = "pinned-header") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.widget_config_pinned),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.widget_config_pinned_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(
                        items = pinnedIds.mapNotNull { id -> state.categories.firstOrNull { it.id == id } },
                        key = { category -> pinnedKeyOf(category.id) },
                    ) { category ->
                        val isDragging = reorderState.isDraggingKey(pinnedKeyOf(category.id), listState)
                        val rowModifier = if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = reorderState.draggingItemOffset }
                        } else {
                            Modifier.animateItem()
                        }
                        val currentKey by rememberUpdatedState(pinnedKeyOf(category.id))
                        PinnedCategoryRow(
                            category = category,
                            elevated = isDragging,
                            onRemove = { onCategoryToggled(category.id) },
                            dragHandleModifier = Modifier.reorderableHandle(
                                state = reorderState,
                                key = category.id,
                                index = { listState.indexOfKey(currentKey) },
                            ),
                            modifier = rowModifier,
                        )
                    }
                    item(key = "pinned-add") {
                        val remaining = state.categories.filterNot { it.id in pinnedIds }
                        if (remaining.isNotEmpty()) {
                            Section(stringResource(R.string.widget_config_pinned_add)) {
                                CategoryChips(remaining, emptyList(), onCategoryToggled)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The stable key of a pinned row in the settings list. */
private fun pinnedKeyOf(categoryId: Long): String = "$PINNED_KEY_PREFIX$categoryId"

private fun pinnedIdOf(key: Any?): Long? =
    (key as? String)?.takeIf { it.startsWith(PINNED_KEY_PREFIX) }
        ?.removePrefix(PINNED_KEY_PREFIX)?.toLongOrNull()

/** The pinned category id shown at list position [index], or null for any other row. */
private fun LazyListState.pinnedIdAt(index: Int): Long? =
    pinnedIdOf(layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key)

private fun LazyListState.indexOfKey(key: Any): Int =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index ?: 0

private fun ReorderableListState.isDraggingKey(key: Any, listState: LazyListState): Boolean {
    val dragging = draggingItemIndex ?: return false
    return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragging }?.key == key
}

private const val PINNED_KEY_PREFIX = "pinned-"

/**
 * One pinned category: avatar in the widget's own visual language, the name,
 * a way out and a drag handle. Removing the last one flips the grid back to
 * the app's own category order, which is what an empty pinned list means.
 */
@Composable
private fun PinnedCategoryRow(
    category: Category,
    elevated: Boolean,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val accent = CategoryVisuals.color(category.color)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (elevated) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AvatarShape)
                .background(accent.copy(alpha = AvatarWashAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(category.icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(
                    R.string.widget_config_pinned_remove_a11y,
                    category.name,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = stringResource(R.string.widget_config_pinned_reorder_a11y),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandleModifier,
        )
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
private fun ButtonsSelector(selected: WidgetActionButtons, onSelect: (WidgetActionButtons) -> Unit) {
    val options = listOf(
        WidgetActionButtons.BOTH to stringResource(R.string.widget_config_buttons_both),
        WidgetActionButtons.EXPENSE_ONLY to stringResource(R.string.widget_quick_add_expense),
        WidgetActionButtons.INCOME_ONLY to stringResource(R.string.widget_quick_add_income),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (buttons, label) ->
            SegmentedButton(
                selected = buttons == selected,
                onClick = { onSelect(buttons) },
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
            FilterChip(
                selected = category.id in pinnedIds,
                onClick = { onToggle(category.id) },
                label = { Text(category.name, maxLines = 1) },
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

/**
 * Three options, not four: "transparent" is no longer offered - the widget
 * always sits on a solid app surface, so it never has to watch the wallpaper
 * to stay readable. Legacy widgets that still store it read back as SYSTEM
 * (see [QuickAddWidgetPrefs.read]).
 */
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

/** Matches the widget's own tile wash. */
private const val AvatarWashAlpha = 0.16f
