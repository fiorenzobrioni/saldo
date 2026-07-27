package com.callbackdev.saldo.core.domain.money

import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The sign convention was private to the transaction editor until the widget
 * needed it too. These cases pin the behaviour so the extraction cannot have
 * changed it, and so a future edit cannot flip a sign under both callers at
 * once.
 */
class TransactionSignTest {

    private val ten = BigDecimal("10.00")

    @Test
    fun `an expense leaves the account`() {
        assertEquals(BigDecimal("-10.00"), TransactionSign.signed(TransactionType.EXPENSE, ten))
    }

    @Test
    fun `an income enters the account`() {
        assertEquals(BigDecimal("10.00"), TransactionSign.signed(TransactionType.INCOME, ten))
    }

    @Test
    fun `a transfer leaves the source account`() {
        assertEquals(BigDecimal("-10.00"), TransactionSign.signed(TransactionType.TRANSFER, ten))
    }

    @Test
    fun `an adjustment keeps the sign it was given`() {
        assertEquals(BigDecimal("-10.00"), TransactionSign.signed(TransactionType.ADJUSTMENT, BigDecimal("-10.00")))
        assertEquals(BigDecimal("10.00"), TransactionSign.signed(TransactionType.ADJUSTMENT, ten))
    }

    @Test
    fun `the magnitude decides nothing - a negative input still reads as an expense`() {
        assertEquals(
            BigDecimal("-10.00"),
            TransactionSign.signed(TransactionType.EXPENSE, BigDecimal("-10.00")),
        )
    }
}
