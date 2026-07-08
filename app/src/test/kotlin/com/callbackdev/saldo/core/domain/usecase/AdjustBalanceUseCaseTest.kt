package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
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
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

class AdjustBalanceUseCaseTest {

    private val eur = Currency.getInstance("EUR")
    private val jpy = Currency.getInstance("JPY")
    private val fixedInstant = Instant.parse("2026-07-08T10:15:00Z")

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val useCase = AdjustBalanceUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        // Rome observes DST in July: expected offset +02:00.
        clock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Rome")),
    )

    private fun givenAccount(
        id: Long = 7L,
        currency: Currency = eur,
        currentBalance: BigDecimal,
    ) {
        coEvery { accountRepository.getAccount(id) } returns Account(
            id = id,
            name = "Checking",
            type = AccountType.CHECKING,
            currency = currency,
            initialBalance = BigDecimal("100.00"),
        )
        every { accountRepository.observeAccountBalance(id) } returns flowOf(currentBalance)
    }

    @Test
    fun `records a positive adjustment carrying the delta`() = runTest {
        givenAccount(currentBalance = BigDecimal("74.50"))
        val recorded = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(recorded)) } returns 1L

        val result = useCase(7L, BigDecimal("80.00"))

        assertEquals(AdjustBalanceUseCase.Result.Adjusted(BigDecimal("5.50")), result)
        with(recorded.captured) {
            assertEquals(TransactionType.ADJUSTMENT, type)
            assertEquals(BigDecimal("5.50"), amount)
            assertEquals(eur, currency)
            assertEquals(7L, accountId)
            assertEquals(fixedInstant, timestamp)
            assertEquals(ZoneOffset.ofHours(2), zoneOffset)
            assertEquals(null, categoryId)
        }
    }

    @Test
    fun `records a negative adjustment when the real balance is lower`() = runTest {
        givenAccount(currentBalance = BigDecimal("74.50"))
        val recorded = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(recorded)) } returns 1L

        val result = useCase(7L, BigDecimal("50.00"))

        assertEquals(AdjustBalanceUseCase.Result.Adjusted(BigDecimal("-24.50")), result)
        assertEquals(BigDecimal("-24.50"), recorded.captured.amount)
    }

    @Test
    fun `records nothing when the balance already matches`() = runTest {
        givenAccount(currentBalance = BigDecimal("74.50"))

        val result = useCase(7L, BigDecimal("74.50"))

        assertEquals(AdjustBalanceUseCase.Result.NoChange, result)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
    }

    @Test
    fun `rounds the typed balance to the currency scale half up`() = runTest {
        givenAccount(currentBalance = BigDecimal("100.00"))
        val recorded = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(recorded)) } returns 1L

        val result = useCase(7L, BigDecimal("100.005"))

        assertEquals(AdjustBalanceUseCase.Result.Adjusted(BigDecimal("0.01")), result)
        assertEquals(BigDecimal("0.01"), recorded.captured.amount)
    }

    @Test
    fun `zero-decimal currencies adjust on whole units`() = runTest {
        givenAccount(currency = jpy, currentBalance = BigDecimal("1000"))
        val recorded = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(recorded)) } returns 1L

        val result = useCase(7L, BigDecimal("1250.4"))

        assertEquals(AdjustBalanceUseCase.Result.Adjusted(BigDecimal("250")), result)
        assertEquals(jpy, recorded.captured.currency)
    }

    @Test
    fun `reports a missing account without recording anything`() = runTest {
        coEvery { accountRepository.getAccount(99L) } returns null

        val result = useCase(99L, BigDecimal("80.00"))

        assertEquals(AdjustBalanceUseCase.Result.AccountNotFound, result)
        coVerify(exactly = 0) { transactionRepository.upsert(any()) }
    }
}
