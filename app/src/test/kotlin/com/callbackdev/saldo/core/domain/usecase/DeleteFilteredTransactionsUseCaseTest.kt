package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

class DeleteFilteredTransactionsUseCaseTest {

    private val eur = Currency.getInstance("EUR")

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val useCase = DeleteFilteredTransactionsUseCase(accountRepository, transactionRepository)

    private val checking = Account(
        id = 1L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )
    private val savings = Account(
        id = 2L,
        name = "Savings",
        type = AccountType.SAVINGS,
        currency = eur,
        initialBalance = BigDecimal.ZERO,
    )

    private fun tx(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String,
        accountId: Long = checking.id,
        timestamp: Instant = Instant.parse("2025-06-01T10:00:00Z"),
        transferAccountId: Long? = null,
        transferAmount: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = accountId,
        timestamp = timestamp,
        zoneOffset = ZoneOffset.ofHours(2),
        transferAccountId = transferAccountId,
        transferAmount = transferAmount?.let(::BigDecimal),
        transferCurrency = transferAccountId?.let { eur },
    )

    private fun givenAccounts(vararg accounts: Account) {
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(accounts.map { AccountWithBalance(it, BigDecimal.ZERO) })
    }

    @Test
    fun `recompute mode deletes by id and creates no carry-over`() = runTest {
        val transactions = listOf(tx(1L, amount = "-10.00"), tx(2L, amount = "-5.00"))

        val carryOverIds = useCase(transactions, preserveBalances = false)

        assertTrue(carryOverIds.isEmpty())
        coVerify { transactionRepository.deleteByIds(listOf(1L, 2L)) }
        coVerify(exactly = 0) { transactionRepository.deleteAndInsert(any(), any()) }
    }

    @Test
    fun `preserve mode carries over the net per account and returns the created ids`() = runTest {
        givenAccounts(checking, savings)
        val transactions = listOf(
            tx(1L, amount = "-10.00", accountId = checking.id),
            tx(2L, type = TransactionType.INCOME, amount = "30.00", accountId = checking.id),
            tx(3L, amount = "-5.00", accountId = savings.id),
        )
        val inserts = slot<List<Transaction>>()
        coEvery {
            transactionRepository.deleteAndInsert(listOf(1L, 2L, 3L), capture(inserts))
        } returns listOf(101L, 102L)

        val carryOverIds = useCase(transactions, preserveBalances = true, carryOverDescription = "Carry")

        assertEquals(listOf(101L, 102L), carryOverIds)
        val byAccount = inserts.captured.associateBy { it.accountId }
        // Checking net = -10 + 30 = +20; the carry-over adds it back to keep the balance.
        assertEquals(BigDecimal("20.00"), byAccount.getValue(checking.id).amount)
        assertEquals(BigDecimal("-5.00"), byAccount.getValue(savings.id).amount)
        inserts.captured.forEach {
            assertEquals(TransactionType.ADJUSTMENT, it.type)
            assertEquals("Carry", it.description)
        }
    }

    @Test
    fun `preserve mode carries a transfer onto both accounts`() = runTest {
        givenAccounts(checking, savings)
        val transfer = tx(
            id = 1L,
            type = TransactionType.TRANSFER,
            amount = "-40.00",
            accountId = checking.id,
            transferAccountId = savings.id,
            transferAmount = "40.00",
        )
        val inserts = slot<List<Transaction>>()
        coEvery { transactionRepository.deleteAndInsert(listOf(1L), capture(inserts)) } returns
            listOf(201L, 202L)

        useCase(listOf(transfer), preserveBalances = true)

        val byAccount = inserts.captured.associateBy { it.accountId }
        assertEquals(BigDecimal("-40.00"), byAccount.getValue(checking.id).amount)
        assertEquals(BigDecimal("40.00"), byAccount.getValue(savings.id).amount)
    }

    @Test
    fun `preserve mode skips accounts whose net cancels out`() = runTest {
        givenAccounts(checking)
        val transactions = listOf(
            tx(1L, amount = "-10.00"),
            tx(2L, type = TransactionType.INCOME, amount = "10.00"),
        )
        val inserts = slot<List<Transaction>>()
        coEvery { transactionRepository.deleteAndInsert(listOf(1L, 2L), capture(inserts)) } returns
            emptyList()

        useCase(transactions, preserveBalances = true)

        assertTrue(inserts.captured.isEmpty())
    }

    @Test
    fun `the carry-over sits at the most recent deleted movement`() = runTest {
        givenAccounts(checking)
        val early = tx(1L, amount = "-10.00", timestamp = Instant.parse("2025-01-01T00:00:00Z"))
        val late = tx(2L, amount = "-5.00", timestamp = Instant.parse("2025-12-31T00:00:00Z"))
        val inserts = slot<List<Transaction>>()
        coEvery { transactionRepository.deleteAndInsert(any(), capture(inserts)) } returns listOf(1L)

        useCase(listOf(early, late), preserveBalances = true)

        assertEquals(Instant.parse("2025-12-31T00:00:00Z"), inserts.captured.single().timestamp)
    }

    @Test
    fun `empty input does nothing`() = runTest {
        val carryOverIds = useCase(emptyList(), preserveBalances = true)

        assertTrue(carryOverIds.isEmpty())
        coVerify(exactly = 0) { transactionRepository.deleteByIds(any()) }
        coVerify(exactly = 0) { transactionRepository.deleteAndInsert(any(), any()) }
    }
}
