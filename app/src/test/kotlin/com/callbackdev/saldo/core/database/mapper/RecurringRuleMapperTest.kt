package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurrenceMode
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class RecurringRuleMapperTest {

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")

    @Test
    fun `round trip preserves a same-currency transfer rule`() {
        val rule = RecurringRule(
            id = 4L,
            name = "Savings",
            type = TransactionType.TRANSFER,
            currency = eur,
            accountId = 1L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 1),
            amount = BigDecimal("150.00"),
            categoryId = null,
            dayOfReference = 1,
            mode = RecurrenceMode.AUTOMATIC,
            transferAccountId = 2L,
            transferAmount = BigDecimal("150.00"),
            transferCurrency = eur,
        )

        val restored = rule.toEntity().toDomain()

        assertEquals(2L, restored.transferAccountId)
        assertEquals(0, restored.transferAmount!!.compareTo(BigDecimal("150.00")))
        assertEquals(eur, restored.transferCurrency)
        assertNull(restored.categoryId)
    }

    @Test
    fun `cross-currency transfer rule keeps a null destination amount`() {
        val rule = RecurringRule(
            id = 5L,
            name = "USD account",
            type = TransactionType.TRANSFER,
            currency = eur,
            accountId = 1L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 1),
            amount = BigDecimal("100.00"),
            mode = RecurrenceMode.CONFIRM,
            transferAccountId = 3L,
            transferAmount = null,
            transferCurrency = usd,
        )

        val entity = rule.toEntity()
        assertNull(entity.transferAmountMinor)
        assertEquals("USD", entity.transferCurrency)

        val restored = entity.toDomain()
        assertNull(restored.transferAmount)
        assertEquals(usd, restored.transferCurrency)
    }

    @Test
    fun `an expense rule has no destination leg`() {
        val rule = RecurringRule(
            name = "Netflix",
            type = TransactionType.EXPENSE,
            currency = eur,
            accountId = 1L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = LocalDate.of(2026, 7, 1),
            amount = BigDecimal("12.99"),
        )

        val entity = rule.toEntity()

        assertNull(entity.transferAccountId)
        assertNull(entity.transferAmountMinor)
        assertNull(entity.transferCurrency)
        assertNull(entity.toDomain().transferAccountId)
    }
}
