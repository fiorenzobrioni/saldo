package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeCategories(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeByType(type.name).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: Long): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun nextSortOrder(): Int = categoryDao.maxSortOrder() + 1

    override suspend fun upsert(category: Category): Long {
        val entity = category.toEntity()
        return if (entity.id == 0L) {
            categoryDao.insert(entity)
        } else {
            categoryDao.update(entity)
            entity.id
        }
    }

    override suspend fun reorder(categories: List<Category>) {
        val reordered = categories.mapIndexed { index, category ->
            category.toEntity().copy(sortOrder = index)
        }
        categoryDao.updateAll(reordered)
    }

    override suspend fun delete(category: Category) = categoryDao.delete(category.toEntity())

    override suspend fun deleteWithReassignment(category: Category, targetCategoryId: Long) =
        categoryDao.reassignTransactionsAndDelete(category.id, targetCategoryId)
}
