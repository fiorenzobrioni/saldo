package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency

class CheckBudgetThresholdsUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-07-12T09:00:00Z"), ZoneId.of("Europe/Rome"))
    private val month: YearMonth = YearMonth.of(2026, 7)

    private val budgetRepository = mockk<BudgetRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val exchangeRateRepository = mockk<ExchangeRateRepository>()

    private val marked80 = mutableListOf<Pair<Long, YearMonth>>()
    private val marked100 = mutableListOf<Pair<Long, YearMonth>>()

    private fun useCase(
        budgets: List<Budget>,
        totalSpend: BigDecimal = BigDecimal.ZERO,
        categorySpends: List<CategoryTotal> = emptyList(),
    ): CheckBudgetThresholdsUseCase {
        marked80.clear()
        marked100.clear()
        coEvery { budgetRepository.getBudgets() } returns budgets
        coEvery { budgetRepository.markNotified80(any(), any()) } answers {
            marked80.add(firstArg<Long>() to secondArg())
        }
        coEvery { budgetRepository.markNotified100(any(), any()) } answers {
            marked100.add(firstArg<Long>() to secondArg())
        }
        coEvery { transactionRepository.getStatsSpendTotal(any(), any(), any()) } returns totalSpend
        coEvery {
            transactionRepository.getCategorySpendTotals(any(), any(), any())
        } returns categorySpends
        coEvery { categoryRepository.getCategory(10L) } returns Category(
            id = 10L,
            name = "Groceries",
            type = CategoryType.EXPENSE,
            color = 0x66BB6A,
            icon = "cart",
        )
        // Conversion off: the single-currency path these tests were written for.
        every { userPreferences.currencyConversionEnabled } returns flowOf(false)
        return CheckBudgetThresholdsUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            userPreferences = userPreferences,
            exchangeRateRepository = exchangeRateRepository,
            clock = clock,
        )
    }

    private fun overall(
        amount: String = "100.00",
        notified80: YearMonth? = null,
        notified100: YearMonth? = null,
    ) = Budget(
        id = 1L,
        categoryId = null,
        amount = BigDecimal(amount),
        currency = eur,
        lastNotified80Month = notified80,
        lastNotified100Month = notified100,
    )

    @Test
    fun `crossing 80 percent alerts once and advances the watermark`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall()),
            totalSpend = BigDecimal("-85.00"),
        ).invoke()

        val alert = alerts.single()
        assertEquals(BudgetLevel.WARNING, alert.level)
        assertEquals(85, alert.percent)
        assertEquals(listOf(1L to month), marked80)
        assertTrue(marked100.isEmpty())
    }

    @Test
    fun `already notified this month stays silent`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall(notified80 = month)),
            totalSpend = BigDecimal("-85.00"),
        ).invoke()

        assertTrue(alerts.isEmpty())
        assertTrue(marked80.isEmpty())
    }

    @Test
    fun `a watermark from a previous month re-arms the alert`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall(notified80 = YearMonth.of(2026, 6))),
            totalSpend = BigDecimal("-85.00"),
        ).invoke()

        assertEquals(BudgetLevel.WARNING, alerts.single().level)
        assertEquals(listOf(1L to month), marked80)
    }

    @Test
    fun `jumping straight past the limit produces a single OVER alert`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall()),
            totalSpend = BigDecimal("-140.00"),
        ).invoke()

        val alert = alerts.single()
        assertEquals(BudgetLevel.OVER, alert.level)
        assertEquals(140, alert.percent)
        // markNotified100 advances both watermarks; no separate WARNING fires.
        assertEquals(listOf(1L to month), marked100)
        assertTrue(marked80.isEmpty())
    }

    @Test
    fun `exceeding after an earlier warning still alerts OVER in the same month`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall(notified80 = month)),
            totalSpend = BigDecimal("-101.00"),
        ).invoke()

        assertEquals(BudgetLevel.OVER, alerts.single().level)
        assertEquals(listOf(1L to month), marked100)
    }

    @Test
    fun `dropping back under a threshold never resets the watermark`() = runTest {
        val alerts = useCase(
            budgets = listOf(overall(notified80 = month, notified100 = month)),
            totalSpend = BigDecimal("-10.00"),
        ).invoke()

        assertTrue(alerts.isEmpty())
        assertTrue(marked80.isEmpty())
        assertTrue(marked100.isEmpty())
    }

    @Test
    fun `category budgets are evaluated on their own spend and named`() = runTest {
        val alerts = useCase(
            budgets = listOf(
                overall(amount = "1000.00"),
                Budget(id = 2L, categoryId = 10L, amount = BigDecimal("50.00"), currency = eur),
            ),
            totalSpend = BigDecimal("-60.00"),
            categorySpends = listOf(
                CategoryTotal(categoryId = 10L, total = BigDecimal("-60.00"), count = 4),
            ),
        ).invoke()

        val alert = alerts.single()
        assertEquals(2L, alert.budget.id)
        assertEquals("Groceries", alert.categoryName)
        assertEquals(BudgetLevel.OVER, alert.level)
        assertEquals(120, alert.percent)
    }

    @Test
    fun `no budgets means no queries and no alerts`() = runTest {
        val alerts = useCase(budgets = emptyList()).invoke()

        assertTrue(alerts.isEmpty())
        coVerify(exactly = 0) { transactionRepository.getStatsSpendTotal(any(), any(), any()) }
    }
}
