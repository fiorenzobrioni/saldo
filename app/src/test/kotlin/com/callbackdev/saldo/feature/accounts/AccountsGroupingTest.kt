package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class AccountsGroupingTest {

    private val eur = Currency.getInstance("EUR")

    private fun item(
        id: Long,
        name: String,
        type: AccountType,
        balance: BigDecimal = BigDecimal.ZERO,
        currency: Currency = eur,
        sortOrder: Int = 0,
        balanceAsOfToday: BigDecimal? = null,
    ) = AccountWithBalance(
        account = Account(
            id = id,
            name = name,
            type = type,
            currency = currency,
            initialBalance = BigDecimal.ZERO,
            sortOrder = sortOrder,
        ),
        balance = balance,
        balanceAsOfToday = balanceAsOfToday,
    )

    @Test
    fun `groups follow the account-type declaration order with checking first`() {
        val items = listOf(
            item(1, "Wallet", AccountType.DIGITAL_WALLET),
            item(2, "Cash", AccountType.CASH),
            item(3, "Main", AccountType.CHECKING),
            item(4, "Piggy", AccountType.SAVINGS),
        )

        val types = buildAccountTypeGroups(items).map { it.type }

        assertEquals(
            listOf(
                AccountType.CHECKING,
                AccountType.SAVINGS,
                AccountType.CASH,
                AccountType.DIGITAL_WALLET,
            ),
            types,
        )
    }

    @Test
    fun `accounts within a group are alphabetical by name, case-insensitive`() {
        val items = listOf(
            item(1, "banca zeta", AccountType.CHECKING),
            item(2, "Banca alfa", AccountType.CHECKING),
            item(3, "Banca Beta", AccountType.CHECKING),
        )

        val names = buildAccountTypeGroups(items).single().accounts.map { it.account.name }

        assertEquals(listOf("Banca alfa", "Banca Beta", "banca zeta"), names)
    }

    @Test
    fun `equal names keep a stable order by id`() {
        val items = listOf(
            item(2, "Conto", AccountType.CHECKING),
            item(1, "Conto", AccountType.CHECKING),
        )

        val ids = buildAccountTypeGroups(items).single().accounts.map { it.account.id }

        assertEquals(listOf(1L, 2L), ids)
    }

    @Test
    fun `sortedByTypeThenName flattens by type order then name`() {
        val items = listOf(
            item(1, "Zed cash", AccountType.CASH),
            item(2, "Beta bank", AccountType.CHECKING),
            item(3, "Alpha cash", AccountType.CASH),
            item(4, "Alpha bank", AccountType.CHECKING),
        )

        val names = items.sortedByTypeThenName().map { it.account.name }

        assertEquals(listOf("Alpha bank", "Beta bank", "Alpha cash", "Zed cash"), names)
    }

    @Test
    fun `within a group manual position wins over name`() {
        val items = listOf(
            item(1, "Alpha", AccountType.CHECKING, sortOrder = 2),
            item(2, "Beta", AccountType.CHECKING, sortOrder = 0),
            item(3, "Gamma", AccountType.CHECKING, sortOrder = 1),
        )

        val names = buildAccountTypeGroups(items).single().accounts.map { it.account.name }

        assertEquals(listOf("Beta", "Gamma", "Alpha"), names)
    }

    @Test
    fun `a single-currency group carries the balance subtotal`() {
        val items = listOf(
            item(1, "A", AccountType.CHECKING, balance = BigDecimal("10.00")),
            item(2, "B", AccountType.CHECKING, balance = BigDecimal("-4.50")),
        )

        val group = buildAccountTypeGroups(items).single()

        assertEquals(BigDecimal("5.50"), group.subtotal)
        assertEquals(eur, group.currency)
    }

    @Test
    fun `a mixed-currency group has no subtotal`() {
        val usd = Currency.getInstance("USD")
        val items = listOf(
            item(1, "A", AccountType.CHECKING, balance = BigDecimal("10.00")),
            item(2, "B", AccountType.CHECKING, balance = BigDecimal("5.00"), currency = usd),
        )

        val group = buildAccountTypeGroups(items).single()

        assertNull(group.subtotal)
        assertNull(group.currency)
    }

    @Test
    fun `the group as-of-today subtotal appears only on divergence`() {
        val items = listOf(
            item(1, "A", AccountType.CHECKING, balance = BigDecimal("100.00")),
            // Future-dated movements make this account run ahead of today.
            item(
                2, "B", AccountType.CHECKING,
                balance = BigDecimal("50.00"),
                balanceAsOfToday = BigDecimal("20.00"),
            ),
        )

        val group = buildAccountTypeGroups(items).single()

        assertEquals(BigDecimal("150.00"), group.subtotal)
        // 100 (no divergence, so its own balance) + 20 (as of today) = 120.
        assertEquals(BigDecimal("120.00"), group.subtotalAsOfToday)
    }

    @Test
    fun `the group as-of-today subtotal is null when nothing diverges`() {
        val items = listOf(
            item(1, "A", AccountType.CHECKING, balance = BigDecimal("100.00")),
            item(2, "B", AccountType.CHECKING, balance = BigDecimal("50.00")),
        )

        assertNull(buildAccountTypeGroups(items).single().subtotalAsOfToday)
    }
}
