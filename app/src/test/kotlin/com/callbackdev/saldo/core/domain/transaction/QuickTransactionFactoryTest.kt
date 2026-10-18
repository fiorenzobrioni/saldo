package com.callbackdev.saldo.core.domain.transaction

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

class QuickTransactionFactoryTest {

    private val rome = ZoneId.of("Europe/Rome")

    private fun account(currency: String = "EUR") = Account(
        id = 7L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = Currency.getInstance(currency),
        initialBalance = BigDecimal.ZERO,
    )

    private val july = LocalDateTime.of(2026, 7, 8, 12, 15)

    @Test
    fun `an expense is negative and scaled to the account currency`() {
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("12.5"),
            account = account(),
            categoryId = 3L,
            dateTime = july,
            zone = rome,
        )
        assertEquals(BigDecimal("-12.50"), transaction.amount)
        assertEquals(TransactionType.EXPENSE, transaction.type)
        assertEquals(7L, transaction.accountId)
        assertEquals(3L, transaction.categoryId)
    }

    @Test
    fun `an income is positive`() {
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.INCOME,
            amount = BigDecimal("12.50"),
            account = account(),
            categoryId = 3L,
            dateTime = july,
            zone = rome,
        )
        assertEquals(BigDecimal("12.50"), transaction.amount)
    }

    @Test
    fun `a zero-decimal currency keeps no fraction`() {
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("1250.4"),
            account = account("JPY"),
            categoryId = null,
            dateTime = july,
            zone = rome,
        )
        assertEquals(BigDecimal("-1250"), transaction.amount)
    }

    @Test
    fun `the offset comes from the movement's own date, not from now`() {
        val summer = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = july,
            zone = rome,
        )
        val winter = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = LocalDateTime.of(2026, 1, 8, 12, 15),
            zone = rome,
        )
        assertEquals(ZoneOffset.ofHours(2), summer.zoneOffset)
        assertEquals(ZoneOffset.ofHours(1), winter.zoneOffset)
        assertEquals(july.toInstant(ZoneOffset.ofHours(2)), summer.timestamp)
    }

    @Test
    fun `a time the clocks repeat is read in the preferred offset`() {
        // Rome, 25 October 2026: 02:15 happens at +02:00 and again at +01:00.
        val repeated = LocalDateTime.of(2026, 10, 25, 2, 15)
        val second = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = repeated,
            zone = rome,
            preferredOffset = ZoneOffset.ofHours(1),
        )
        val first = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = repeated,
            zone = rome,
        )
        assertEquals(ZoneOffset.ofHours(1), second.zoneOffset)
        assertEquals(repeated.toInstant(ZoneOffset.ofHours(1)), second.timestamp)
        // Without a preference, the earlier of the two, as before.
        assertEquals(ZoneOffset.ofHours(2), first.zoneOffset)
    }

    @Test
    fun `a time the clocks skip keeps the offset before the gap, whatever is preferred`() {
        // Rome, 28 March 2027: 02:00 CET becomes 03:00 CEST, so 02:30 does not exist.
        val skipped = LocalDateTime.of(2027, 3, 28, 2, 30)
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = skipped,
            zone = rome,
            preferredOffset = ZoneOffset.ofHours(2),
        )
        assertEquals(ZoneOffset.ofHours(1), transaction.zoneOffset)
        assertEquals(skipped.toInstant(ZoneOffset.ofHours(1)), transaction.timestamp)
    }

    @Test
    fun `a blank description is stored as nothing at all`() {
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = july,
            zone = rome,
            description = "   ",
        )
        assertNull(transaction.description)
    }

    @Test
    fun `a quick movement is never pending and never belongs to a recurring rule`() {
        val transaction = QuickTransactionFactory.create(
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ONE,
            account = account(),
            categoryId = null,
            dateTime = july,
            zone = rome,
        )
        assertEquals(false, transaction.isPending)
        assertNull(transaction.recurringRuleId)
        assertEquals(0L, transaction.id)
    }
}
