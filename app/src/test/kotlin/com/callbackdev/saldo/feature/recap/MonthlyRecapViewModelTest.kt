package com.callbackdev.saldo.feature.recap

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.turbine.test
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.MonthlyRecap
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.usecase.GetMonthlyRecapUseCase
import com.callbackdev.saldo.navigation.MonthlyRecapRoute
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class MonthlyRecapViewModelTest {

    private val eur: Currency = Currency.getInstance("EUR")
    private val accountRepository = mockk<AccountRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>()
    private val getMonthlyRecap = mockk<GetMonthlyRecapUseCase>()
    private val recapImageSharer = mockk<RecapImageSharer>()

    private fun recap(month: YearMonth, movementCount: Int) = MonthlyRecap(
        month = month,
        currency = eur,
        expenseTotal = BigDecimal("100.00"),
        incomeTotal = BigDecimal("200.00"),
        previousExpenseTotal = null,
        topCategories = emptyList(),
        biggestExpense = null,
        busiestDay = null,
        recurringSpend = BigDecimal.ZERO,
        movementCount = movementCount,
        savingsRatePercent = null,
    )

    private fun viewModel(month: YearMonth, movementCount: Int): MonthlyRecapViewModel {
        val account = Account(
            id = 1L,
            name = "acc",
            type = AccountType.CHECKING,
            currency = eur,
            initialBalance = BigDecimal.ZERO,
        )
        every { accountRepository.observeAccountsWithBalance() } returns
            flowOf(listOf(AccountWithBalance(account, BigDecimal.ZERO)))
        every { userPreferences.primaryCurrencyOverride } returns flowOf(null)
        every { categoryRepository.observeCategories() } returns flowOf(emptyList())
        coEvery { getMonthlyRecap(month, eur) } returns recap(month, movementCount)
        return MonthlyRecapViewModel(
            route = MonthlyRecapRoute(month.year, month.monthValue),
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            userPreferences = userPreferences,
            getMonthlyRecap = getMonthlyRecap,
            recapImageSharer = recapImageSharer,
        )
    }

    @Test
    fun `loads the recap of the routed month`() = runTest {
        val month = YearMonth.of(2026, 6)
        val viewModel = viewModel(month, movementCount = 12)

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(month, state.month)
        assertEquals(eur, state.currency)
        assertEquals(12, state.recap?.movementCount)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `a month without movements is empty`() = runTest {
        val viewModel = viewModel(YearMonth.of(2026, 6), movementCount = 0)

        val state = viewModel.uiState.first { !it.isLoading }

        assertTrue(state.isEmpty)
    }

    @Test
    fun `sharing emits the uri event`() = runTest {
        val month = YearMonth.of(2026, 6)
        val viewModel = viewModel(month, movementCount = 3)
        viewModel.uiState.first { !it.isLoading }
        val uri = mockk<Uri>()
        coEvery { recapImageSharer.share(any(), month) } returns uri

        viewModel.events.test {
            viewModel.onShareRequested(mockk<ImageBitmap>())
            assertEquals(MonthlyRecapEvent.ShareReady(uri), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed share emits the failure event`() = runTest {
        val month = YearMonth.of(2026, 6)
        val viewModel = viewModel(month, movementCount = 3)
        viewModel.uiState.first { !it.isLoading }
        coEvery { recapImageSharer.share(any(), month) } throws IllegalStateException("io")

        viewModel.events.test {
            viewModel.onShareRequested(mockk<ImageBitmap>())
            assertEquals(MonthlyRecapEvent.ShareFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
