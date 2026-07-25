package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.BudgetDao
import com.callbackdev.saldo.core.database.dao.CategoryDao
import com.callbackdev.saldo.core.database.dao.RecurringRuleDao
import com.callbackdev.saldo.core.database.dao.SavingsGoalDao
import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import android.content.Context
import com.callbackdev.saldo.core.database.mapper.toBackup
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.database.seed.DefaultCategories
import com.callbackdev.saldo.core.domain.backup.BackupData
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import dagger.hilt.android.qualifiers.ApplicationContext
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
@Suppress("LongParameterList") // One DAO per backed-up table, by design.
class RoomBackupRepository @Inject constructor(
    // Only for the localized default categories replanted by eraseAll, the same
    // resource-backed seed the Room onCreate callback uses.
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
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
            budgets = budgetDao.getAll().map { it.toBackup() },
            savingsGoals = savingsGoalDao.getAll().map { it.toBackup() },
        )
    }

    override suspend fun eraseAll() {
        transactionRunner.inTransaction {
            deleteEverything()
            // Room's onCreate seed never runs again on an existing file, so the
            // defaults are replanted here: a wipe must land on the same state a
            // fresh install starts from, not on an app without categories.
            categoryDao.insertAll(DefaultCategories.build(context))
        }
    }

    override suspend fun restore(data: BackupData) {
        transactionRunner.inTransaction {
            deleteEverything()

            accountDao.insertAll(data.accounts.map { it.toEntity() })
            categoryDao.insertAll(data.categories.map { it.toEntity() })
            tagDao.insertAll(data.tags.map { it.toEntity() })
            recurringRuleDao.insertAll(data.recurringRules.map { it.toEntity() })
            transactionDao.insertAll(data.transactions.map { it.toEntity() })
            tagDao.insertCrossRefs(data.transactionTags.map { it.toEntity() })
            budgetDao.insertAll(data.budgets.map { it.toEntity() })
            savingsGoalDao.insertAll(data.savingsGoals.map { it.toEntity() })
        }
    }

    /** Empties every table, children first, so no foreign key is ever dangling. */
    private suspend fun deleteEverything() {
        tagDao.deleteAllCrossRefs()
        transactionDao.deleteAll()
        recurringRuleDao.deleteAll()
        budgetDao.deleteAll()
        savingsGoalDao.deleteAll()
        tagDao.deleteAll()
        categoryDao.deleteAll()
        accountDao.deleteAll()
    }
}
