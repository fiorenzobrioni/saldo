package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

class TransactionMapperTest {

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")

    @Test
    fun `expense stores a negative signed amount in cents`() {
        val expense = Transaction(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("-45.00"),
            currency = eur,
            accountId = 1L,
            timestamp = Instant.ofEpochMilli(1_700_000_000_000L),
            zoneOffset = ZoneOffset.ofHours(2),
            categoryId = 7L,
        )

        val entity = expense.toEntity()

        assertEquals(-4500L, entity.amountMinor)
        assertEquals("EUR", entity.currency)
        assertEquals(7_200, entity.zoneOffsetSeconds)
    }

    @Test
    fun `round trip preserves an expense`() {
        val expense = Transaction(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("-18.90"),
            currency = eur,
            accountId = 3L,
            timestamp = Instant.ofEpochMilli(1_700_000_123_000L),
            zoneOffset = ZoneOffset.ofHours(1),
            id = 42L,
            categoryId = 5L,
            description = "Supermercato",
            isExcludedFromStats = true,
        )

        val restored = expense.toEntity().toDomain()

        assertEquals(expense.id, restored.id)
        assertEquals(expense.type, restored.type)
        assertEquals(0, expense.amount.compareTo(restored.amount))
        assertEquals(expense.currency, restored.currency)
        assertEquals(expense.accountId, restored.accountId)
        assertEquals(expense.timestamp, restored.timestamp)
        assertEquals(expense.zoneOffset, restored.zoneOffset)
        assertEquals(expense.categoryId, restored.categoryId)
        assertEquals(expense.description, restored.description)
        assertEquals(expense.isExcludedFromStats, restored.isExcludedFromStats)
    }

    @Test
    fun `cross-currency transfer keeps both legs`() {
        val transfer = Transaction(
            type = TransactionType.TRANSFER,
            amount = BigDecimal("-100.00"),
            currency = eur,
            accountId = 1L,
            timestamp = Instant.ofEpochMilli(1_700_000_000_000L),
            zoneOffset = ZoneOffset.UTC,
            transferAccountId = 2L,
            transferAmount = BigDecimal("108.50"),
            transferCurrency = usd,
        )

        val entity = transfer.toEntity()
        assertEquals(-10_000L, entity.amountMinor)
        assertEquals(10_850L, entity.transferAmountMinor)
        assertEquals("USD", entity.transferCurrency)

        val restored = entity.toDomain()
        assertEquals(0, restored.transferAmount!!.compareTo(BigDecimal("108.50")))
        assertEquals(usd, restored.transferCurrency)
    }

    @Test
    fun `non-transfer movement has no destination leg`() {
        val income = Transaction(
            type = TransactionType.INCOME,
            amount = BigDecimal("2000.00"),
            currency = eur,
            accountId = 1L,
            timestamp = Instant.EPOCH,
            zoneOffset = ZoneOffset.UTC,
        )

        val entity = income.toEntity()
        assertNull(entity.transferAmountMinor)
        assertNull(entity.transferCurrency)
        assertNull(entity.toDomain().transferAmount)
    }
}
