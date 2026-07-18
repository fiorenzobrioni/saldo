package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.SavingsGoalDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomSavingsGoalRepository @Inject constructor(
    private val savingsGoalDao: SavingsGoalDao,
) : SavingsGoalRepository {

    override fun observeGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getGoals(): List<SavingsGoal> =
        savingsGoalDao.getAll().map { it.toDomain() }

    override suspend fun getGoal(id: Long): SavingsGoal? =
        savingsGoalDao.getById(id)?.toDomain()

    override suspend fun getGoalForAccount(accountId: Long): SavingsGoal? =
        savingsGoalDao.getByAccountId(accountId)?.toDomain()

    override suspend fun upsert(goal: SavingsGoal): Long {
        val entity = goal.toEntity()
        return if (entity.id == 0L) {
            savingsGoalDao.insert(entity)
        } else {
            savingsGoalDao.update(entity)
            entity.id
        }
    }

    override suspend fun deleteGoal(id: Long) = savingsGoalDao.deleteById(id)
}
