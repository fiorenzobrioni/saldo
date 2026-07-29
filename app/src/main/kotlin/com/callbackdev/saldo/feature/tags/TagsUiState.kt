package com.callbackdev.saldo.feature.tags

import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.tag.TagNames

/** How the tag list is ordered. */
enum class TagSort { USAGE, NAME }

/** How [TagSort] orders the rows; ties fall to the name, then the id, for stability. */
internal val TagSort.comparator: Comparator<TagListItem>
    get() = when (this) {
        TagSort.USAGE -> compareByDescending<TagListItem> { it.movementCount }
            .thenBy { TagNames.key(it.tag.name) }
            .thenBy { it.tag.id }

        TagSort.NAME -> compareBy<TagListItem> { TagNames.key(it.tag.name) }
            .thenBy { it.tag.id }
    }

/** One row of the list: the tag and how many movements carry it. */
data class TagListItem(
    val tag: Tag,
    val movementCount: Int,
)

/** Immutable UI state of the tag management screen. */
data class TagsUiState(
    val isLoading: Boolean = true,
    /** The rows to show: search applied, then the active sort. */
    val tags: List<TagListItem> = emptyList(),
    /** How many tags exist in total, before the search narrows them. */
    val totalCount: Int = 0,
    val sort: TagSort = TagSort.USAGE,
    /** The tag whose quick actions sheet is open, resolved against the live list. */
    val sheetTag: TagListItem? = null,
    val dialog: TagsDialog? = null,
) {
    /** No tags at all: the empty state explains where tags come from. */
    val isEmpty: Boolean get() = !isLoading && totalCount == 0

    /** Tags exist, but none matches the search. */
    val hasNoResults: Boolean get() = !isLoading && totalCount > 0 && tags.isEmpty()

    /** The search field appears once the list is long enough to need it. */
    val isSearchAvailable: Boolean get() = totalCount >= SEARCH_THRESHOLD

    /** With a single tag there is nothing to merge and nothing to reorder. */
    val isSortAvailable: Boolean get() = totalCount > 1

    companion object {
        /** Below this many tags, scanning the list beats typing in a field. */
        const val SEARCH_THRESHOLD = 8
    }
}

/** The modal currently requested by the screen, if any. */
sealed interface TagsDialog {

    /**
     * Rename [tag]. [others] is the rest of the tags, snapshotted when the
     * dialog opened, so every keystroke can check for a collision without a
     * query; [collision] is the tag whose name already matches the typed one,
     * in which case confirming merges the two instead of minting a duplicate
     * the database cannot catch (its unique index is case-sensitive).
     */
    data class Rename(
        val tag: Tag,
        val input: String,
        val others: List<Tag>,
        val collision: Tag? = null,
    ) : TagsDialog {

        /** What would actually be stored. */
        val normalizedInput: String get() = TagNames.normalize(input)

        /**
         * A blank or unchanged name has nothing to confirm - unless it
         * collides: a pre-existing case-variant duplicate (possible because
         * the unique index compares bytes) can then be merged right away,
         * without retyping the name.
         */
        val canConfirm: Boolean
            get() = normalizedInput.isNotEmpty() &&
                (collision != null || normalizedInput != tag.name)
    }

    /** Pick one or more [candidates] to merge into [target]. */
    data class Merge(
        val target: TagListItem,
        val candidates: List<TagListItem>,
        val selectedIds: Set<Long> = emptySet(),
    ) : TagsDialog

    /** Confirm the deletion of [tag], which [movementCount] movements carry. */
    data class ConfirmDelete(
        val tag: Tag,
        val movementCount: Int,
    ) : TagsDialog
}

/** One-shot feedback of the write operations, shown as snackbars. */
sealed interface TagsEvent {

    /** One or more tags were merged into [targetName]. */
    data class Merged(val targetName: String) : TagsEvent

    data class Deleted(val tagName: String) : TagsEvent

    data object WriteFailed : TagsEvent
}
