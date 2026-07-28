package com.callbackdev.saldo.feature.counterparties

import com.callbackdev.saldo.core.domain.model.CounterpartyAmount
import com.callbackdev.saldo.core.domain.model.CounterpartyBalance
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** The two navigation decisions the credits and debts rows make. */
class CounterpartyActionsTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")

    private fun entry(vararg amounts: Pair<Currency, String>) = CounterpartyBalance(
        name = "Marta",
        amounts = amounts.map { (currency, amount) ->
            CounterpartyAmount(currency, BigDecimal(amount))
        },
        movementCount = 1,
        lastActivity = LocalDate.of(2026, 7, 1),
    )

    @Test
    fun `a credit is settled by an income of the residual amount`() {
        val route = settlementRoute(entry(eur to "-70.00"))

        assertEquals(TransactionType.INCOME.name, route?.initialTypeName)
        assertEquals("70.00", route?.initialAmountInput)
        assertEquals("Marta", route?.initialCounterparty)
    }

    @Test
    fun `a debt is settled by an expense of the residual amount`() {
        val route = settlementRoute(entry(eur to "25.50"))

        assertEquals(TransactionType.EXPENSE.name, route?.initialTypeName)
        assertEquals("25.50", route?.initialAmountInput)
    }

    @Test
    fun `an even counterparty has nothing to settle`() {
        assertNull(settlementRoute(entry(eur to "0.00")))
        assertNull(settlementRoute(entry()))
    }

    @Test
    fun `a position in another currency is taken only when the primary one is closed`() {
        // Amounts arrive primary-currency first: an open primary position wins.
        assertEquals("70.00", settlementRoute(entry(eur to "-70.00", usd to "-10.00"))?.initialAmountInput)
        assertEquals("10.00", settlementRoute(entry(eur to "0.00", usd to "-10.00"))?.initialAmountInput)
    }

    @Test
    fun `the drill-down carries the person and no date window`() {
        val route = drillDownRoute(entry(eur to "-70.00"))

        assertEquals("Marta", route.counterparty)
        assertNull(route.startEpochDay)
        assertNull(route.endEpochDayExclusive)
    }

    @Test
    fun `initials come from the first two words`() {
        assertEquals("M", initialsOf("Marta"))
        assertEquals("MR", initialsOf("marta rossi"))
        assertEquals("MR", initialsOf("  Marta   Rossi Bianchi "))
        assertEquals("?", initialsOf("   "))
    }

    @Test
    fun `the avatar tint is stable for a name and insensitive to case`() {
        assertEquals(avatarColorKey("Marta"), avatarColorKey(" marta "))
    }
}
