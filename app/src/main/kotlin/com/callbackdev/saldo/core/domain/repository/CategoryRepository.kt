package com.callbackdev.saldo.core.domain.repository

import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/** Read/write access to categories. */
interface CategoryRepository {

    /** All categories ordered for display. */
    fun observeCategories(): Flow<List<Category>>

    /** Categories usable for a given movement [type] (matching type or BOTH). */
    fun observeCategories(type: CategoryType): Flow<List<Category>>

    suspend fun getCategory(id: Long): Category?

    /** Next free sort position, so a freshly created category appends to the end. */
    suspend fun nextSortOrder(): Int

    /** Inserts a new category (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(category: Category): Long

    /** Persists a manual reorder: each category's sort position becomes its index. */
    suspend fun reorder(categories: List<Category>)

    suspend fun delete(category: Category)

    /**
     * Reassigns every movement of [category] to [targetCategoryId] and then deletes
     * the category, atomically. Used when a deleted category still labels movements.
     */
    suspend fun deleteWithReassignment(category: Category, targetCategoryId: Long)
}
