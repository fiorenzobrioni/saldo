package com.callbackdev.saldo.feature.onboarding

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Currency

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class OnboardingViewModelTest {

    private val usd: Currency = Currency.getInstance("USD")
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-07-12T10:00:00Z"), ZoneId.of("Europe/Rome"))

    private val contentResolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { getString(any()) } returns "Conto corrente"
        every { contentResolver } returns this@OnboardingViewModelTest.contentResolver
    }
    private val accountRepository = mockk<AccountRepository>()
    private val userPreferences = mockk<UserPreferencesRepository>(relaxUnitFun = true)
    private val importBackup = mockk<ImportBackupUseCase>()
    private val generateRecurringMovements = mockk<GenerateRecurringMovementsUseCase> {
        coEvery { this@mockk.invoke(any()) } returns emptyList()
    }

    private fun viewModel(): OnboardingViewModel = OnboardingViewModel(
        context = context,
        accountRepository = accountRepository,
        userPreferences = userPreferences,
        importBackup = importBackup,
        generateRecurringMovements = generateRecurringMovements,
        clock = clock,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private val summary = BackupSummary(
        exportedAt = Instant.parse("2026-07-01T08:00:00Z"),
        appVersion = "0.8.7",
        accounts = 2,
        categories = 10,
        transactions = 150,
        recurringRules = 3,
        tags = 4,
    )

    @Test
    fun `account name is prefilled and pages advance in order`() = runTest {
        val viewModel = viewModel()

        assertEquals("Conto corrente", viewModel.uiState.value.accountName)
        assertEquals(OnboardingPage.WELCOME, viewModel.uiState.value.page)

        viewModel.next()
        assertEquals(OnboardingPage.PRIVACY, viewModel.uiState.value.page)
        assertTrue(viewModel.back())
        assertEquals(OnboardingPage.WELCOME, viewModel.uiState.value.page)
        // On the first page back is not consumed: the system handles it.
        assertFalse(viewModel.back())
    }

    @Test
    fun `confirming the currency persists the override and advances`() = runTest {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.next()
        assertEquals(OnboardingPage.CURRENCY, viewModel.uiState.value.page)

        viewModel.onCurrencySelected(usd)
        viewModel.confirmCurrency()

        coVerify(exactly = 1) { userPreferences.setPrimaryCurrencyOverride(usd) }
        assertEquals(OnboardingPage.ACCOUNT, viewModel.uiState.value.page)
    }

    @Test
    fun `creating the account saves the chosen currency and parsed balance`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()
        viewModel.onCurrencySelected(usd)
        viewModel.onAccountNameChanged("  My bank  ")
        viewModel.onBalanceChanged("1234,56")

        viewModel.createAccount()

        assertEquals("My bank", saved.captured.name)
        assertEquals(usd, saved.captured.currency)
        assertEquals(BigDecimal("1234.56"), saved.captured.initialBalance)
        assertEquals(AccountType.CHECKING, saved.captured.type)
        assertTrue(saved.captured.isIncludedInTotal)
        assertEquals(OnboardingPage.NOTIFICATIONS, viewModel.uiState.value.page)
    }

    @Test
    fun `an empty balance saves as zero`() = runTest {
        val saved = slot<Account>()
        coEvery { accountRepository.upsert(capture(saved)) } returns 1L
        val viewModel = viewModel()

        viewModel.createAccount()

        assertEquals(BigDecimal.ZERO, saved.captured.initialBalance)
    }

    @Test
    fun `a blank name blocks creation`() = runTest {
        val viewModel = viewModel()
        viewModel.onAccountNameChanged("   ")

        viewModel.createAccount()

        coVerify(exactly = 0) { accountRepository.upsert(any()) }
    }

    @Test
    fun `skipping the account creates nothing and advances`() = runTest {
        val viewModel = viewModel()
        viewModel.next()
        viewModel.next()
        viewModel.next()
        assertEquals(OnboardingPage.ACCOUNT, viewModel.uiState.value.page)

        viewModel.skipAccount()

        coVerify(exactly = 0) { accountRepository.upsert(any()) }
        assertEquals(OnboardingPage.NOTIFICATIONS, viewModel.uiState.value.page)
    }

    @Test
    fun `a valid backup file waits for confirmation, then restores and skips account creation`() = runTest {
        val file = mockk<BackupFile>()
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream("backup".toByteArray())
        every { importBackup.inspect(any()) } returns
            ImportBackupUseCase.Inspection.Valid(file, summary)
        coEvery { importBackup.restore(file) } returns Unit
        val viewModel = viewModel()

        viewModel.onRestoreFilePicked(mockk<Uri>())
        assertNotNull(viewModel.uiState.value.pendingRestore)

        viewModel.onRestoreConfirmed()

        coVerify(exactly = 1) { importBackup.restore(file) }
        // Restored rules catch up right away, mirroring the Backup screen.
        coVerify(exactly = 1) { generateRecurringMovements(any()) }
        assertNull(viewModel.uiState.value.pendingRestore)
        assertEquals(OnboardingPage.NOTIFICATIONS, viewModel.uiState.value.page)
    }

    @Test
    fun `dismissing the restore keeps the flow where it was`() = runTest {
        val file = mockk<BackupFile>()
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream("backup".toByteArray())
        every { importBackup.inspect(any()) } returns
            ImportBackupUseCase.Inspection.Valid(file, summary)
        val viewModel = viewModel()

        viewModel.onRestoreFilePicked(mockk<Uri>())
        viewModel.onRestoreDismissed()

        assertNull(viewModel.uiState.value.pendingRestore)
        // Confirming after a dismissal must not restore a stale file.
        viewModel.onRestoreConfirmed()
        coVerify(exactly = 0) { importBackup.restore(any()) }
    }

    @Test
    fun `an invalid file emits its error and never blocks the flow`() = runTest {
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream("not json".toByteArray())
        every { importBackup.inspect(any()) } returns
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.NOT_A_BACKUP)
        val viewModel = viewModel()

        viewModel.onRestoreFilePicked(mockk<Uri>())

        assertNull(viewModel.uiState.value.pendingRestore)
        assertFalse(viewModel.uiState.value.isWorking)
    }
}
