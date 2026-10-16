package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.ReorderableListState
import com.callbackdev.saldo.core.designsystem.component.rememberReorderableListState
import com.callbackdev.saldo.core.designsystem.component.reorderableHandle
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.designsystem.theme.widgetCardContainer
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first

/**
 * One widget's settings, in two flavours served by the same activity: the
 * grid's and the bar's. Both read top to bottom the same way (16 Oct 2026):
 * the widget at the sizes it can be given; where its entries go (the account,
 * always first: it is where the widget's data goes, not how it looks); what
 * it is painted on and how solid; then what it carries. The option that does
 * not apply to a flavour is simply not there.
 *
 * Every choice is saved as it is made and repaints this one widget; "Done"
 * only closes the door, and so does the back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddWidgetConfigScreen(
    state: QuickAddWidgetConfigUiState,
    isBar: Boolean,
    theme: QuickAddWidgetTheme,
    placed: DpSize?,
    onAccountSelected: (Long?) -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onShowAppShortcutChanged: (Boolean) -> Unit,
    onButtonsSelected: (WidgetActionButtons) -> Unit,
    onCustomCategoriesChanged: (Boolean) -> Unit,
    onCategoryToggled: (Long) -> Unit,
    onPinnedReordered: (List<Long>) -> Unit,
    onLookChanged: (QuickAddWidgetConfig) -> Unit,
    onOpacityDrag: (Int) -> Unit,
    onOpacityDone: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        // Until the stored configuration is read, nothing: a screen of defaults
        // about to change would show the reader choices in the wrong place.
        if (state.isLoading) return@Scaffold
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            // On an open foldable or a phone on its side the column stays a
            // phone's width, centred, instead of stretching rows across it.
            val side = ((maxWidth - PageMaxWidth) / 2).coerceAtLeast(0.dp)
            ConfigList(
                state = state,
                isBar = isBar,
                theme = theme,
                placed = placed,
                contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
                actions = ConfigActions(
                    onAccountSelected = onAccountSelected,
                    onTypeSelected = onTypeSelected,
                    onShowAppShortcutChanged = onShowAppShortcutChanged,
                    onButtonsSelected = onButtonsSelected,
                    onCustomCategoriesChanged = onCustomCategoriesChanged,
                    onCategoryToggled = onCategoryToggled,
                    onPinnedReordered = onPinnedReordered,
                    onLookChanged = onLookChanged,
                    onOpacityDrag = onOpacityDrag,
                    onOpacityDone = onOpacityDone,
                    onReset = onReset,
                    onDone = onDone,
                ),
            )
        }
    }
}

/** What the reader can do on this screen, passed down as one thing. */
private class ConfigActions(
    val onAccountSelected: (Long?) -> Unit,
    val onTypeSelected: (TransactionType) -> Unit,
    val onShowAppShortcutChanged: (Boolean) -> Unit,
    val onButtonsSelected: (WidgetActionButtons) -> Unit,
    val onCustomCategoriesChanged: (Boolean) -> Unit,
    val onCategoryToggled: (Long) -> Unit,
    val onPinnedReordered: (List<Long>) -> Unit,
    val onLookChanged: (QuickAddWidgetConfig) -> Unit,
    val onOpacityDrag: (Int) -> Unit,
    val onOpacityDone: () -> Unit,
    val onReset: () -> Unit,
    val onDone: () -> Unit,
)

