package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Budget
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryTotal
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Currency

class ObserveBudgetProgressUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val usd: Currency = Currency.getInstance("USD")
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-07-12T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val budgetRepository = mockk<BudgetRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()

    private val groceries = Category(
        id = 10L,
        name = "Groceries",
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "cart",
    )
    private val transport = Category(
        id = 11L,
        name = "Transport",
        type = CategoryType.EXPENSE,
        color = 0x111111,
        icon = "bus",
    )

    private fun useCase(
        budgets: List<Budget>,
        totalSpend: BigDecimal = BigDecimal.ZERO,
        categorySpends: List<CategoryTotal> = emptyList(),
        categories: List<Category> = listOf(groceries, transport),
    ): ObserveBudgetProgressUseCase {
        every { budgetRepository.observeBudgets() } returns flowOf(budgets)
        every {
            transactionRepository.observeStatsSpendTotal(any(), any(), any())
        } returns flowOf(totalSpend)
        every {
            transactionRepository.observeCategorySpendTotals(any(), any(), any())
        } returns flowOf(categorySpends)
        every { categoryRepository.observeCategories() } returns flowOf(categories)
        return ObserveBudgetProgressUseCase(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            clock = clock,
        )
    }

    private fun overall(amount: String) =
        Budget(id = 1L, categoryId = null, amount = BigDecimal(amount), currency = eur)

    private fun forCategory(id: Long, categoryId: Long, amount: String, currency: Currency = eur) =
        Budget(id = id, categoryId = categoryId, amount = BigDecimal(amount), currency = currency)

    @Test
    fun `thresholds are exact on minor units`() = runTest {
        // Signed convention: spend arrives negative from the queries.
        suspend fun levelFor(spend: String): BudgetLevel =
            useCase(budgets = listOf(overall("100.00")), totalSpend = BigDecimal(spend))
                .invoke(eur).first().single().level

        assertEquals(BudgetLevel.UNDER, levelFor("-79.99"))
        assertEquals(BudgetLevel.WARNING, levelFor("-80.00"))
        assertEquals(BudgetLevel.WARNING, levelFor("-99.99"))
        assertEquals(BudgetLevel.OVER, levelFor("-100.00"))
        assertEquals(BudgetLevel.OVER, levelFor("-250.00"))
    }

    @Test
    fun `a refund-only month reads as zero spent, never negative`() = runTest {
        val progress = useCase(
            budgets = listOf(overall("100.00")),
            // Net positive: refunds exceeded expenses this month.
            totalSpend = BigDecimal("25.00"),
        ).invoke(eur).first().single()

        assertEquals(BigDecimal.ZERO, progress.spent)
        assertEquals(0f, progress.fraction)
        assertEquals(BudgetLevel.UNDER, progress.level)
    }

    @Test
    fun `budgets in another currency are not listed`() = runTest {
        val progresses = useCase(
            budgets = listOf(
                overall("100.00"),
                forCategory(id = 2L, categoryId = 10L, amount = "50.00", currency = usd),
            ),
        ).invoke(eur).first()

        assertEquals(listOf(1L), progresses.map { it.budget.id })
    }

    @Test
    fun `overall comes first and categories sort by descending fraction`() = runTest {
        val progresses = useCase(
            budgets = listOf(
                forCategory(id = 2L, categoryId = 10L, amount = "100.00"),
                forCategory(id = 3L, categoryId = 11L, amount = "100.00"),
                overall("500.00"),
            ),
            totalSpend = BigDecimal("-120.00"),
            categorySpends = listOf(
                CategoryTotal(categoryId = 10L, total = BigDecimal("-20.00"), count = 2),
                CategoryTotal(categoryId = 11L, total = BigDecimal("-90.00"), count = 3),
            ),
        ).invoke(eur).first()

        assertEquals(listOf(1L, 3L, 2L), progresses.map { it.budget.id })
        assertTrue(progresses.first().budget.isOverall)
        assertEquals(transport, progresses[1].category)
    }

    @Test
    fun `category spend maps by id and missing spend means zero`() = runTest {
        val progresses = useCase(
            budgets = listOf(forCategory(id = 2L, categoryId = 10L, amount = "80.00")),
            categorySpends = listOf(
                CategoryTotal(categoryId = 11L, total = BigDecimal("-70.00"), count = 1),
            ),
        ).invoke(eur).first()

        assertEquals(BigDecimal.ZERO, progresses.single().spent)
        assertEquals(BudgetLevel.UNDER, progresses.single().level)
    }

    @Test
    fun `a budget whose category vanished is skipped`() = runTest {
        val progresses = useCase(
            budgets = listOf(forCategory(id = 2L, categoryId = 99L, amount = "80.00")),
        ).invoke(eur).first()

        assertTrue(progresses.isEmpty())
    }

    @Test
    fun `fraction reports the overshoot instead of capping at one`() = runTest {
        val progress = useCase(
            budgets = listOf(overall("100.00")),
            totalSpend = BigDecimal("-112.00"),
        ).invoke(eur).first().single()

        assertEquals(1.12f, progress.fraction, 0.0001f)
    }
}
