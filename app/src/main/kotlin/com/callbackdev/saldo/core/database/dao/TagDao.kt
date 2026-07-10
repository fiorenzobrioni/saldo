package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN transaction_tag_cross_ref x ON x.tagId = t.id
        WHERE x.transactionId = :transactionId
        ORDER BY t.name ASC
        """,
    )
    fun observeForTransaction(transactionId: Long): Flow<List<TagEntity>>

    /** Every movement-tag assignment, for filtering the ledger by tag. */
    @Query("SELECT * FROM transaction_tag_cross_ref")
    fun observeAllCrossRefs(): Flow<List<TransactionTagCrossRef>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun clearCrossRefs(transactionId: Long)

    /** Replaces the tag set of a movement atomically. */
    @Transaction
    suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>) {
        clearCrossRefs(transactionId)
        tagIds.forEach { tagId -> insertCrossRef(TransactionTagCrossRef(transactionId, tagId)) }
    }
}
