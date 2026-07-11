package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.mapper.toBackup
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.backup.BackupData
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import javax.inject.Inject

/**
 * Room-backed snapshot and restore of the whole database.
 *
 * Both operations run in a single transaction: the snapshot is a consistent
 * point-in-time read across every table, and the restore either replaces
 * everything or (on any failure, e.g. a malformed foreign key in the file)
 * rolls back leaving the current data untouched. Row ids are preserved on
 * restore so cross-references (movements to accounts, tag assignments, ...)
 * survive as they are. Table order respects the foreign keys: children are
 * deleted first and inserted last.
 */
class RoomBackupRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val transactionRunner: TransactionRunner,
) : BackupRepository {

    override suspend fun createSnapshot(): BackupData = transactionRunner.inTransaction {
        BackupData(
            accounts = accountDao.getAll().map { it.toBackup() },
            categories = categoryDao.getAll().map { it.toBackup() },
            tags = tagDao.getAll().map { it.toBackup() },
            recurringRules = recurringRuleDao.getAll().map { it.toBackup() },
            transactions = transactionDao.getAll().map { it.toBackup() },
            transactionTags = tagDao.getAllCrossRefs().map { it.toBackup() },
        )
    }

    override suspend fun restore(data: BackupData) {
        transactionRunner.inTransaction {
            tagDao.deleteAllCrossRefs()
            transactionDao.deleteAll()
            recurringRuleDao.deleteAll()
            tagDao.deleteAll()
            categoryDao.deleteAll()
            accountDao.deleteAll()

            accountDao.insertAll(data.accounts.map { it.toEntity() })
            categoryDao.insertAll(data.categories.map { it.toEntity() })
            tagDao.insertAll(data.tags.map { it.toEntity() })
            recurringRuleDao.insertAll(data.recurringRules.map { it.toEntity() })
            transactionDao.insertAll(data.transactions.map { it.toEntity() })
            tagDao.insertCrossRefs(data.transactionTags.map { it.toEntity() })
        }
    }
}
