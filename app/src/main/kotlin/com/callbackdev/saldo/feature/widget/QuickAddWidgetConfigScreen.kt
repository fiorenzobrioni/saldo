@file:Suppress("TooManyFunctions") // One small composable per settings row/section, as in SettingsScreen.

package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.ReorderableListState
import com.callbackdev.saldo.core.designsystem.component.SettingsGroup
import com.callbackdev.saldo.core.designsystem.component.SettingsSectionHeader
import com.callbackdev.saldo.core.designsystem.component.rememberReorderableListState
import com.callbackdev.saldo.core.designsystem.component.reorderableHandle
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.theme.widgetCardContainer
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

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
    onLookChanged: (QuickAddWidgetConfig) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.saldoSurfaces.canvas,
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
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "preview") {
                // The preview leads: every control below changes what is drawn
                // here, so the choice is made by looking rather than by placing
                // the widget and coming back.
                QuickAddWidgetPreview(
                    data = rememberPreviewData(state),
                    theme = theme,
                    bar = isBar,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            item(key = "background") {
                SettingsSectionHeader(stringResource(R.string.widget_config_background))
                SettingsGroup {
                    BackgroundChoices(state.config, theme.dress, onLookChanged)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    OpacityRow(state.config.opacityPct) { onLookChanged(state.config.copy(opacityPct = it)) }
                }
            }
            item(key = "content") {
                SettingsSectionHeader(stringResource(R.string.widget_config_content))
                SettingsGroup {
                    if (!isBar) {
                        Section(stringResource(R.string.widget_config_type)) {
                            TypeSelector(state.config.type, onTypeSelected)
                        }
                    }
                    Section(stringResource(R.string.widget_config_account)) {
                        AccountChips(state.accounts, state.config.accountId, onAccountSelected)
                    }
                    if (isBar) {
                        Section(stringResource(R.string.widget_config_buttons)) {
                            ButtonsSelector(state.config.buttons, onButtonsSelected)
                        }
                        SwitchRow(
                            title = stringResource(R.string.widget_config_app_shortcut),
                            subtitle = stringResource(R.string.widget_config_app_shortcut_caption),
                            checked = state.config.showAppShortcut,
                            onCheckedChange = onShowAppShortcutChanged,
                        )
                    } else {
                        SwitchRow(
                            title = stringResource(R.string.widget_config_custom_categories),
                            subtitle = stringResource(R.string.widget_config_custom_categories_caption),
                            checked = state.config.usesCustomCategories,
                            onCheckedChange = onCustomCategoriesChanged,
                        )
                    }
                }
            }
            if (!isBar) {
                if (state.config.usesCustomCategories) {
                    item(key = "pinned-header") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        ) {
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
                            modifier = rowModifier.padding(horizontal = 16.dp),
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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

/** Matches the widget's own tile wash. */
private const val AvatarWashAlpha = 0.16f

/** What the background question offers, in Chiaro's order less its sky, as Passo offers it. */
internal val WidgetBackgroundChoices: List<Pair<WidgetBackground, Int>> = listOf(
    WidgetBackground.LIGHT to R.string.widget_bg_light,
    WidgetBackground.DARK to R.string.widget_bg_dark,
    WidgetBackground.SYSTEM to R.string.widget_bg_system,
    WidgetBackground.COLOR to R.string.widget_bg_color,
)

/** Chiaro's six, in Chiaro's order. Every [WidgetCardColor] has its swatch. */
internal val WidgetCardColorChoices: List<Pair<WidgetCardColor, Int>> = listOf(
    WidgetCardColor.BLUE to R.string.widget_color_blue,
    WidgetCardColor.AZURE to R.string.widget_color_azure,
    WidgetCardColor.GREEN to R.string.widget_color_green,
    WidgetCardColor.TEAL to R.string.widget_color_teal,
    WidgetCardColor.PLUM to R.string.widget_color_plum,
    WidgetCardColor.CLAY to R.string.widget_color_clay,
)

/**
 * What kind of card, each row with a swatch of the ground it paints; and, once
 * "A colour" is picked, which colour, as a strip of swatches with the chosen
 * one's name under it (a swatch is never the only label). Chiaro's and Passo's
 * group, row for row.
 */
@Composable
private fun BackgroundChoices(
    config: QuickAddWidgetConfig,
    dress: WidgetDress,
    onChange: (QuickAddWidgetConfig) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        WidgetBackgroundChoices.forEach { (background, label) ->
            ChoiceRow(
                label = stringResource(label),
                selected = config.background == background,
                onPick = { onChange(config.copy(background = background)) },
            ) {
                val shape = RoundedCornerShape(10.dp)
                val swatch = Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                if (background == WidgetBackground.SYSTEM) {
                    // The phone's choice, drawn as both of its answers on a diagonal.
                    Canvas(swatch) {
                        drawRect(dress.lightScheme.surface)
                        drawPath(
                            Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            },
                            dress.darkScheme.surface,
                        )
                    }
                } else {
                    Box(swatch.background(dress.ground(background, config.cardColor, night = false)))
                }
            }
        }
    }
    if (config.background == WidgetBackground.COLOR) {
        ColorSwatches(config.cardColor) { onChange(config.copy(cardColor = it)) }
    }
}

@Composable
private fun ColorSwatches(selected: WidgetCardColor, onPick: (WidgetCardColor) -> Unit) {
    Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
            WidgetCardColorChoices.forEach { (color, label) ->
                val chosen = color == selected
                val name = stringResource(label)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (chosen) 2.dp else 0.dp,
                            color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(if (chosen) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(widgetCardContainer(color))
                        .selectable(selected = chosen, onClick = { onPick(color) }, role = Role.RadioButton)
                        .semantics { contentDescription = name },
                ) {
                    if (chosen) {
                        // Every card colour is a dark ground under white ink.
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(WidgetCardColorChoices.first { it.first == selected }.second),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onPick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        trailing()
    }
}

/**
 * How solid the card is: the name and the value on one line, the slider under
 * them, in steps of 5%. The value is said in words at the two ends.
 */
@Composable
private fun OpacityRow(pct: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.widget_config_opacity),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when (pct) {
                    FullOpacity -> stringResource(R.string.widget_opacity_full)
                    0 -> stringResource(R.string.widget_opacity_transparent)
                    else -> stringResource(R.string.widget_opacity_percent, pct)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { raw -> onChange((raw / OpacityStep).roundToInt() * OpacityStep) },
            valueRange = 0f..FullOpacity.toFloat(),
            steps = FullOpacity / OpacityStep - 1,
        )
    }
}
