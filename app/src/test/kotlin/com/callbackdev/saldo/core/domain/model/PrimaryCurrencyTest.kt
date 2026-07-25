package com.callbackdev.saldo.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class PrimaryCurrencyTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val chf: Currency = Currency.getInstance("CHF")

    private fun account(
        id: Long,
        currency: Currency,
        isArchived: Boolean = false,
        isIncludedInTotal: Boolean = true,
    ) = AccountWithBalance(
        account = Account(
            id = id,
            name = "acc-$id",
            type = AccountType.CHECKING,
            currency = currency,
            initialBalance = BigDecimal.ZERO,
            isArchived = isArchived,
            isIncludedInTotal = isIncludedInTotal,
        ),
        balance = BigDecimal.ZERO,
    )

    @Test
    fun `the plurality of the counted accounts wins`() {
        val accounts = listOf(account(1L, usd), account(2L, usd), account(3L, eur))

        assertEquals(usd, accounts.primaryCurrency())
    }

    @Test
    fun `archived and excluded accounts do not vote`() {
        val accounts = listOf(
            account(1L, eur),
            account(2L, usd, isArchived = true),
            account(3L, usd, isIncludedInTotal = false),
        )

        assertEquals(eur, accounts.primaryCurrency())
    }

    @Test
    fun `a tie does not depend on the account order`() {
        // Neither is the fallback (whatever the JVM locale is), so the tie
        // resolves alphabetically: CHF before JPY.
        val jpy = Currency.getInstance("JPY")
        val oneWay = listOf(account(1L, jpy), account(2L, chf))
        val theOther = listOf(account(1L, chf), account(2L, jpy))

        // An arbitrary winner would let an unrelated edit that reshuffles the
        // list flip every aggregate on the screen.
        assertEquals(oneWay.primaryCurrency(), theOther.primaryCurrency())
        assertEquals(chf, oneWay.primaryCurrency())
    }

    @Test
    fun `a tie involving the fallback currency resolves to it`() {
        val other = if (fallbackCurrency == chf) usd else chf
        val accounts = listOf(account(1L, other), account(2L, fallbackCurrency))

        assertEquals(fallbackCurrency, accounts.primaryCurrency())
    }

    @Test
    fun `no countable account falls back`() {
        assertEquals(fallbackCurrency, emptyList<AccountWithBalance>().primaryCurrency())
    }

    @Test
    fun `an explicit override beats the plurality`() {
        val accounts = listOf(account(1L, usd), account(2L, usd))

        assertEquals(chf, primaryCurrency(accounts, chf))
        assertEquals(usd, primaryCurrency(accounts, null))
    }
}
