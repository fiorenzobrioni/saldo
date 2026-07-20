package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class AccountsGroupingTest {

    private val eur = Currency.getInstance("EUR")

    private fun item(
        id: Long,
        name: String,
        type: AccountType,
    ) = AccountWithBalance(
        account = Account(
            id = id,
            name = name,
            type = type,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
        ),
        balance = BigDecimal.ZERO,
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
}