@Composable
private fun ConfigList(
    state: QuickAddWidgetConfigUiState,
    isBar: Boolean,
    theme: QuickAddWidgetTheme,
    placed: DpSize?,
    contentPadding: PaddingValues,
    actions: ConfigActions,
) {
    // The pinned rows are reorderable in place, inside this same list. The
    // live order is a local mirror (the drag mutates it synchronously, the
    // ViewModel hears about it once, at drop), and every mapping between the
    // drag's list indices and the pinned categories goes through the rows'
    // stable keys: the rows sit among other settings items, so a positional
    // offset would break the day a section is added above them.
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
        onSettle = { actions.onPinnedReordered(pinnedIds.toList()) },
        canMove = { from, to ->
            listState.pinnedIdAt(from) != null && listState.pinnedIdAt(to) != null
        },
    )
    // Re-adopt the source of truth (a removal, an addition, a reset) once any
    // in-flight drag has settled; mid-drag the local order is the truth.
    LaunchedEffect(state.config.pinnedCategoryIds) {
        snapshotFlow { reorderState.isDragging }.first { !it }
        if (pinnedIds.toList() != state.config.pinnedCategoryIds) {
            pinnedIds.clear()
            pinnedIds.addAll(state.config.pinnedCategoryIds)
        }
    }
    val config = state.config

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "preview") {
            // The preview leads: every control below changes what is drawn
            // here, so the choice is made by looking rather than by placing
            // the widget and coming back.
            QuickAddWidgetPreviewSection(
                data = rememberPreviewData(state),
                theme = theme,
                bar = isBar,
                placed = placed,
            )
        }
        item(key = "account") {
            ConfigHeader(stringResource(R.string.widget_config_account))
            AccountChoices(state.accounts, config.accountId, actions.onAccountSelected)
        }
        item(key = "background") {
            ConfigHeader(stringResource(R.string.widget_config_background))
            ConfigGroup {
                BackgroundChoices(config, theme.dress, actions.onLookChanged)
                ConfigDivider()
                OpacityRow(config.opacityPct, actions.onOpacityDrag, actions.onOpacityDone)
            }
        }
        if (isBar) {
            item(key = "buttons") {
                ConfigHeader(stringResource(R.string.widget_config_buttons))
                ConfigChoices(
                    options = listOf(
                        WidgetActionButtons.BOTH to stringResource(R.string.widget_config_buttons_both),
                        WidgetActionButtons.EXPENSE_ONLY to stringResource(R.string.widget_config_buttons_expense),
                        WidgetActionButtons.INCOME_ONLY to stringResource(R.string.widget_config_buttons_income),
                    ),
                    selected = config.buttons,
                    onPick = actions.onButtonsSelected,
                )
            }
            item(key = "content") {
                ConfigHeader(stringResource(R.string.widget_config_content))
                ConfigGroup {
                    ConfigSwitchRow(
                        label = stringResource(R.string.widget_config_app_shortcut),
                        note = stringResource(R.string.widget_config_app_shortcut_caption),
                        checked = config.showAppShortcut,
                        onToggle = actions.onShowAppShortcutChanged,
                    )
                }
            }
        } else {
            item(key = "type") {
                ConfigHeader(stringResource(R.string.widget_config_type))
                ConfigChoices(
                    options = listOf(
                        TransactionType.EXPENSE to stringResource(R.string.widget_quick_add_expense),
                        TransactionType.INCOME to stringResource(R.string.widget_quick_add_income),
                    ),
                    selected = config.type,
                    onPick = actions.onTypeSelected,
                )
            }
            item(key = "content") {
                ConfigHeader(stringResource(R.string.widget_config_content))
                ConfigGroup {
                    ConfigSwitchRow(
                        label = stringResource(R.string.widget_config_custom_categories),
                        note = stringResource(R.string.widget_config_custom_categories_caption),
                        checked = config.usesCustomCategories,
                        onToggle = actions.onCustomCategoriesChanged,
                    )
                }
            }
            if (config.usesCustomCategories) {
                pinnedCategories(state, pinnedIds, listState, reorderState, actions.onCategoryToggled)
            }
        }
        item(key = "actions") {
            ConfigResetButton(actions.onReset)
            ConfigDoneButton(actions.onDone)
        }
    }
}

/**
 * The hand-picked categories, as one group drawn across several list items:
 * each pinned row is its own item so it can be dragged, and the group's
 * rounded ends sit on the first and the last of them.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.pinnedCategories(
    state: QuickAddWidgetConfigUiState,
    pinnedIds: List<Long>,
    listState: LazyListState,
    reorderState: ReorderableListState,
    onCategoryToggled: (Long) -> Unit,
) {
    val pinned = pinnedIds.mapNotNull { id -> state.categories.firstOrNull { it.id == id } }
    val remaining = state.categories.filterNot { it.id in pinnedIds }
    item(key = "pinned-header") {
        ConfigHeader(stringResource(R.string.widget_config_pinned))
        ConfigNote(stringResource(R.string.widget_config_pinned_caption))
    }
    items(items = pinned, key = { category -> pinnedKeyOf(category.id) }) { category ->
        val index = pinned.indexOf(category)
        val isDragging = reorderState.isDraggingKey(pinnedKeyOf(category.id), listState)
        val rowModifier = if (isDragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer { translationY = reorderState.draggingItemOffset }
        } else {
            Modifier.animateItem()
        }
        val currentKey by rememberUpdatedState(pinnedKeyOf(category.id))
        Column(
            rowModifier.groupSegment(
                first = index == 0,
                last = index == pinned.lastIndex && remaining.isEmpty(),
                raised = isDragging,
            ),
        ) {
            if (index > 0 && !isDragging) ConfigDivider()
            PinnedCategoryRow(
                category = category,
                onRemove = { onCategoryToggled(category.id) },
                dragHandleModifier = Modifier.reorderableHandle(
                    state = reorderState,
                    key = category.id,
                    index = { listState.indexOfKey(currentKey) },
                ),
            )
        }
    }
    if (remaining.isNotEmpty()) {
        item(key = "pinned-add") {
            Column(Modifier.groupSegment(first = pinned.isEmpty(), last = true, raised = false)) {
                if (pinned.isNotEmpty()) ConfigDivider()
                Text(
                    text = stringResource(R.string.widget_config_pinned_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    remaining.forEach { category ->
                        FilterChip(
                            selected = false,
                            onClick = { onCategoryToggled(category.id) },
                            label = { Text(category.name, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One slice of a [ConfigGroup] drawn across list items: the group's margin and
 * ground, its rounded corners only where the group begins and ends. A row held
 * by the drag rises onto a higher container, so it reads as lifted.
 */
