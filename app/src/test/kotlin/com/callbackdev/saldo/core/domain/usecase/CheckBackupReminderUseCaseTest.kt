package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.BackupReminderPreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

class CheckBackupReminderUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), zone)
    private val today: LocalDate = LocalDate.of(2026, 7, 20)

    private val userPreferences = mockk<UserPreferencesRepository>()
    private val accountRepository = mockk<AccountRepository>()

    /** In-memory stand-in for the watermark, so a run sees what the previous one wrote. */
    private val notifiedOn = MutableStateFlow<LocalDate?>(null)

    private val account = Account(
        id = 1L,
        name = "Main",
        type = AccountType.CHECKING,
        currency = Currency.getInstance("EUR"),
        initialBalance = BigDecimal.ZERO,
    )

    private fun useCase(
        enabled: Boolean = true,
        intervalDays: Int = 14,
        lastBackup: LocalDate? = null,
        alreadyNotifiedOn: LocalDate? = null,
        hasAccounts: Boolean = true,
    ): CheckBackupReminderUseCase {
        notifiedOn.value = alreadyNotifiedOn
        every { userPreferences.backupReminderPreferences } returns
            flowOf(BackupReminderPreferences(enabled = enabled, intervalDays = intervalDays))
        every { userPreferences.lastBackupAtEpochMilli } returns
            flowOf(lastBackup?.atStartOfDay(zone)?.plusHours(10)?.toInstant()?.toEpochMilli())
        every { userPreferences.backupReminderNotifiedOn } returns notifiedOn
        coEvery { userPreferences.setBackupReminderNotifiedOn(any()) } coAnswers { notifiedOn.value = firstArg() }
        every { accountRepository.observeAccounts() } returns flowOf(if (hasAccounts) listOf(account) else emptyList())
        return CheckBackupReminderUseCase(userPreferences, accountRepository, clock)
    }

    @Test
    fun `off by default means nothing is ever posted`() = runTest {
        assertNull(useCase(enabled = false)())
        assertNull(notifiedOn.value)
    }

    @Test
    fun `an install without accounts has nothing to protect`() = runTest {
        assertNull(useCase(hasAccounts = false)())
        assertNull(notifiedOn.value)
    }

    @Test
    fun `no backup ever made reminds right away and watermarks today`() = runTest {
        val reminder = useCase(lastBackup = null)()

        assertNotNull(reminder)
        assertNull(reminder!!.lastBackupDate)
        assertNull(reminder.daysSince)
        assertEquals(today, notifiedOn.value)
    }

    @Test
    fun `no backup ever made repeats once per interval, not once per day`() = runTest {
        assertNull(useCase(lastBackup = null, alreadyNotifiedOn = today.minusDays(1))())
        assertNull(useCase(lastBackup = null, alreadyNotifiedOn = today.minusDays(13))())
        assertNotNull(useCase(lastBackup = null, alreadyNotifiedOn = today.minusDays(14))())
    }

    @Test
    fun `a backup younger than the interval is quiet`() = runTest {
        assertNull(useCase(lastBackup = today.minusDays(13))())
        assertNull(notifiedOn.value)
    }

    @Test
    fun `a backup exactly one interval old is announced with its age`() = runTest {
        val reminder = useCase(lastBackup = today.minusDays(14))()

        assertNotNull(reminder)
        assertEquals(today.minusDays(14), reminder!!.lastBackupDate)
        assertEquals(14, reminder.daysSince)
        assertEquals(today, notifiedOn.value)
    }

    @Test
    fun `the same deadline is not announced twice`() = runTest {
        // Backup on 6 Jul, deadline 20 Jul, already posted on the 20th: the 21st stays quiet.
        val useCase = useCase(lastBackup = LocalDate.of(2026, 7, 6), alreadyNotifiedOn = today)

        assertNull(useCase(today.plusDays(1)))
        assertEquals(today, notifiedOn.value)
    }

    @Test
    fun `the next deadline is announced when its own day comes`() = runTest {
        // Backup on 6 Jul, interval 14: deadlines on 20 Jul and 3 Aug.
        val useCase = useCase(lastBackup = LocalDate.of(2026, 7, 6), alreadyNotifiedOn = today)

        assertNull(useCase(LocalDate.of(2026, 8, 2)))
        val reminder = useCase(LocalDate.of(2026, 8, 3))
        assertNotNull(reminder)
        assertEquals(28, reminder!!.daysSince)
    }

    @Test
    fun `a new backup re-arms the reminder for a later deadline`() = runTest {
        // Reminded on 20 Jul, then backed up on 22 Jul: quiet until 5 Aug, loud on 5 Aug.
        val useCase = useCase(lastBackup = LocalDate.of(2026, 7, 22), alreadyNotifiedOn = today)

        assertNull(useCase(LocalDate.of(2026, 8, 4)))
        assertNotNull(useCase(LocalDate.of(2026, 8, 5)))
    }

    @Test
    fun `the interval choice is honored`() = runTest {
        assertNull(useCase(intervalDays = 30, lastBackup = today.minusDays(29))())
        assertNotNull(useCase(intervalDays = 7, lastBackup = today.minusDays(7))())
    }
}
