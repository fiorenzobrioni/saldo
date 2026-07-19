package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.DailyNet
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
import java.time.LocalDate
import java.util.Currency

class ObserveDailyBalanceHistoryUseCaseTest {

    private val eur = Currency.getInstance("EUR")
    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val useCase = ObserveDailyBalanceHistoryUseCase(accountRepository, transactionRepository)

    private fun stub(initial: String, before: String, changes: List<DailyNet>) {
        every { accountRepository.observeInitialBalanceTotal(eur) } returns
            flowOf(BigDecimal(initial))
        every { transactionRepository.observeNetChangeBefore(eur, any()) } returns
            flowOf(BigDecimal(before))
        every { transactionRepository.observeDailyNetChanges(eur, any(), any()) } returns
            flowOf(changes)
    }

    private fun days(from: String, count: Int): List<LocalDate> {
        val start = LocalDate.parse(from)
        return (0 until count).map { start.plusDays(it.toLong()) }
    }

    @Test
    fun `cumulates the starting level and each day's net`() = runTest {
        stub(
            initial = "100.00",
            before = "0.00",
            changes = listOf(
                DailyNet(LocalDate.parse("2026-07-01"), BigDecimal("10.00")),
                DailyNet(LocalDate.parse("2026-07-02"), BigDecimal("-25.00")),
                DailyNet(LocalDate.parse("2026-07-03"), BigDecimal("5.00")),
            ),
        )

        val history = useCase(eur, days("2026-07-01", 3)).first()

        assertEquals(
            listOf("110.00", "85.00", "90.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `days without movements repeat the previous balance`() = runTest {
        stub(
            initial = "50.00",
            before = "0.00",
            changes = listOf(DailyNet(LocalDate.parse("2026-07-01"), BigDecimal("10.00"))),
        )

        val history = useCase(eur, days("2026-07-01", 4)).first()

        assertEquals(
            listOf("60.00", "60.00", "60.00", "60.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `movements before the window seed the starting balance`() = runTest {
        stub(
            initial = "0.00",
            before = "700.00",
            changes = listOf(DailyNet(LocalDate.parse("2026-07-02"), BigDecimal("50.00"))),
        )

        val history = useCase(eur, days("2026-07-01", 2)).first()

        assertEquals(
            listOf("700.00", "750.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `the last point equals the current total balance`() = runTest {
        // Invariant: initial + net before + every net in the window = the
        // dashboard's computed total balance shown above the sparkline.
        val changes = listOf(
            DailyNet(LocalDate.parse("2026-06-20"), BigDecimal("120.00")),
            DailyNet(LocalDate.parse("2026-07-05"), BigDecimal("-45.50")),
            DailyNet(LocalDate.parse("2026-07-15"), BigDecimal("3.25")),
        )
        stub(initial = "200.00", before = "80.00", changes = changes)
        val expectedTotal = changes.fold(BigDecimal("280.00")) { acc, c -> acc.add(c.net) }

        val history = useCase(eur, days("2026-06-19", 30)).first()

        assertEquals(expectedTotal, history.last().balance)
    }

    @Test
    fun `negative balances are preserved`() = runTest {
        stub(
            initial = "10.00",
            before = "0.00",
            changes = listOf(DailyNet(LocalDate.parse("2026-07-01"), BigDecimal("-35.00"))),
        )

        val history = useCase(eur, days("2026-07-01", 2)).first()

        assertEquals(
            listOf("-25.00", "-25.00"),
            history.map { it.balance.toPlainString() },
        )
    }

    @Test
    fun `single day window holds one point`() = runTest {
        stub(
            initial = "10.00",
            before = "5.00",
            changes = listOf(DailyNet(LocalDate.parse("2026-07-01"), BigDecimal("1.00"))),
        )

        val history = useCase(eur, days("2026-07-01", 1)).first()

        assertEquals(1, history.size)
        assertEquals("16.00", history.single().balance.toPlainString())
    }

    @Test
    fun `empty day window yields an empty series`() = runTest {
        stub(initial = "10.00", before = "0.00", changes = emptyList())

        assertEquals(emptyList<Any>(), useCase(eur, emptyList()).first())
    }
}