@Composable
private fun Modifier.groupSegment(first: Boolean, last: Boolean, raised: Boolean): Modifier {
    val top = if (first) ConfigGroupCorner else 0.dp
    val bottom = if (last) ConfigGroupCorner else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val colors = MaterialTheme.colorScheme
    return this
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(if (raised) ConfigGroupShape else shape)
        .background(if (raised) colors.surfaceContainerHigh else colors.surfaceContainerLow)
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

/** The widest the page's column grows on an open foldable or a phone on its side. */
private val PageMaxWidth = 640.dp

/**
 * Where the widget's entries go: the app's default account, which follows the
 * app when its default changes, or one account, so two widgets can record on
 * two accounts.
 */
@Composable
private fun AccountChoices(accounts: List<Account>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    ConfigGroup {
        Column(Modifier.selectableGroup()) {
            ConfigChoiceRow(
                label = stringResource(R.string.widget_config_account_default),
                selected = selectedId == null,
                onPick = { onSelect(null) },
            )
            accounts.forEach { account ->
                ConfigChoiceRow(
                    label = account.name,
                    selected = account.id == selectedId,
                    onPick = { onSelect(account.id) },
                )
            }
        }
    }
}

/**
 * One pinned category: avatar in the widget's own visual language, the name,
 * a way out and a drag handle. Removing the last one flips the grid back to
 * the app's own category order, which is what an empty pinned list means.
 */
@Composable
private fun PinnedCategoryRow(category: Category, onRemove: () -> Unit, dragHandleModifier: Modifier) {
    val accent = CategoryVisuals.color(category.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                contentDescription = stringResource(R.string.widget_config_pinned_remove_a11y, category.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = dragHandleModifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = stringResource(R.string.widget_config_pinned_reorder_a11y),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Matches the widget's own tile wash. */
private const val AvatarWashAlpha = 0.16f

/** What the background question offers, in order. */
internal val WidgetBackgroundChoices: List<Pair<WidgetBackground, Int>> = listOf(
    WidgetBackground.LIGHT to R.string.widget_bg_light,
    WidgetBackground.DARK to R.string.widget_bg_dark,
    WidgetBackground.SYSTEM to R.string.widget_bg_system,
    WidgetBackground.COLOR to R.string.widget_bg_color,
)

/** The six, in their order. Every [WidgetCardColor] has its swatch. */
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
 * one's name under it (a swatch is never the only label).
 */
@Composable
private fun BackgroundChoices(
    config: QuickAddWidgetConfig,
    dress: WidgetDress,
    onChange: (QuickAddWidgetConfig) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        WidgetBackgroundChoices.forEach { (background, label) ->
            ConfigChoiceRow(
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
    Column(modifier = Modifier.padding(start = 50.dp, end = 10.dp, bottom = 12.dp)) {
        // Each colour is a 48dp target around its 36dp disc (the chosen one's
        // included), side by side: the gaps between the discs are the targets'
        // own. A narrow phone wraps the row rather than squeezing the last disc
        // into an oval, which a fixed row of six did at 360dp.
        FlowRow(modifier = Modifier.selectableGroup()) {
            WidgetCardColorChoices.forEach { (color, label) ->
                val chosen = color == selected
                val name = stringResource(label)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .selectable(selected = chosen, onClick = { onPick(color) }, role = Role.RadioButton)
                        .semantics { contentDescription = name },
                ) {
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
                            .background(widgetCardContainer(color)),
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
        }
        Text(
            text = stringResource(WidgetCardColorChoices.first { it.first == selected }.second),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
