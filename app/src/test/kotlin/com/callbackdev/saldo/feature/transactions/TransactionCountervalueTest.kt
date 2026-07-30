package com.callbackdev.saldo.feature.transactions

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.rates.RateTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

class TransactionCountervalueTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")

    private val day = LocalDate.of(2026, 7, 15)

    /** 1 EUR = 2 USD keeps the arithmetic legible. */
    private val rates = RateTable.of(listOf(ExchangeRate("USD", day, BigDecimal("2"))))

    private fun movement(
        currency: Currency,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "-44.00",
    ) = Transaction(
        id = 1L,
        type = type,
        amount = BigDecimal(amount),
        currency = currency,
        accountId = 1L,
        timestamp = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
        zoneOffset = ZoneOffset.UTC,
        transferAccountId = if (type == TransactionType.TRANSFER) 2L else null,
        transferAmount = if (type == TransactionType.TRANSFER) BigDecimal("40.00") else null,
        transferCurrency = if (type == TransactionType.TRANSFER) eur else null,
    )

    @Test
    fun `a foreign flow converts at the rate of its own day, keeping the sign`() {
        assertEquals(
            BigDecimal("-22.00"),
            movement(usd).countervalueIn(eur, rates),
        )
    }

    @Test
    fun `a same-currency movement has nothing to declare`() {
        assertNull(movement(eur).countervalueIn(eur, rates))
    }

    @Test
    fun `a transfer never carries a countervalue - both legs are explicit`() {
        assertNull(movement(usd, type = TransactionType.TRANSFER).countervalueIn(eur, rates))
    }

    @Test
    fun `without rates the row stays undeclared instead of guessing`() {
        assertNull(movement(usd).countervalueIn(eur, RateTable.EMPTY))
    }
}
