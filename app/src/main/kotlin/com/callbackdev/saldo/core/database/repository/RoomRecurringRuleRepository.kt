package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class RoomRecurringRuleRepository @Inject constructor(
    private val recurringRuleDao: RecurringRuleDao,
) : RecurringRuleRepository {

    override fun observeRules(): Flow<List<RecurringRule>> =
        recurringRuleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRules(): List<RecurringRule> =
        recurringRuleDao.getAll().map { it.toDomain() }

    override suspend fun getRule(id: Long): RecurringRule? =
        recurringRuleDao.getById(id)?.toDomain()

    override suspend fun upsert(rule: RecurringRule): Long {
        val entity = rule.toEntity()
        return if (entity.id == 0L) {
            recurringRuleDao.insert(entity)
        } else {
            recurringRuleDao.update(entity)
            entity.id
        }
    }

    override suspend fun delete(rule: RecurringRule) = recurringRuleDao.delete(rule.toEntity())

    override suspend fun updateLastReminderDate(ruleId: Long, date: LocalDate) =
        recurringRuleDao.updateLastReminder(ruleId, date.toEpochDay())
}
