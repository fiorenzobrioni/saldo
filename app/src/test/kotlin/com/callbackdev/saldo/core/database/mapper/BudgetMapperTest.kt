package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.BudgetEntity
import com.callbackdev.saldo.core.domain.model.Budget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency

class BudgetMapperTest {

    @Test
    fun `entity to domain scales minor units by the currency`() {
        val entity = BudgetEntity(
            id = 1L,
            categoryId = 7L,
            amountMinor = 80_000L,
            currency = "EUR",
        )

        val domain = entity.toDomain()

        assertEquals(BigDecimal("800.00"), domain.amount)
        assertEquals(Currency.getInstance("EUR"), domain.currency)
        assertEquals(7L, domain.categoryId)
    }

    @Test
    fun `zero fraction digit currencies keep whole amounts`() {
        val entity = BudgetEntity(
            id = 1L,
            categoryId = null,
            amountMinor = 80_000L,
            currency = "JPY",
        )

        assertEquals(BigDecimal("80000"), entity.toDomain().amount)
    }

    @Test
    fun `domain to entity and back is the identity`() {
        val domain = Budget(
            id = 3L,
            categoryId = null,
            amount = BigDecimal("450.50"),
            currency = Currency.getInstance("EUR"),
            lastNotified80Month = YearMonth.of(2026, 7),
            lastNotified100Month = YearMonth.of(2026, 6),
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }

    @Test
    fun `overall flag follows the category id`() {
        val overall = BudgetEntity(id = 1L, categoryId = null, amountMinor = 1L, currency = "EUR")
        val scoped = BudgetEntity(id = 2L, categoryId = 9L, amountMinor = 1L, currency = "EUR")

        assertEquals(true, overall.toDomain().isOverall)
        assertEquals(false, scoped.toDomain().isOverall)
        assertNull(overall.toDomain().lastNotified80Month)
    }

    @Test
    fun `epoch month round trips across year boundaries`() {
        listOf(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 12),
            YearMonth.of(1969, 12),
            YearMonth.of(1970, 1),
        ).forEach { month ->
            assertEquals(month, yearMonthOfEpochMonth(month.toEpochMonth()))
        }
    }

    @Test
    fun `epoch month is the proleptic month`() {
        assertEquals(2026L * 12 + 6, YearMonth.of(2026, 7).toEpochMonth())
    }
}
