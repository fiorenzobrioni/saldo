package com.callbackdev.saldo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    /** Persists a new ordering (used by manual reorder). */
    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type OR type = 'BOTH'
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    fun observeByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    /** One-shot dump of every category, for backup export. */
    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAll(): List<CategoryEntity>

    /** Empties the table; only backup restore calls this, inside its transaction. */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Highest sortOrder in use, or -1 when the table is empty (new rows append). */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE transactions SET categoryId = :targetId WHERE categoryId = :categoryId")
    suspend fun reassignTransactions(categoryId: Long, targetId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteById(categoryId: Long)

    /**
     * Reassigns every movement of [categoryId] to [targetId] and then deletes the
     * category, atomically, so history is never left dangling mid-operation.
     */
    @Transaction
    suspend fun reassignTransactionsAndDelete(categoryId: Long, targetId: Long) {
        reassignTransactions(categoryId, targetId)
        deleteById(categoryId)
    }
}
