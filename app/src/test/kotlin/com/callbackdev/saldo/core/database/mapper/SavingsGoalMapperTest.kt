package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.SavingsGoalEntity
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class SavingsGoalMapperTest {

    @Test
    fun `entity to domain scales minor units and parses the date`() {
        val entity = SavingsGoalEntity(
            id = 4L,
            name = "Holiday",
            targetAmountMinor = 200_000L,
            currency = "EUR",
            accountId = 9L,
            targetDateEpochDay = LocalDate.of(2026, 12, 31).toEpochDay(),
            color = 0x66BB6A,
            icon = "beach",
        )

        val domain = entity.toDomain()

        assertEquals(BigDecimal("2000.00"), domain.targetAmount)
        assertEquals(Currency.getInstance("EUR"), domain.currency)
        assertEquals(9L, domain.accountId)
        assertEquals(LocalDate.of(2026, 12, 31), domain.targetDate)
    }

    @Test
    fun `zero fraction digit currencies keep whole amounts`() {
        val entity = SavingsGoalEntity(
            id = 1L,
            name = "Car",
            targetAmountMinor = 500_000L,
            currency = "JPY",
            accountId = 2L,
        )

        assertEquals(BigDecimal("500000"), entity.toDomain().targetAmount)
        assertNull(entity.toDomain().targetDate)
    }

    @Test
    fun `domain to entity and back is the identity`() {
        val domain = SavingsGoal(
            id = 3L,
            name = "New laptop",
            targetAmount = BigDecimal("1200.50"),
            currency = Currency.getInstance("EUR"),
            accountId = 7L,
            targetDate = LocalDate.of(2027, 6, 1),
            color = 0x111111,
            icon = "laptop",
            sortOrder = 2,
        )

        assertEquals(domain, domain.toEntity().toDomain())
    }
}
