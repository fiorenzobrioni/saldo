package com.callbackdev.saldo.core.domain.rates

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.DashboardTotals
import com.callbackdev.saldo.core.domain.model.ForeignCategoryDayTotal
import com.callbackdev.saldo.core.domain.model.ForeignDashboardDayFlows
import com.callbackdev.saldo.core.domain.model.ForeignMonthlyDayTotal
import com.callbackdev.saldo.core.domain.model.MonthlyTotal
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

class ConvertedAggregatesTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val chf: Currency = Currency.getInstance("CHF")

    private val day = LocalDate.of(2026, 7, 15)

    /** 1 EUR = 2 USD keeps the arithmetic legible: USD amounts halve. */
    private val table = RateTable.of(listOf(ExchangeRate("USD", day, BigDecimal("2"))))

    @Test
    fun `merges dashboard totals converting each bucket at the rate of its day`() {
        val base = DashboardTotals(
            today = PeriodTotals(spend = BigDecimal("-10.00"), income = BigDecimal("5.00")),
            month = PeriodTotals(spend = BigDecimal("-100.00"), income = BigDecimal("50.00")),
            monthToDateSpend = BigDecimal("100.00"),
            monthToDateNonRecurringSpend = BigDecimal("80.00"),
            previousMonthToDateSpend = BigDecimal("90.00"),
        )
        val foreign = ForeignDashboardDayFlows(
            currency = usd,
            day = day,
            today = PeriodTotals(spend = BigDecimal("-20.00"), income = BigDecimal.ZERO),
            month = PeriodTotals(spend = BigDecimal("-20.00"), income = BigDecimal("10.00")),
            monthToDateSpend = BigDecimal("20.00"),
            monthToDateNonRecurringSpend = BigDecimal("20.00"),
            previousToDateSpend = BigDecimal("40.00"),
        )

        val merged = ConvertedAggregates.mergeDashboardTotals(base, listOf(foreign), eur, table)

        assertEquals(BigDecimal("-20.00"), merged.value.today.spend)
        assertEquals(BigDecimal("5.00"), merged.value.today.income)
        assertEquals(BigDecimal("-110.00"), merged.value.month.spend)
        assertEquals(BigDecimal("55.00"), merged.value.month.income)
        assertEquals(BigDecimal("110.00"), merged.value.monthToDateSpend)
        assertEquals(BigDecimal("90.00"), merged.value.monthToDateNonRecurringSpend)
        assertEquals(BigDecimal("110.00"), merged.value.previousMonthToDateSpend)
        assertTrue(merged.includesEstimates)
    }

    @Test
    fun `with no foreign buckets the merge is the identity - the single-currency case is untouched`() {
        val base = DashboardTotals(monthToDateSpend = BigDecimal("42.00"))

        val merged = ConvertedAggregates.mergeDashboardTotals(base, emptyList(), eur, table)

        assertEquals(base, merged.value)
        assertFalse(merged.includesEstimates)
        assertTrue(merged.unconvertedCurrencies.isEmpty())
    }

    @Test
    fun `a currency without rates stays out and is reported, not guessed`() {
        val base = DashboardTotals(monthToDateSpend = BigDecimal("42.00"))
        val foreign = ForeignDashboardDayFlows(
            currency = chf,
            day = day,
            today = PeriodTotals(),
            month = PeriodTotals(),
            monthToDateSpend = BigDecimal("99.00"),
            monthToDateNonRecurringSpend = BigDecimal.ZERO,
            previousToDateSpend = BigDecimal.ZERO,
        )

        val merged = ConvertedAggregates.mergeDashboardTotals(base, listOf(foreign), eur, table)

        assertEquals(base, merged.value)
        assertFalse(merged.includesEstimates)
        assertEquals(setOf("CHF"), merged.unconvertedCurrencies)
    }

    @Test
    fun `category totals merge by category, adding converted amounts and counts`() {
        val base = listOf(CategoryTotal(categoryId = 1L, total = BigDecimal("-30.00"), count = 3))
        val foreign = listOf(
            ForeignCategoryDayTotal(1L, usd, day, BigDecimal("-20.00"), count = 2),
            ForeignCategoryDayTotal(2L, usd, day, BigDecimal("-40.00"), count = 1),
        )

        val merged = ConvertedAggregates.mergeCategoryTotals(base, foreign, eur, table)

        val byId = merged.value.associateBy { it.categoryId }
        assertEquals(BigDecimal("-40.00"), byId[1L]?.total)
        assertEquals(5, byId[1L]?.count)
        assertEquals(BigDecimal("-20.00"), byId[2L]?.total)
        assertEquals(1, byId[2L]?.count)
        assertEquals(2, merged.convertedCount)
    }

    @Test
    fun `monthly buckets re-bucket into the month of the movement's own day`() {
        val june = YearMonth.of(2026, 6)
        val base = listOf(
            MonthlyTotal(june, expense = BigDecimal("-10.00"), income = BigDecimal.ZERO),
        )
        val foreign = listOf(
            ForeignMonthlyDayTotal(usd, june.atDay(10), BigDecimal("-20.00"), BigDecimal.ZERO),
            ForeignMonthlyDayTotal(usd, day, BigDecimal("-2.00"), BigDecimal("4.00")),
        )

        val merged = ConvertedAggregates.mergeMonthlyTotals(base, foreign, eur, table)

        val byMonth = merged.value.associateBy { it.month }
        assertEquals(BigDecimal("-20.00"), byMonth[june]?.expense)
        assertEquals(BigDecimal("-1.00"), byMonth[YearMonth.from(day)]?.expense)
        assertEquals(BigDecimal("2.00"), byMonth[YearMonth.from(day)]?.income)
        // Sorted ascending by month.
        assertEquals(listOf(june, YearMonth.from(day)), merged.value.map { it.month })
    }

    @Test
    fun `the total balance counts foreign stocks at the latest rate and exposes per-account countervalues`() {
        val accounts = listOf(
            item(1L, eur, "100.00"),
            item(2L, usd, "50.00"),
            item(3L, usd, "10.00", includedInTotal = false),
            item(4L, chf, "999.00"),
            item(5L, eur, "888.00", archived = true),
        )

        val balance = ConvertedAggregates.convertTotalBalance(accounts, eur, table)

        // 100 EUR + 50 USD / 2 = 125; CHF has no rate, the archived one never counts.
        assertEquals(BigDecimal("125.00"), balance.total)
        assertEquals(day, balance.rateDay)
        assertEquals(1, balance.convertedCount)
        // The excluded USD account still gets a countervalue for its row.
        assertEquals(BigDecimal("5.00"), balance.countervalues[3L]?.amount)
        assertEquals(setOf("CHF"), balance.unconvertedCurrencies)
    }

    @Test
    fun `with an empty table the total balance is the plain primary-only sum`() {
        val accounts = listOf(
            item(1L, eur, "100.00"),
            item(2L, usd, "50.00"),
        )

        val balance = ConvertedAggregates.convertTotalBalance(accounts, eur, RateTable.EMPTY)

        assertEquals(BigDecimal("100.00"), balance.total)
        assertEquals(0, balance.convertedCount)
        assertNull(balance.rateDay)
        assertTrue(balance.countervalues.isEmpty())
    }

    private fun item(
        id: Long,
        currency: Currency,
        balance: String,
        includedInTotal: Boolean = true,
        archived: Boolean = false,
    ): AccountWithBalance = AccountWithBalance(
        account = Account(
            id = id,
            name = "account-$id",
            type = AccountType.CHECKING,
            currency = currency,
            initialBalance = BigDecimal.ZERO,
            isIncludedInTotal = includedInTotal,
            isArchived = archived,
        ),
        balance = BigDecimal(balance),
    )
}
