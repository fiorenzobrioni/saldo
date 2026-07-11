package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.MonthlyNet
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency

class ObserveBalanceHistoryUseCaseTest {

    private val eur = Currency.getInstance("EUR")
    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val useCase = ObserveBalanceHistoryUseCase(accountRepository, transactionRepository)

    private fun stub(initial: String, changes: List<MonthlyNet>) {
        every { accountRepository.observeInitialBalanceTotal(eur) } returns
            flowOf(BigDecimal(initial))
        every { transactionRepository.observeMonthlyNetChanges(eur) } returns flowOf(changes)
    }

    private fun months(from: String, count: Int): List<YearMonth> {
        val start = YearMonth.parse(from)
        return (0 until count).map { start.plusMonths(it.toLong()) }
    }

    @Test
    fun `cumulates the initial balance and each month's net`() = runTest {
        stub(
            initial = "100.00",
            changes = listOf(
                MonthlyNet(YearMonth.parse("2026-05"), BigDecimal("10.00")),
                MonthlyNet(YearMonth.parse("2026-06"), BigDecimal("-25.00")),
                MonthlyNet(YearMonth.parse("2026-07"), BigDecimal("5.00")),
            ),
        )

        val history = useCase(eur, months("2026-05", 3)).first()

        assertEquals(
            listOf("110.00", "85.00", "90.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `months without movements repeat the previous balance`() = runTest {
        stub(
            initial = "50.00",
            changes = listOf(MonthlyNet(YearMonth.parse("2026-05"), BigDecimal("10.00"))),
        )

        val history = useCase(eur, months("2026-05", 3)).first()

        assertEquals(
            listOf("60.00", "60.00", "60.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `history before the window seeds the first balance`() = runTest {
        stub(
            initial = "0.00",
            changes = listOf(
                MonthlyNet(YearMonth.parse("2025-01"), BigDecimal("1000.00")),
                MonthlyNet(YearMonth.parse("2025-06"), BigDecimal("-300.00")),
                MonthlyNet(YearMonth.parse("2026-06"), BigDecimal("50.00")),
            ),
        )

        val history = useCase(eur, months("2026-06", 2)).first()

        assertEquals(
            listOf("750.00", "750.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `the last point equals the current total balance`() = runTest {
        // Invariant: initial + every net = the dashboard's computed balance.
        val changes = listOf(
            MonthlyNet(YearMonth.parse("2026-01"), BigDecimal("120.00")),
            MonthlyNet(YearMonth.parse("2026-03"), BigDecimal("-45.50")),
            MonthlyNet(YearMonth.parse("2026-07"), BigDecimal("3.25")),
        )
        stub(initial = "200.00", changes = changes)
        val expectedTotal = changes.fold(BigDecimal("200.00")) { acc, c -> acc.add(c.net) }

        val history = useCase(eur, months("2025-08", 12)).first()

        assertEquals(expectedTotal, history.last().balance)
    }

    @Test
    fun `empty month window yields an empty series`() = runTest {
        stub(initial = "10.00", changes = emptyList())

        assertEquals(emptyList<Any>(), useCase(eur, emptyList()).first())
    }
}
