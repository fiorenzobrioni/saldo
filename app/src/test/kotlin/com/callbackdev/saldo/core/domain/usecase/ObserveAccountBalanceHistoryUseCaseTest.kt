package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.DailyNet
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class ObserveAccountBalanceHistoryUseCaseTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val repository = mockk<AccountRepository>()
    private val useCase = ObserveAccountBalanceHistoryUseCase(repository)

    private val account = Account(
        id = 7L,
        name = "Checking",
        type = AccountType.CHECKING,
        currency = eur,
        initialBalance = BigDecimal("100.00"),
    )

    private val days = (1..4).map { LocalDate.of(2026, 7, it) }

    @Test
    fun `walks from initial balance plus history, carrying flat days forward`() = runTest {
        every { repository.observeNetChangeBefore(7L, eur, days.first()) } returns
            flowOf(BigDecimal("-30.00"))
        every {
            repository.observeDailyNetChanges(7L, eur, days.first(), days.last().plusDays(1))
        } returns flowOf(
            listOf(
                DailyNet(LocalDate.of(2026, 7, 2), BigDecimal("-10.00")),
                DailyNet(LocalDate.of(2026, 7, 4), BigDecimal("50.00")),
            ),
        )

        val series = useCase(account, days).first()

        // 100 - 30 = 70 before the window; flat on the 1st, -10 on the 2nd,
        // flat on the 3rd, +50 on the 4th.
        assertEquals(
            listOf("70.00", "60.00", "60.00", "110.00"),
            series.map { it.balance.toPlainString() },
        )
        assertEquals(days, series.map { it.date })
    }

    @Test
    fun `an empty window yields an empty series without touching the repository`() = runTest {
        assertTrue(useCase(account, emptyList()).first().isEmpty())
    }
}
