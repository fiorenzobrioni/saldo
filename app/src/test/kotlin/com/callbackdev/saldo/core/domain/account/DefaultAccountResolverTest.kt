package com.callbackdev.saldo.core.domain.account

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class DefaultAccountResolverTest {

    private fun account(id: Long) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.CHECKING,
        currency = Currency.getInstance("EUR"),
        initialBalance = BigDecimal.ZERO,
    )

    private val first = account(1L)
    private val second = account(2L)
    private val third = account(3L)
    private val active = listOf(first, second, third)

    @Test
    fun `the explicit default wins`() {
        val resolved = DefaultAccountResolver.resolve(active, defaultAccountId = 3L, lastUsedAccountId = 2L)
        assertEquals(third, resolved)
    }

    @Test
    fun `a default that is no longer active falls back to the last used`() {
        val resolved = DefaultAccountResolver.resolve(active, defaultAccountId = 99L, lastUsedAccountId = 2L)
        assertEquals(second, resolved)
    }

    @Test
    fun `with no preferences at all the first active account is used`() {
        val resolved = DefaultAccountResolver.resolve(active, defaultAccountId = null, lastUsedAccountId = null)
        assertEquals(first, resolved)
    }

    @Test
    fun `a last used account that is gone falls through to the first active`() {
        val resolved = DefaultAccountResolver.resolve(active, defaultAccountId = null, lastUsedAccountId = 99L)
        assertEquals(first, resolved)
    }

    @Test
    fun `no active account resolves to nothing rather than inventing one`() {
        assertNull(DefaultAccountResolver.resolve(emptyList(), defaultAccountId = 1L, lastUsedAccountId = 2L))
    }
}
