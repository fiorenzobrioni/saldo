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

    /** Inserts a new category (id == 0) or updates an existing one. Returns its id. */
    suspend fun upsert(category: Category): Long

    suspend fun delete(category: Category)
}
