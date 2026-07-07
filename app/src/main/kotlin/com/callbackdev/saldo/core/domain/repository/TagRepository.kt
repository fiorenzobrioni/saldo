package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** Read/write access to tags and their assignment to movements. */
interface TagRepository {

    /** All tags ordered by name. */
    fun observeTags(): Flow<List<Tag>>

    /** Tags currently attached to [transactionId]. */
    fun observeTagsForTransaction(transactionId: Long): Flow<List<Tag>>

    /** Inserts a new tag (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(tag: Tag): Long

    suspend fun delete(tag: Tag)

    /** Replaces the set of tags attached to [transactionId] with [tagIds]. */
    suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>)
}
