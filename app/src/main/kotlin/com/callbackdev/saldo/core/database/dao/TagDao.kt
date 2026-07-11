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
@Suppress("TooManyFunctions") // A data-access interface naturally has many queries.
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity): Long

    /** Bulk insert with explicit ids, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

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

    /** One-shot dump of every tag, for backup export. */
    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun getAll(): List<TagEntity>

    /** One-shot dump of every movement-tag assignment, for backup export. */
    @Query("SELECT * FROM transaction_tag_cross_ref")
    suspend fun getAllCrossRefs(): List<TransactionTagCrossRef>

    /** Empties the tags table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    /** Empties the assignments table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM transaction_tag_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: TransactionTagCrossRef)

    /** Bulk insert of assignments, used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCrossRefs(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun clearCrossRefs(transactionId: Long)

    /** Replaces the tag set of a movement atomically. */
    @Transaction
    suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>) {
        clearCrossRefs(transactionId)
        tagIds.forEach { tagId -> insertCrossRef(TransactionTagCrossRef(transactionId, tagId)) }
    }
}
