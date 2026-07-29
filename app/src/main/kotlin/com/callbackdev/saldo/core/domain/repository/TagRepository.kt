package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** Read/write access to tags and their assignment to movements. */
interface TagRepository {

    /** All tags ordered by name. */
    fun observeTags(): Flow<List<Tag>>

    /** Tags currently attached to [transactionId]. */
    fun observeTagsForTransaction(transactionId: Long): Flow<List<Tag>>

    /** Every assignment at once, as transaction id to tag ids. */
    fun observeTagAssignments(): Flow<Map<Long, Set<Long>>>

    /** Movement count per tag id; a tag attached to no movement has no entry. */
    fun observeTagUsage(): Flow<Map<Long, Int>>

    /** Inserts a new tag (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(tag: Tag): Long

    suspend fun delete(tag: Tag)

    /**
     * Merges [sourceIds] into [targetId]: their movements move onto the target
     * (one that carried both keeps a single assignment) and the source tags are
     * deleted, in one transaction. The movements themselves are never touched.
     */
    suspend fun merge(targetId: Long, sourceIds: Set<Long>)

    /** Replaces the set of tags attached to [transactionId] with [tagIds]. */
    suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>)
}
