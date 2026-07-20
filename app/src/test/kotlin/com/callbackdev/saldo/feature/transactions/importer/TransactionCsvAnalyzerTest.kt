package com.callbackdev.saldo.feature.transactions.importer

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class TransactionCsvAnalyzerTest {

    private val analyzer = TransactionCsvAnalyzer()
    private val eur: Currency = Currency.getInstance("EUR")

    private val checking = Account(id = 1L, name = "Checking", type = AccountType.CHECKING, currency = eur, initialBalance = BigDecimal.ZERO)
    private val savings = Account(id = 2L, name = "Savings", type = AccountType.SAVINGS, currency = eur, initialBalance = BigDecimal.ZERO)
    private val groceries = Category(id = 10L, name = "Groceries", type = CategoryType.EXPENSE, color = 0, icon = "x")
    private val holiday = Tag(id = 100L, name = "holiday")

    private val columnLabels = mapOf(
        CsvField.DATE to "Date", CsvField.TYPE to "Type", CsvField.CATEGORY to "Category",
        CsvField.DESCRIPTION to "Description", CsvField.ACCOUNT to "Account",
        CsvField.TO_ACCOUNT to "To account", CsvField.AMOUNT to "Amount",
        CsvField.CURRENCY to "Currency", CsvField.RECEIVED_AMOUNT to "Amount received",
        CsvField.RECEIVED_CURRENCY to "Currency received", CsvField.TAGS to "Tags",
        CsvField.NOTE to "Note",
    )
    private val typeLabels = mapOf(
        TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income",
        TransactionType.TRANSFER to "Transfer", TransactionType.ADJUSTMENT to "Adjustment",
    )
    private val header = listOf(
        "Date", "Type", "Category", "Description", "Account", "To account",
        "Amount", "Currency", "Amount received", "Currency received", "Tags", "Note",
    )
    private val mapping = CsvHeaderMapper.map(header, columnLabels)!!

    @Suppress("LongParameterList")
    private fun row(
        date: String = "2026-07-08", type: String = "Expense", category: String = "",
        description: String = "", account: String = "Checking", toAccount: String = "",
        amount: String = "-10,00", currency: String = "EUR", receivedAmount: String = "",
        receivedCurrency: String = "", tags: String = "", note: String = "",
    ) = listOf(date, type, category, description, account, toAccount, amount, currency, receivedAmount, receivedCurrency, tags, note)

    private fun context(
        accounts: List<Account> = listOf(checking, savings),
        categories: List<Category> = listOf(groceries),
        tags: List<Tag> = listOf(holiday),
        signatures: Set<String> = emptySet(),
    ) = ImportContext(accounts, categories, tags, signatures, columnLabels, typeLabels, eur)

    private fun analyze(rows: List<List<String>>, context: ImportContext = context(), options: CsvImportOptions = CsvImportOptions()) =
        analyzer.analyze(rows, mapping, context, options)

    @Test
    fun `a clean expense row becomes an importable movement`() {
        val analysis = analyze(listOf(row(category = "Groceries", description = "Pizza", amount = "-12,50")))
        assertEquals(1, analysis.importableCount)
        val movement = analysis.importable.single().movement
        assertEquals(TransactionType.EXPENSE, movement.type)
        assertEquals("Checking", movement.accountName)
        assertEquals("Groceries", movement.categoryName)
        assertEquals(0, movement.amount.compareTo(BigDecimal("-12.50")))
    }

    @Test
    fun `a duplicate of an existing ledger movement is skipped`() {
        val signature = MovementSignature.of(
            LocalDate.of(2026, 7, 8), TransactionType.EXPENSE, BigDecimal("-12.50"), eur, "Checking", "Pizza",
        )
        val analysis = analyze(
            listOf(row(description = "Pizza", amount = "-12,50")),
            context(signatures = setOf(signature)),
        )
        assertEquals(0, analysis.importableCount)
        assertEquals(1, analysis.duplicateCount)
        assertEquals(DuplicateReason.ALREADY_IN_LEDGER, (analysis.rows.single() as RowOutcome.Duplicate).reason)
    }

    @Test
    fun `two identical rows in the same file keep the first and skip the second`() {
        val analysis = analyze(listOf(row(description = "Pizza"), row(description = "Pizza")))
        assertEquals(1, analysis.importableCount)
        assertEquals(1, analysis.duplicateCount)
    }

    @Test
    fun `an expense with a positive amount has its sign normalized`() {
        val analysis = analyze(listOf(row(type = "Expense", amount = "12,50")))
        val importable = analysis.importable.single()
        assertTrue(importable.movement.amount.signum() < 0)
        assertTrue(RowAdjustmentCode.SIGN_NORMALIZED in importable.adjustments)
    }

    @Test
    fun `a missing type is inferred from the amount sign without a sign adjustment`() {
        val analysis = analyze(listOf(row(type = "", amount = "40,00")))
        val importable = analysis.importable.single()
        assertEquals(TransactionType.INCOME, importable.movement.type)
        assertTrue(RowAdjustmentCode.TYPE_INFERRED in importable.adjustments)
        assertTrue(RowAdjustmentCode.SIGN_NORMALIZED !in importable.adjustments)
    }

    @Test
    fun `an unknown account is scheduled for creation when allowed`() {
        val analysis = analyze(listOf(row(account = "Wallet", currency = "EUR")))
        assertEquals(1, analysis.importableCount)
        assertEquals(listOf("Wallet"), analysis.newAccounts.map { it.name })
    }

    @Test
    fun `an unknown account is an error when creation is off`() {
        val analysis = analyze(
            listOf(row(account = "Wallet")),
            options = CsvImportOptions(createMissingAccounts = false),
        )
        assertEquals(0, analysis.importableCount)
        val invalid = analysis.rows.single() as RowOutcome.Invalid
        assertTrue(RowErrorCode.UNKNOWN_ACCOUNT in invalid.errors)
    }

    @Test
    fun `a currency that mismatches an existing account is rejected`() {
        val analysis = analyze(listOf(row(account = "Checking", currency = "USD")))
        val invalid = analysis.rows.single() as RowOutcome.Invalid
        assertTrue(RowErrorCode.ACCOUNT_CURRENCY_MISMATCH in invalid.errors)
    }

    @Test
    fun `missing amount and invalid date are both reported`() {
        val analysis = analyze(listOf(row(date = "nope", amount = "")))
        val invalid = analysis.rows.single() as RowOutcome.Invalid
        assertTrue(RowErrorCode.MISSING_AMOUNT in invalid.errors)
        assertTrue(RowErrorCode.INVALID_DATE in invalid.errors)
    }

    @Test
    fun `a new category and new tag are scheduled and the tag is kept on the movement`() {
        val analysis = analyze(
            listOf(row(category = "Hobbies", amount = "-9,00", tags = "gaming, holiday")),
        )
        val importable = analysis.importable.single()
        assertEquals(listOf("Hobbies"), analysis.newCategories.map { it.name })
        assertEquals(listOf("gaming"), analysis.newTags)
        assertTrue("holiday" in importable.movement.tagNames && "gaming" in importable.movement.tagNames)
    }

    @Test
    fun `an unknown tag is dropped when tag creation is off`() {
        val analysis = analyze(
            listOf(row(amount = "-9,00", tags = "gaming, holiday")),
            options = CsvImportOptions(createMissingTags = false),
        )
        val importable = analysis.importable.single()
        assertEquals(listOf("holiday"), importable.movement.tagNames)
        assertTrue(RowAdjustmentCode.TAGS_DROPPED in importable.adjustments)
        assertTrue(analysis.newTags.isEmpty())
    }

    @Test
    fun `a same-currency transfer derives its received amount from the source`() {
        val analysis = analyze(
            listOf(row(type = "Transfer", account = "Checking", toAccount = "Savings", amount = "-100,00")),
        )
        val movement = analysis.importable.single().movement
        assertEquals(TransactionType.TRANSFER, movement.type)
        assertEquals("Savings", movement.toAccountName)
        assertEquals(0, movement.transferAmount!!.compareTo(BigDecimal("100.00")))
        assertEquals(eur, movement.transferCurrency)
    }

    @Test
    fun `a transfer without a destination is incomplete`() {
        val analysis = analyze(listOf(row(type = "Transfer", toAccount = "", amount = "-100,00")))
        val invalid = analysis.rows.single() as RowOutcome.Invalid
        assertTrue(RowErrorCode.INCOMPLETE_TRANSFER in invalid.errors)
    }

    @Test
    fun `blank lines are ignored and not counted`() {
        val analysis = analyze(listOf(row(description = "Pizza"), List(12) { "" }))
        assertEquals(1, analysis.totalRows)
    }

    @Test
    fun `a formula-guarded text field is stripped back on import`() {
        val analysis = analyze(listOf(row(description = "'=SUM(A1)", amount = "-5,00")))
        assertEquals("=SUM(A1)", analysis.importable.single().movement.description)
    }

    @Test
    fun `a category on a transfer is not created`() {
        val analysis = analyze(
            listOf(row(type = "Transfer", account = "Checking", toAccount = "Savings", amount = "-5,00", category = "Nope")),
        )
        assertNull(analysis.importable.single().movement.categoryName)
        assertTrue(analysis.newCategories.isEmpty())
    }
}
