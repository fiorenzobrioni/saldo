package com.callbackdev.saldo.feature.categories

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.ReorderableListState
import com.callbackdev.saldo.core.designsystem.component.rememberReorderableListState
import com.callbackdev.saldo.core.designsystem.component.reorderableHandle
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType

/**
 * Category management: expenses and incomes split into two tabs, each with
 * manual drag-to-reorder. Tapping a category opens its editor; the FAB creates
 * a new one preset to the current tab's type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNewCategory: (CategoryType) -> Unit,
    onNavigateToEditCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabType = if (selectedTab == 0) CategoryType.EXPENSE else CategoryType.INCOME

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
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
            if (!uiState.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToNewCategory(tabType) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.categories_new)) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.categories_tab_expenses)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.categories_tab_incomes)) },
                )
            }

            when {
                uiState.isLoading -> LoadingState()

                else -> key(tabType) {
                    // Fresh reorder + scroll state per tab.
                    CategoryReorderList(
                        categories = uiState.forTab(tabType),
                        onEditCategory = onNavigateToEditCategory,
                        onReorder = { orderedIds -> viewModel.persistOrder(tabType, orderedIds) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryReorderList(
    categories: List<Category>,
    onEditCategory: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember { categories.toMutableStateList() }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableListState(
        listState = listState,
        onMove = { from, to -> items.add(to, items.removeAt(from)) },
        onSettle = { onReorder(items.map { it.id }) },
    )

    // Re-seed from the source of truth whenever it changes, unless the user is
    // mid-drag (the live list is authoritative until the drag settles).
    LaunchedResync(categories, reorderState, items)

    if (categories.isEmpty()) {
        CategoriesEmptyState(modifier = modifier)
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, category -> category.id }) { index, category ->
            val isDragging = reorderState.draggingItemIndex == index
            val rowModifier = if (isDragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = reorderState.draggingItemOffset }
            } else {
                Modifier.animateItem()
            }
            // Read lazily via a stable holder so the handle's pointerInput, keyed
            // on the id, always sees the row's current index without restarting.
            val currentIndex by rememberUpdatedState(index)
            CategoryRow(
                category = category,
                elevated = isDragging,
                onClick = { onEditCategory(category.id) },
                dragHandle = {
                    DragHandle(
                        modifier = Modifier.reorderableHandle(
                            state = reorderState,
                            key = category.id,
                            index = { currentIndex },
                        ),
                    )
                },
                modifier = rowModifier,
            )
        }
    }
}

/**
 * Adopts a new [source] order once any in-progress drag has settled. Keying on
 * [source] means a freshly persisted order (or the initial load) replaces the
 * live list, while a drop that has not yet round-tripped through the database
 * does not trigger a revert.
 */
@Composable
private fun LaunchedResync(
    source: List<Category>,
    reorderState: ReorderableListState,
    items: MutableList<Category>,
) {
    LaunchedEffect(source) {
        snapshotFlow { reorderState.isDragging }.first { !it }
        if (items.toList() != source) {
            items.clear()
            items.addAll(source)
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    elevated: Boolean,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (elevated) 6.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(category = category)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.type == CategoryType.BOTH) {
                    Text(
                        text = stringResource(R.string.category_type_both_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            dragHandle()
        }
    }
}

/** Squircle colored avatar with the category icon; reused by the editor preview. */
@Composable
internal fun CategoryAvatar(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val avatarColor = CategoryVisuals.color(category.color)
    Box(
        modifier = modifier
            .size(size)
            .clip(AvatarShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategoryVisuals.icon(category.icon),
            contentDescription = null,
            tint = contentColorOn(avatarColor),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.categories_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoriesEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = CategoryVisuals.icon(null),
        title = stringResource(R.string.categories_empty_title),
        body = stringResource(R.string.categories_empty_body),
        modifier = modifier,
    )
}
