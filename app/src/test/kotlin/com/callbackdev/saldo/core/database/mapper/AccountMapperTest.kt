package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.relation.AccountWithBalanceRow
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class AccountMapperTest {

    private val eur = Currency.getInstance("EUR")

    @Test
    fun `round trip preserves an account`() {
        val account = Account(
            name = "Conto Intesa",
            type = AccountType.CHECKING,
            currency = eur,
            initialBalance = BigDecimal("1250.75"),
            id = 9L,
            color = 0x5C6BC0,
            icon = "account_balance",
            isIncludedInTotal = false,
            isIncludedInBudget = false,
            isArchived = true,
            sortOrder = 3,
        )

        val restored = account.toEntity().toDomain()

        assertEquals(account.id, restored.id)
        assertEquals(account.name, restored.name)
        assertEquals(account.type, restored.type)
        assertEquals(account.currency, restored.currency)
        assertEquals(0, account.initialBalance.compareTo(restored.initialBalance))
        assertEquals(account.color, restored.color)
        assertEquals(account.icon, restored.icon)
        assertEquals(account.isIncludedInTotal, restored.isIncludedInTotal)
        assertEquals(account.isIncludedInBudget, restored.isIncludedInBudget)
        assertEquals(account.isArchived, restored.isArchived)
        assertEquals(account.sortOrder, restored.sortOrder)
    }

    @Test
    fun `round trip preserves the credit card configuration`() {
        val account = Account(
            name = "Carta di credito",
            type = AccountType.CREDIT_CARD,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
            id = 4L,
            creditCard = CreditCardConfig(
                statementClosingDay = 20,
                paymentDueDay = 5,
                linkedAccountId = 2L,
                creditLimit = BigDecimal("1500.00"),
                autoPost = true,
                lastSettledClosing = LocalDate.of(2024, 2, 20),
            ),
        )

        val restored = account.toEntity().toDomain().creditCard!!

        assertEquals(20, restored.statementClosingDay)
        assertEquals(5, restored.paymentDueDay)
        assertEquals(2L, restored.linkedAccountId)
        assertEquals(0, BigDecimal("1500.00").compareTo(restored.creditLimit))
        assertEquals(true, restored.autoPost)
        assertEquals(LocalDate.of(2024, 2, 20), restored.lastSettledClosing)
    }

    @Test
    fun `a non credit card has no credit card config`() {
        val restored = Account(
            name = "Cash",
            type = AccountType.CASH,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
        ).toEntity().toDomain()

        assertNull(restored.creditCard)
    }

    @Test
    fun `balance row maps minor units to the account currency scale`() {
        val entity = Account(
            name = "Cash",
            type = AccountType.CASH,
            currency = eur,
            initialBalance = BigDecimal("0.00"),
        ).toEntity()

        val domain = AccountWithBalanceRow(account = entity, balanceMinor = -1234L).toDomain()

        assertEquals(0, domain.balance.compareTo(BigDecimal("-12.34")))
        assertEquals(2, domain.balance.scale())
    }
}
