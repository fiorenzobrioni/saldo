package com.callbackdev.saldo.core.domain.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {

    @Test
    fun `encode then decode returns an identical backup`() {
        val original = fullyPopulatedBackupFile()

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decode ignores unknown keys so newer minor additions still restore`() {
        val json = BackupCodec.encode(fullyPopulatedBackupFile())
            .replaceFirst("\"version\"", "\"aFutureField\": \"whatever\", \"version\"")

        val decoded = BackupCodec.decode(json)

        assertEquals(fullyPopulatedBackupFile(), decoded)
    }

    @Test
    fun `decode applies defaults for fields missing from older files`() {
        // A version-1 file written before an optional field existed: drop one.
        val json = """
            {
              "format": "saldo-backup",
              "version": 1,
              "exportedAtEpochMilli": 1000,
              "data": {
                "accounts": [
                  {"id": 1, "name": "Cash", "type": "CASH", "currency": "EUR", "initialBalanceMinor": 0}
                ]
              }
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(json)

        assertEquals(1, decoded.data.accounts.size)
        assertTrue(decoded.data.accounts.single().isIncludedInTotal)
        // The budget-exclusion flag is additive too: older files default to included.
        assertTrue(decoded.data.accounts.single().isIncludedInBudget)
        assertEquals(emptyList<TransactionBackup>(), decoded.data.transactions)
        // Files written before the budgets feature restore with no budgets.
        assertEquals(emptyList<BudgetBackup>(), decoded.data.budgets)
    }

    @Test
    fun `foreign json is rejected as not a backup`() {
        assertThrows(BackupDecodeException.NotABackup::class.java) {
            BackupCodec.decode("""{"some": "other", "file": true}""")
        }
    }

    @Test
    fun `a json array is rejected as not a backup`() {
        assertThrows(BackupDecodeException.NotABackup::class.java) {
            BackupCodec.decode("""[1, 2, 3]""")
        }
    }

    @Test
    fun `a newer schema version is refused with the declared version`() {
        val json = """{"format": "saldo-backup", "version": 99, "data": {}}"""

        val error = assertThrows(BackupDecodeException.UnsupportedVersion::class.java) {
            BackupCodec.decode(json)
        }

        assertEquals(99, error.version)
    }

    @Test
    fun `malformed json is reported as corrupted`() {
        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode("not json at all {")
        }
    }

    @Test
    fun `a marked file with a broken payload is reported as corrupted`() {
        val json = """
            {"format": "saldo-backup", "version": 1, "exportedAtEpochMilli": 0,
             "data": {"accounts": [{"id": "not-a-number"}]}}
        """.trimIndent()

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(json)
        }
    }

    @Test
    fun `an unknown currency code is rejected at decode time`() {
        val file = fullyPopulatedBackupFile()
        val tampered = file.copy(
            data = file.data.copy(
                accounts = file.data.accounts.map { it.copy(currency = "EURO") },
            ),
        )

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(BackupCodec.encode(tampered))
        }
    }

    @Test
    fun `an unknown transfer currency is rejected at decode time`() {
        val file = fullyPopulatedBackupFile()
        val tampered = file.copy(
            data = file.data.copy(
                transactions = file.data.transactions.map {
                    if (it.transferCurrency != null) it.copy(transferCurrency = "XXY") else it
                },
            ),
        )

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(BackupCodec.encode(tampered))
        }
    }

    @Test
    fun `an unknown enum name is rejected at decode time`() {
        val file = fullyPopulatedBackupFile()
        val tampered = file.copy(
            data = file.data.copy(
                recurringRules = file.data.recurringRules.map { it.copy(frequency = "FORTNIGHTLY") },
            ),
        )

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(BackupCodec.encode(tampered))
        }
    }

    @Test
    fun `a transfer without destination account or amount is rejected at decode time`() {
        val file = fullyPopulatedBackupFile()
        val tampered = file.copy(
            data = file.data.copy(
                transactions = file.data.transactions.map {
                    if (it.type == "TRANSFER") it.copy(transferAmountMinor = null) else it
                },
            ),
        )

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(BackupCodec.encode(tampered))
        }
    }

    @Test
    fun `more than one overall budget is rejected at decode time`() {
        val file = fullyPopulatedBackupFile()
        val overall = file.data.budgets.first { it.categoryId == null }
        val tampered = file.copy(
            data = file.data.copy(
                budgets = file.data.budgets + overall.copy(id = 999L),
            ),
        )

        assertThrows(BackupDecodeException.Corrupted::class.java) {
            BackupCodec.decode(BackupCodec.encode(tampered))
        }
    }

    @Test
    fun `summary counts every collection`() {
        val summary = fullyPopulatedBackupFile().summary()

        assertEquals(2, summary.accounts)
        assertEquals(1, summary.categories)
        assertEquals(2, summary.transactions)
        assertEquals(1, summary.recurringRules)
        assertEquals(1, summary.tags)
        assertEquals(2, summary.budgets)
        assertEquals("1.2.3", summary.appVersion)
    }
}

/** A backup exercising every field, shared by the codec and round-trip tests. */
@Suppress("LongMethod") // Deliberately exhaustive fixture: every schema field appears once.
internal fun fullyPopulatedBackupFile(): BackupFile = BackupFile(
    version = BackupFile.CURRENT_VERSION,
    exportedAtEpochMilli = 1_752_240_000_000,
    appVersion = "1.2.3",
    data = BackupData(
        accounts = listOf(
            AccountBackup(
                id = 1L,
                name = "Checking",
                type = "CHECKING",
                currency = "EUR",
                initialBalanceMinor = 123_456L,
                color = 0x336699,
                icon = "account_balance",
                isIncludedInTotal = true,
                isArchived = false,
                sortOrder = 0,
                createdAtEpochMilli = 1_700_000_000_000,
            ),
            AccountBackup(
                id = 2L,
                name = "Old wallet",
                type = "CASH",
                currency = "USD",
                initialBalanceMinor = -500L,
                isIncludedInTotal = false,
                isArchived = true,
                sortOrder = 1,
            ),
        ),
        categories = listOf(
            CategoryBackup(
                id = 10L,
                name = "Groceries",
                type = "EXPENSE",
                color = 0x66BB6A,
                icon = "shopping_cart",
                sortOrder = 3,
                isDefault = true,
            ),
        ),
        tags = listOf(TagBackup(id = 20L, name = "holiday")),
        recurringRules = listOf(
            RecurringRuleBackup(
                id = 30L,
                name = "Netflix",
                type = "EXPENSE",
                currency = "EUR",
                accountId = 1L,
                frequency = "MONTHLY",
                startDateEpochDay = 20_000L,
                amountMinor = 1_299L,
                categoryId = 10L,
                dayOfReference = 31,
                endDateEpochDay = 21_000L,
                mode = "CONFIRM",
                isVariableAmount = false,
                lastGeneratedEpochDay = 20_600L,
                color = 0xAA0000,
                icon = "subscriptions",
                note = "shared plan",
                lastReminderEpochDay = 20_590L,
            ),
        ),
        transactions = listOf(
            TransactionBackup(
                id = 100L,
                type = "EXPENSE",
                amountMinor = -1_299L,
                currency = "EUR",
                accountId = 1L,
                timestampEpochMilli = 1_752_000_000_000,
                zoneOffsetSeconds = 7_200,
                categoryId = 10L,
                description = "Netflix",
                note = "with \"quotes\"; and, separators",
                isExcludedFromStats = true,
                isRefund = false,
                recurringRuleId = 30L,
                isPending = true,
                recurringOccurrenceEpochDay = 20_600L,
            ),
            TransactionBackup(
                id = 101L,
                type = "TRANSFER",
                amountMinor = -50_00L,
                currency = "EUR",
                accountId = 1L,
                timestampEpochMilli = 1_751_000_000_000,
                zoneOffsetSeconds = -3_600,
                transferAccountId = 2L,
                transferAmountMinor = 54_25L,
                transferCurrency = "USD",
            ),
        ),
        transactionTags = listOf(TransactionTagBackup(transactionId = 100L, tagId = 20L)),
        budgets = listOf(
            BudgetBackup(
                id = 40L,
                categoryId = null,
                amountMinor = 80_000L,
                currency = "EUR",
                lastNotified80EpochMonth = 24_318L,
                lastNotified100EpochMonth = null,
            ),
            BudgetBackup(
                id = 41L,
                categoryId = 10L,
                amountMinor = 25_000L,
                currency = "EUR",
                lastNotified80EpochMonth = 24_318L,
                lastNotified100EpochMonth = 24_318L,
            ),
        ),
        savingsGoals = listOf(
            SavingsGoalBackup(
                id = 50L,
                name = "Holiday",
                targetAmountMinor = 200_000L,
                currency = "EUR",
                accountId = 1L,
                targetDateEpochDay = 21_000L,
                color = 0x66BB6A,
                icon = "savings",
                sortOrder = 0,
            ),
        ),
    ),
)
