package com.callbackdev.saldo.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.tag.TagNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tag management (Phase 16): rename, merge and delete over the existing tag
 * and cross-ref tables. Every operation is a plain repository call - the only
 * domain rule here is the name normalization shared with the editor's inline
 * creation ([TagNames]) and the collision check it enables.
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val sort = MutableStateFlow(TagSort.USAGE)
    private val sheetTagId = MutableStateFlow<Long?>(null)
    private val dialog = MutableStateFlow<TagsDialog?>(null)

    /**
     * The search text, mirrored synchronously so the field's value never
     * round-trips through the state combine while the user is typing.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Sheet and dialog, pre-combined to stay within combine's arity. */
    private val overlays = combine(sheetTagId, dialog, ::Pair)

    val uiState: StateFlow<TagsUiState> = combine(
        tagRepository.observeTags(),
        tagRepository.observeTagUsage(),
        sort,
        _searchQuery,
        overlays,
    ) { tags, usage, sortOrder, query, (sheetId, activeDialog) ->
        val items = tags.map { TagListItem(tag = it, movementCount = usage[it.id] ?: 0) }
        TagsUiState(
            isLoading = false,
            tags = items.matching(query).sortedWith(sortOrder.comparator),
            totalCount = items.size,
            sort = sortOrder,
            sheetTag = sheetId?.let { id -> items.firstOrNull { it.tag.id == id } },
            dialog = activeDialog,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TagsUiState(),
        )

    private val _events = Channel<TagsEvent>(Channel.BUFFERED)
    val events: Flow<TagsEvent> = _events.receiveAsFlow()

    fun onSortSelected(sort: TagSort) {
        this.sort.value = sort
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onTagClick(item: TagListItem) {
        sheetTagId.value = item.tag.id
    }

    fun dismissSheet() {
        sheetTagId.value = null
    }

    fun dismissDialog() {
        dialog.value = null
    }

    /**
     * Opens the rename dialog with a fresh snapshot of the other tags. The
     * collision is computed right away, not only on typing: a pre-existing
     * case-variant duplicate surfaces its merge proposal on open.
     */
    fun requestRename() {
        val item = uiState.value.sheetTag ?: return
        viewModelScope.launch {
            val others = tagRepository.observeTags().first().filter { it.id != item.tag.id }
            sheetTagId.value = null
            dialog.value = TagsDialog.Rename(
                tag = item.tag,
                input = item.tag.name,
                others = others,
                collision = others.firstOrNull { TagNames.sameName(it.name, item.tag.name) },
            )
        }
    }

    fun onRenameInputChange(input: String) {
        val current = dialog.value as? TagsDialog.Rename ?: return
        val collision = current.others.firstOrNull { TagNames.sameName(it.name, input) }
        dialog.value = current.copy(input = input, collision = collision)
    }

    /**
     * Saves the normalized name, or - when it collides with an existing tag,
     * as the dialog has already told the user - merges this tag into it.
     */
    fun confirmRename() {
        val current = dialog.value as? TagsDialog.Rename ?: return
        if (!current.canConfirm) return
        val collision = current.collision
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching {
                if (collision != null) {
                    tagRepository.merge(targetId = collision.id, sourceIds = setOf(current.tag.id))
                } else {
                    tagRepository.upsert(current.tag.copy(name = current.normalizedInput))
                }
            }
                .onSuccess {
                    if (collision != null) _events.send(TagsEvent.Merged(collision.name))
                }
                .onFailure { _events.send(TagsEvent.WriteFailed) }
        }
    }

    /** Opens the merge picker: every other tag is a candidate, most used first. */
    fun requestMerge() {
        val item = uiState.value.sheetTag ?: return
        viewModelScope.launch {
            val usage = tagRepository.observeTagUsage().first()
            val candidates = tagRepository.observeTags().first()
                .filter { it.id != item.tag.id }
                .map { TagListItem(tag = it, movementCount = usage[it.id] ?: 0) }
                .sortedWith(TagSort.USAGE.comparator)
            sheetTagId.value = null
            dialog.value = TagsDialog.Merge(target = item, candidates = candidates)
        }
    }

    fun onMergeSourceToggled(tagId: Long) {
        val current = dialog.value as? TagsDialog.Merge ?: return
        val ids = current.selectedIds
        dialog.value = current.copy(selectedIds = if (tagId in ids) ids - tagId else ids + tagId)
    }

    fun confirmMerge() {
        val current = dialog.value as? TagsDialog.Merge ?: return
        if (current.selectedIds.isEmpty()) return
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching {
                tagRepository.merge(targetId = current.target.tag.id, sourceIds = current.selectedIds)
            }
                .onSuccess { _events.send(TagsEvent.Merged(current.target.tag.name)) }
                .onFailure { _events.send(TagsEvent.WriteFailed) }
        }
    }

    /** Asks to confirm the deletion; the count comes from the tapped row. */
    fun requestDelete() {
        val item = uiState.value.sheetTag ?: return
        sheetTagId.value = null
        dialog.value = TagsDialog.ConfirmDelete(tag = item.tag, movementCount = item.movementCount)
    }

    fun confirmDelete() {
        val current = dialog.value as? TagsDialog.ConfirmDelete ?: return
        dialog.value = null
        viewModelScope.launch {
            suspendRunCatching { tagRepository.delete(current.tag) }
                .onSuccess { _events.send(TagsEvent.Deleted(current.tag.name)) }
                .onFailure { _events.send(TagsEvent.WriteFailed) }
        }
    }

    private fun List<TagListItem>.matching(query: String): List<TagListItem> {
        val key = TagNames.key(query)
        if (key.isEmpty()) return this
        return filter { key in TagNames.key(it.tag.name) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
