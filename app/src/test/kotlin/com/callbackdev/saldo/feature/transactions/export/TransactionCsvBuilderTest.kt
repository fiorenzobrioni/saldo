package com.callbackdev.saldo.feature.transactions.export

import com.callbackdev.saldo.core.common.prefs.CsvSeparator
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

class TransactionCsvBuilderTest {

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")

    private val labels = CsvColumnLabels(
        date = "Date",
        type = "Type",
        category = "Category",
        description = "Description",
        account = "Account",
        toAccount = "To account",
        amount = "Amount",
        currency = "Currency",
        receivedAmount = "Amount received",
        receivedCurrency = "Currency received",
        tags = "Tags",
        note = "Note",
        recurring = "Recurring",
    )

    private val typeLabels = mapOf(
        TransactionType.EXPENSE to "Expense",
        TransactionType.INCOME to "Income",
        TransactionType.TRANSFER to "Transfer",
        TransactionType.ADJUSTMENT to "Adjustment",
    )

    private val checking = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private val savings = Account(
        id = 2L,
        name = "Savings",
        type = AccountType.CHECKING,
        currency = usd,
        initialBalance = BigDecimal.ZERO,
    )

    private val groceries = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )

    private fun item(
        transaction: Transaction,
        account: Account? = checking,
        toAccount: Account? = null,
        category: Category? = null,
    ) = TransactionListItem(
        transaction = transaction,
        account = account,
        toAccount = toAccount,
        category = category,
    )

    private fun expense(
        id: Long = 1L,
        amount: String = "-12.50",
        description: String? = "Pizza",
        note: String? = null,
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = checking.id,
        timestamp = Instant.parse("2026-07-08T08:00:00Z"),
        zoneOffset = ZoneOffset.ofHours(2),
        categoryId = groceries.id,
        description = description,
        note = note,
    )

    private fun lines(csv: String): List<String> =
        csv.removePrefix("\uFEFF").trimEnd().split("\r\n")

    @Test
    fun `semicolon separator pairs with comma decimals`() {
        val csv = TransactionCsvBuilder.build(
            items = listOf(item(expense(), category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        val rows = lines(csv)
        assertEquals(
            "Date;Type;Category;Description;Account;To account;Amount;Currency;" +
                "Amount received;Currency received;Tags;Note;Recurring",
            rows[0],
        )
        assertEquals("2026-07-08;Expense;Groceries;Pizza;Checking;;-12,50;EUR;;;;;", rows[1])
    }

    @Test
    fun `comma separator pairs with dot decimals`() {
        val csv = TransactionCsvBuilder.build(
            items = listOf(item(expense(), category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.COMMA,
            recurringMark = "Yes",
        )

        assertEquals("2026-07-08,Expense,Groceries,Pizza,Checking,,-12.50,EUR,,,,,", lines(csv)[1])
    }

    @Test
    fun `document starts with a BOM so spreadsheets detect UTF-8`() {
        val csv = TransactionCsvBuilder.build(
            items = emptyList(),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        assertTrue(csv.startsWith("\uFEFF"))
    }

    @Test
    fun `fields containing separators quotes or newlines are quoted and escaped`() {
        val tricky = expense(
            description = "Pizza; with \"friends\"",
            note = "line one\nline two",
        )

        val csv = TransactionCsvBuilder.build(
            items = listOf(item(tricky, category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        assertTrue(csv.contains("\"Pizza; with \"\"friends\"\"\""))
        assertTrue(csv.contains("\"line one\nline two\""))
    }

    @Test
    fun `comma-decimal amounts are not quoted because the separator is semicolon`() {
        val csv = TransactionCsvBuilder.build(
            items = listOf(item(expense(amount = "-1234.56"), category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        assertTrue(lines(csv)[1].contains(";-1234,56;"))
    }

    @Test
    fun `cross-currency transfer carries both legs`() {
        val transfer = Transaction(
            id = 2L,
            type = TransactionType.TRANSFER,
            amount = BigDecimal("-100.00"),
            currency = eur,
            accountId = checking.id,
            timestamp = Instant.parse("2026-07-08T08:00:00Z"),
            zoneOffset = ZoneOffset.UTC,
            transferAccountId = savings.id,
            transferAmount = BigDecimal("108.50"),
            transferCurrency = usd,
        )

        val csv = TransactionCsvBuilder.build(
            items = listOf(item(transfer, toAccount = savings)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.COMMA,
            recurringMark = "Yes",
        )

        assertEquals("2026-07-08,Transfer,,,Checking,Savings,-100.00,EUR,108.50,USD,,,", lines(csv)[1])
    }

    @Test
    fun `a text field that looks like a formula is prefixed against injection`() {
        val evil = expense(description = "=HYPERLINK(\"http://x\")")

        val csv = TransactionCsvBuilder.build(
            items = listOf(item(evil, category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        // The description is prefixed with an apostrophe (and quoted for its quotes).
        assertTrue(csv.contains("\"'=HYPERLINK"))
    }

    @Test
    fun `a recurring movement is flagged, a manual one is not`() {
        val manual = item(expense(id = 1L), category = groceries)
        val recurring = item(expense(id = 2L).copy(recurringRuleId = 5L), category = groceries)

        val csv = TransactionCsvBuilder.build(
            items = listOf(manual, recurring),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        val rows = lines(csv)
        assertTrue(rows[1].endsWith(";")) // manual: trailing recurring field is empty
        assertTrue(rows[2].endsWith(";Yes"))
    }

    @Test
    fun `tags are joined in a single field`() {
        val csv = TransactionCsvBuilder.build(
            items = listOf(item(expense(id = 7L), category = groceries)),
            tagNames = mapOf(7L to listOf("holiday", "family")),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.SEMICOLON,
            recurringMark = "Yes",
        )

        // The joined list contains no semicolon, so it needs no quoting.
        assertTrue(lines(csv)[1].contains(";holiday, family;"))
    }

    @Test
    fun `the date is the movement's own local day`() {
        // 23:30Z at UTC+2 is already July 9th.
        val lateEvening = expense().copy(
            timestamp = Instant.parse("2026-07-08T23:30:00Z"),
        )

        val csv = TransactionCsvBuilder.build(
            items = listOf(item(lateEvening, category = groceries)),
            tagNames = emptyMap(),
            typeLabels = typeLabels,
            labels = labels,
            separator = CsvSeparator.COMMA,
            recurringMark = "Yes",
        )

        assertTrue(lines(csv)[1].startsWith("2026-07-09,"))
    }
}
