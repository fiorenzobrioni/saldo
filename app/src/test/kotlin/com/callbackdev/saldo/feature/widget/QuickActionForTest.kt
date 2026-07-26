package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The widget's "open Saldo" tile used to be wired to the expense action
 * whatever the widget was showing, so an income widget dropped the user into a
 * new *expense*. The routing is a one-liner and it is pinned here because
 * nothing else in a build would ever have caught it.
 */
class QuickActionForTest {

    @Test
    fun `an income widget opens the income editor`() {
        assertEquals(MainActivity.ACTION_ADD_INCOME, quickActionFor(TransactionType.INCOME))
    }

    @Test
    fun `an expense widget opens the expense editor`() {
        assertEquals(MainActivity.ACTION_ADD_EXPENSE, quickActionFor(TransactionType.EXPENSE))
    }

    @Test
    fun `any other type falls back to the expense editor rather than to nothing`() {
        assertEquals(MainActivity.ACTION_ADD_EXPENSE, quickActionFor(TransactionType.TRANSFER))
        assertEquals(MainActivity.ACTION_ADD_EXPENSE, quickActionFor(TransactionType.ADJUSTMENT))
    }
}
