package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.database.entity.RecurringRuleEntity
import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.database.entity.TransactionTagCrossRef
import com.callbackdev.saldo.core.domain.backup.CategoryBackup
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Entity -> backup schema -> entity must be the identity for every field:
 * this is where a silently dropped column would lose user data.
 */
class BackupMapperTest {

    @Test
    fun `account survives the backup round trip`() {
        val entity = AccountEntity(
            id = 7L,
            name = "Revolut",
            type = AccountType.DIGITAL_WALLET,
            currency = "GBP",
            initialBalanceMinor = -12_345L,
            color = 0x112233,
            icon = "wallet",
            isIncludedInTotal = false,
            isIncludedInBudget = false,
            isArchived = true,
            sortOrder = 4,
            createdAtEpochMilli = 1_600_000_000_000,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `category survives the backup round trip`() {
        val entity = CategoryEntity(
            id = 3L,
            name = "Viaggi",
            type = CategoryType.BOTH,
            color = 0xFFAA00,
            icon = "flight",
            sortOrder = 9,
            sortOrderIncome = 5,
            isDefault = true,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `a pre-per-tab backup inherits the income order from sortOrder`() {
        // Older backups carry no income key: the income position must fall back
        // to the shared sortOrder so the restored order matches the old app.
        val legacy = CategoryBackup(
            id = 3L,
            name = "Viaggi",
            type = "BOTH",
            color = 0xFFAA00,
            icon = "flight",
            sortOrder = 9,
            sortOrderIncome = null,
            isDefault = true,
        )

        assertEquals(9, legacy.toEntity().sortOrderIncome)
    }

    @Test
    fun `tag survives the backup round trip`() {
        val entity = TagEntity(id = 5L, name = "work")

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `recurring rule with every optional field survives the backup round trip`() {
        val entity = RecurringRuleEntity(
            id = 11L,
            name = "Salary",
            type = TransactionType.INCOME,
            currency = "EUR",
            accountId = 7L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDateEpochDay = 19_000L,
            amountMinor = 250_000L,
            categoryId = 3L,
            dayOfReference = 27,
            endDateEpochDay = 25_000L,
            mode = RecurrenceMode.CONFIRM,
            isVariableAmount = true,
            lastGeneratedEpochDay = 20_500L,
            color = 0x00AA66,
            icon = "payments",
            note = "gross",
            lastReminderEpochDay = 20_490L,
            isPaused = true,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `transfer recurring rule survives the backup round trip`() {
        val entity = RecurringRuleEntity(
            id = 13L,
            name = "Savings",
            type = TransactionType.TRANSFER,
            currency = "EUR",
            accountId = 7L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDateEpochDay = 19_200L,
            amountMinor = 15_000L,
            categoryId = null,
            dayOfReference = 1,
            mode = RecurrenceMode.AUTOMATIC,
            transferAccountId = 8L,
            transferAmountMinor = 15_000L,
            transferCurrency = "EUR",
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `recurring rule with null optionals survives the backup round trip`() {
        val entity = RecurringRuleEntity(
            id = 12L,
            name = "Gym",
            type = TransactionType.EXPENSE,
            currency = "EUR",
            accountId = 7L,
            frequency = RecurrenceFrequency.WEEKLY,
            startDateEpochDay = 19_100L,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `cross-currency transfer movement survives the backup round trip`() {
        val entity = TransactionEntity(
            id = 42L,
            type = TransactionType.TRANSFER,
            amountMinor = -10_000L,
            currency = "EUR",
            accountId = 7L,
            timestampEpochMilli = 1_752_000_123_456,
            zoneOffsetSeconds = -14_400,
            transferAccountId = 8L,
            transferAmountMinor = 10_850L,
            transferCurrency = "USD",
            categoryId = null,
            description = "monthly move",
            note = "note",
            isExcludedFromStats = true,
            isRefund = false,
            recurringRuleId = 11L,
            isPending = true,
            recurringOccurrenceEpochDay = 20_500L,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `a loan between people survives the backup round trip`() {
        val entity = TransactionEntity(
            id = 43L,
            type = TransactionType.EXPENSE,
            amountMinor = -5_000L,
            currency = "EUR",
            accountId = 7L,
            timestampEpochMilli = 1_752_000_123_456,
            zoneOffsetSeconds = 7_200,
            categoryId = 3L,
            description = "prestito",
            isExcludedFromStats = true,
            counterparty = "Marta",
        )

        assertEquals(entity, entity.toBackup().toEntity())
        assertEquals("Marta", entity.toBackup().counterparty)
        // A file written before the field existed restores a plain movement.
        assertNull(entity.copy(counterparty = null).toBackup().counterparty)
    }

    @Test
    fun `a movement reminder and its watermark survive the backup round trip`() {
        val entity = TransactionEntity(
            id = 44L,
            type = TransactionType.EXPENSE,
            amountMinor = -21_000L,
            currency = "EUR",
            accountId = 7L,
            timestampEpochMilli = 1_752_000_123_456,
            zoneOffsetSeconds = 7_200,
            categoryId = 3L,
            description = "bollo auto",
            hasReminder = true,
            lastReminderEpochDay = 20_640L,
        )

        assertEquals(entity, entity.toBackup().toEntity())
        // The watermark travels with the flag, so restoring does not re-notify
        // about a date already announced.
        assertEquals(20_640L, entity.toBackup().lastReminderEpochDay)
        // A file written before the field existed restores a movement without one.
        assertFalse(entity.copy(hasReminder = false).toBackup().hasReminder)
    }

    @Test
    fun `tag assignment survives the backup round trip`() {
        val entity = TransactionTagCrossRef(transactionId = 42L, tagId = 5L)

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `overall budget survives the backup round trip`() {
        val entity = BudgetEntity(
            id = 40L,
            categoryId = null,
            amountMinor = 80_000L,
            currency = "EUR",
            lastNotified80EpochMonth = 24_318L,
            lastNotified100EpochMonth = null,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `category budget survives the backup round trip`() {
        val entity = BudgetEntity(
            id = 41L,
            categoryId = 3L,
            amountMinor = 25_000L,
            currency = "USD",
            lastNotified80EpochMonth = 24_318L,
            lastNotified100EpochMonth = 24_318L,
        )

        assertEquals(entity, entity.toBackup().toEntity())
    }

    @Test
    fun `an unknown enum name fails loudly instead of guessing`() {
        val backup = AccountEntity(
            id = 1L,
            name = "X",
            type = AccountType.CASH,
            currency = "EUR",
            initialBalanceMinor = 0L,
        ).toBackup().copy(type = "SPACESHIP")

        assertThrows(IllegalArgumentException::class.java) { backup.toEntity() }
    }
}
