package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DailyActivity
import com.callbackdev.saldo.core.domain.model.StatsPeriodTotals
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

class GetMonthlyRecapUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), zone)
    private val month: YearMonth = YearMonth.of(2026, 6)

    private val repository = mockk<TransactionRepository>()
    private val useCase = GetMonthlyRecapUseCase(repository, clock)

    private fun stub(
        totals: StatsPeriodTotals = StatsPeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO),
        categoryTotals: List<CategoryTotal> = emptyList(),
        activity: List<DailyActivity> = emptyList(),
        biggestExpense: Transaction? = null,
        recurringSpend: BigDecimal = BigDecimal.ZERO,
        previousTotals: StatsPeriodTotals = StatsPeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO),
        previousCategoryTotals: List<CategoryTotal> = emptyList(),
    ) {
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant()
        coEvery { repository.getStatsPeriodTotals(monthStart, any(), eur) } returns totals
        coEvery { repository.getCategoryTotals(monthStart, any(), eur) } returns categoryTotals
        coEvery { repository.getDailyActivity(any(), any(), eur) } returns activity
        coEvery { repository.getBiggestExpense(any(), any(), eur) } returns biggestExpense
        coEvery { repository.getRecurringSpendTotal(any(), any(), eur) } returns recurringSpend
        val previousStart = month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        coEvery { repository.getStatsPeriodTotals(previousStart, monthStart, eur) } returns previousTotals
        coEvery { repository.getCategoryTotals(previousStart, monthStart, eur) } returns previousCategoryTotals
    }

    private fun expense(
        id: Long,
        amount: String,
        date: LocalDate,
        description: String? = null,
        categoryId: Long? = null,
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = 1L,
        timestamp = date.atTime(12, 0).toInstant(ZoneOffset.ofHours(2)),
        zoneOffset = ZoneOffset.ofHours(2),
        categoryId = categoryId,
        description = description,
    )

    @Test
    fun `expense total is a positive magnitude with refunds netted`() = runTest {
        stub(
            totals = StatsPeriodTotals(BigDecimal("-350.00"), BigDecimal("2000.00")),
            activity = listOf(DailyActivity(month.atDay(3), 4, BigDecimal("-350.00"))),
        )

        val recap = useCase(month, eur)

        assertEquals(BigDecimal("350.00"), recap.expenseTotal)
        assertEquals(BigDecimal("2000.00"), recap.incomeTotal)
        assertEquals(BigDecimal("1650.00"), recap.net)
        assertTrue(recap.hasData)
    }

    @Test
    fun `a refund-dominated month clamps the expense total to zero`() = runTest {
        stub(totals = StatsPeriodTotals(BigDecimal("40.00"), BigDecimal.ZERO))

        assertEquals(BigDecimal.ZERO, useCase(month, eur).expenseTotal)
    }

    @Test
    fun `previous delta is null without a baseline month`() = runTest {
        stub(previousCategoryTotals = emptyList())

        assertNull(useCase(month, eur).previousExpenseTotal)
    }

    @Test
    fun `previous month spend is a positive magnitude when tracked`() = runTest {
        stub(
            previousCategoryTotals = listOf(CategoryTotal(1L, BigDecimal("-120.00"), 3)),
            previousTotals = StatsPeriodTotals(BigDecimal("-120.00"), BigDecimal.ZERO),
        )

        assertEquals(BigDecimal("120.00"), useCase(month, eur).previousExpenseTotal)
    }

    @Test
    fun `top categories are ordered capped at five with whole-month percents`() = runTest {
        val totals = (1L..7L).map { id ->
            CategoryTotal(id, BigDecimal("-${id * 10}.00"), id.toInt())
        }
        stub(categoryTotals = totals)

        val top = useCase(month, eur).topCategories

        assertEquals(5, top.size)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), top.map { it.categoryId })
        assertEquals(BigDecimal("70.00"), top.first().amount)
        // Percent shares are of the whole month's spend (280), not the top five.
        assertEquals(25, top.first().percent)
    }

    @Test
    fun `refund-dominated categories are excluded, uncategorized bucket kept`() = runTest {
        stub(
            categoryTotals = listOf(
                CategoryTotal(1L, BigDecimal("-90.00"), 2),
                CategoryTotal(2L, BigDecimal("15.00"), 1),
                CategoryTotal(null, BigDecimal("-10.00"), 1),
            ),
        )

        val top = useCase(month, eur).topCategories

        assertEquals(listOf(1L, null), top.map { it.categoryId })
        assertEquals(BigDecimal("10.00"), top.last().amount)
    }

    @Test
    fun `biggest expense carries magnitude, description and local date`() = runTest {
        stub(
            biggestExpense = expense(
                id = 9L,
                amount = "-89.90",
                date = LocalDate.of(2026, 6, 15),
                description = "Concert tickets",
                categoryId = 4L,
            ),
        )

        val biggest = useCase(month, eur).biggestExpense

        assertEquals(BigDecimal("89.90"), biggest?.amount)
        assertEquals("Concert tickets", biggest?.description)
        assertEquals(4L, biggest?.categoryId)
        assertEquals(LocalDate.of(2026, 6, 15), biggest?.date)
    }

    @Test
    fun `busiest day is picked by count with spend tie-break`() = runTest {
        stub(
            activity = listOf(
                DailyActivity(month.atDay(2), 3, BigDecimal("-20.00")),
                DailyActivity(month.atDay(10), 5, BigDecimal("-15.00")),
                DailyActivity(month.atDay(20), 5, BigDecimal("-45.00")),
            ),
        )

        val busiest = useCase(month, eur).busiestDay

        assertEquals(month.atDay(20), busiest?.date)
        assertEquals(5, busiest?.count)
        assertEquals(BigDecimal("45.00"), busiest?.spend)
    }

    @Test
    fun `recurring spend is a positive magnitude and zero when none`() = runTest {
        stub(recurringSpend = BigDecimal("-47.97"))
        assertEquals(BigDecimal("47.97"), useCase(month, eur).recurringSpend)

        stub(recurringSpend = BigDecimal.ZERO)
        assertEquals(BigDecimal.ZERO, useCase(month, eur).recurringSpend)
    }

    @Test
    fun `savings rate is the floor percent of income kept`() = runTest {
        stub(totals = StatsPeriodTotals(BigDecimal("-1234.00"), BigDecimal("2000.00")))

        // Net 766 over 2000 = 38.3%, floored.
        assertEquals(38, useCase(month, eur).savingsRatePercent)
    }

    @Test
    fun `savings rate is null without positive income and positive net`() = runTest {
        stub(totals = StatsPeriodTotals(BigDecimal("-100.00"), BigDecimal.ZERO))
        assertNull(useCase(month, eur).savingsRatePercent)

        stub(totals = StatsPeriodTotals(BigDecimal("-300.00"), BigDecimal("200.00")))
        assertNull(useCase(month, eur).savingsRatePercent)
    }

    @Test
    fun `an empty month has no data`() = runTest {
        stub()

        val recap = useCase(month, eur)

        assertFalse(recap.hasData)
        assertEquals(0, recap.movementCount)
        assertNull(recap.biggestExpense)
        assertNull(recap.busiestDay)
        assertTrue(recap.topCategories.isEmpty())
    }

    @Test
    fun `windows are calendar months in the device zone`() = runTest {
        val start = slot<Instant>()
        val end = slot<Instant>()
        stub()
        coEvery { repository.getDailyActivity(capture(start), capture(end), eur) } returns emptyList()

        useCase(month, eur)

        assertEquals(month.atDay(1).atStartOfDay(zone).toInstant(), start.captured)
        assertEquals(month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(), end.captured)
    }
}
