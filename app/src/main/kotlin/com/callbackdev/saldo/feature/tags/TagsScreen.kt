package com.callbackdev.saldo.feature.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.ListSkeleton
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn

/**
 * Tag management (Phase 16): one row per tag with how many movements carry it,
 * ordered by use or by name, searchable once the list grows. A row opens the
 * quick actions sheet (rename, merge, delete); merge and delete confirm in a
 * dialog rather than offering an undo, because they touch many rows at once
 * and a partial restore would lie.
 *
 * Tags are not created here: they are born inline in the movement editor, and
 * the empty state says so instead of duplicating that entry point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is TagsEvent.Merged ->
                    resources.getString(R.string.tags_snackbar_merged, event.targetName)

                is TagsEvent.Deleted ->
                    resources.getString(R.string.tags_snackbar_deleted, event.tagName)

                TagsEvent.WriteFailed -> resources.getString(R.string.editor_write_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.tags_title)) },
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
    ) { innerPadding ->
        when {
            uiState.isLoading -> ListSkeleton(modifier = Modifier.padding(innerPadding))

            uiState.isEmpty -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.Label,
                title = stringResource(R.string.tags_empty_title),
                body = stringResource(R.string.tags_empty_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> TagsContent(
                uiState = uiState,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSortSelected = viewModel::onSortSelected,
                onTagClick = viewModel::onTagClick,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    uiState.sheetTag?.let { item ->
        TagActionsSheet(
            item = item,
            canMerge = uiState.totalCount > 1,
            onDismiss = viewModel::dismissSheet,
            onRename = viewModel::requestRename,
            onMerge = viewModel::requestMerge,
            onDelete = viewModel::requestDelete,
        )
    }

    TagsDialogHost(
        dialog = uiState.dialog,
        onRenameInputChange = viewModel::onRenameInputChange,
        onConfirmRename = viewModel::confirmRename,
        onMergeSourceToggled = viewModel::onMergeSourceToggled,
        onConfirmMerge = viewModel::confirmMerge,
        onConfirmDelete = viewModel::confirmDelete,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun TagsContent(
    uiState: TagsUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSortSelected: (TagSort) -> Unit,
    onTagClick: (TagListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // The field also stays while a query is active, even if deletions have
        // shrunk the list below the threshold: a filter that cannot be edited
        // or cleared must never happen.
        if (uiState.isSearchAvailable || searchQuery.isNotEmpty()) {
            TagSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        if (uiState.isSortAvailable) {
            TagSortSelector(
                selected = uiState.sort,
                onSelected = onSortSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (uiState.hasNoResults) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.tags_no_results_title),
                body = stringResource(R.string.tags_no_results_body),
                actionLabel = stringResource(R.string.tags_search_clear),
                onAction = { onSearchQueryChange("") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
            ) {
                items(uiState.tags, key = { it.tag.id }) { item ->
                    TagRow(
                        item = item,
                        onClick = { onTagClick(item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** A flat pill on the canvas, matching the shape language of the cards below. */
@Composable
private fun TagSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.tags_search_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.tags_search_clear),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun TagSortSelector(
    selected: TagSort,
    onSelected: (TagSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = selected == TagSort.USAGE,
            onClick = { onSelected(TagSort.USAGE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(R.string.tags_sort_usage))
        }
        SegmentedButton(
            selected = selected == TagSort.NAME,
            onClick = { onSelected(TagSort.NAME) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.tags_sort_name))
        }
    }
}

/** One tag: avatar, name and how many movements carry it. Tapping opens the actions sheet. */
@Composable
private fun TagRow(
    item: TagListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            TagAvatar(name = item.tag.name)
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.tag.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = movementCountLabel(item.movementCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun movementCountLabel(count: Int): String =
    if (count == 0) {
        stringResource(R.string.tags_never_used)
    } else {
        pluralStringResource(R.plurals.tags_movement_count, count, count)
    }

/**
 * Initials on a tint derived from the name itself, the same trick as the
 * counterparty avatars: no color is stored for a tag, but a stable one makes
 * the list scannable and the same tag keeps the same tint everywhere.
 */
@Composable
internal fun TagAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = CategoryVisuals.color(tagColorKey(name))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(AvatarShape)
            .background(color),
    ) {
        Text(
            text = tagInitials(name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColorOn(color),
        )
    }
}

/** Up to two initials, from the first two words of the name. */
internal fun tagInitials(name: String): String = name
    .trim()
    .split(' ', '\t')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString(separator = "")
    .ifEmpty { "#" }

/** Stable palette color for a name, from its own characters (never persisted). */
internal fun tagColorKey(name: String): Int {
    val sum = name.trim().lowercase().sumOf { it.code }
    return CategoryVisuals.colors[sum % CategoryVisuals.colors.size]
}
