package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.dao.AccountDao
import com.callbackdev.saldo.core.database.dao.TransactionDao
import com.callbackdev.saldo.core.database.entity.AccountEntity
import com.callbackdev.saldo.core.database.entity.TransactionEntity
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.DashboardWindows
import com.callbackdev.saldo.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Instrumented tests for the future-movement queries of Phase 13 (ADR 36):
 * `observeAfter`, the reminder scan and its watermark.
 *
 * They also pin down the promise the phase makes explicit: a movement dated in
 * the future belongs to the ledger and to the headline balance, but stays out
 * of every figure scoped to today - the statistics, the budget spend and the
 * dashboard's today/month cards - until its day arrives. The tests resolve the
 * windows through `DashboardWindows`, the same object production uses, so a
 * window that drifted back to the calendar month end (as it once did) would
 * fail here instead of quietly counting tomorrow's bill today.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoUpcomingTest {

    private lateinit var database: SaldoDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao

    private var accountId = 0L

    /** "Today" for these tests; the ledger is built around it. */
    private val today: LocalDate = LocalDate.of(2026, 7, 9)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
        accountId = runBlocking {
            accountDao.insert(
                AccountEntity(
                    name = "acc",
                    type = AccountType.CHECKING,
                    currency = "EUR",
                    initialBalanceMinor = 100_00L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun instantOf(date: LocalDate): Instant =
        LocalDateTime.of(date, java.time.LocalTime.NOON).toInstant(ZoneOffset.UTC)

    private fun movement(
        date: LocalDate,
        amountMinor: Long = -10_00L,
        type: TransactionType = TransactionType.EXPENSE,
        isPending: Boolean = false,
        hasReminder: Boolean = false,
        lastReminderEpochDay: Long? = null,
    ) = TransactionEntity(
        type = type,
        amountMinor = amountMinor,
        currency = "EUR",
        accountId = accountId,
        timestampEpochMilli = instantOf(date).toEpochMilli(),
        zoneOffsetSeconds = 0,
        isPending = isPending,
        hasReminder = hasReminder,
        lastReminderEpochDay = lastReminderEpochDay,
    )

    @Test
    fun observeAfterReturnsOnlyConfirmedMovementsFromTheCutoffOn() = runBlocking {
        transactionDao.insert(movement(today.minusDays(1)))
        transactionDao.insert(movement(today))
        val tomorrow = transactionDao.insert(movement(today.plusDays(1)))
        val later = transactionDao.insert(movement(today.plusDays(20)))
        // Pending movements are the other half of "upcoming", but they travel
        // through their own query: this one is about the confirmed ledger.
        transactionDao.insert(movement(today.plusDays(2), isPending = true))

        val rows = transactionDao.observeAfter(today.plusDays(1).toEpochDay()).first()

        assertEquals(listOf(tomorrow, later), rows.map { it.id })
    }

    @Test
    fun observeAfterOrdersBySoonestFirst() = runBlocking {
        val later = transactionDao.insert(movement(today.plusDays(10)))
        val sooner = transactionDao.insert(movement(today.plusDays(2)))

        val rows = transactionDao.observeAfter(today.plusDays(1).toEpochDay()).first()

        assertEquals(listOf(sooner, later), rows.map { it.id })
    }

    @Test
    fun observeAfterUsesTheMovementsOwnOffset() = runBlocking {
        // 23:30 UTC on the 9th is already the 10th at +02:00: the local day is
        // the movement's own (ADR 7), not the server's.
        val id = transactionDao.insert(
            movement(today).copy(
                timestampEpochMilli = LocalDateTime.of(today, java.time.LocalTime.of(23, 30))
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli(),
                zoneOffsetSeconds = 7_200,
            ),
        )

        val rows = transactionDao.observeAfter(today.plusDays(1).toEpochDay()).first()

        assertEquals(listOf(id), rows.map { it.id })
    }

    @Test
    fun aFutureMovementCountsInTheBalanceButNotInTheBalanceAsOfToday() = runBlocking {
        transactionDao.insert(movement(today.plusDays(5), amountMinor = -30_00L))

        val total = accountDao.observeTotalBalance("EUR").first()
        val asOfToday = accountDao.observeAllBalancesAsOf(today.plusDays(1).toEpochDay())
            .first()
            .single()
            .balanceMinor

        assertEquals(70_00L, total)
        assertEquals(100_00L, asOfToday)
    }

    @Test
    fun aFutureMovementStaysOutOfTheStatisticsAndTheBudgetSpendOfThisMonth() = runBlocking {
        val spent = transactionDao.insert(movement(today.minusDays(1), amountMinor = -25_00L))
        transactionDao.insert(movement(today.plusDays(5), amountMinor = -30_00L))
        // The very windows production resolves, not a window of the test's own:
        // the budget spend, the statistics and the month card all stop at
        // `todayEnd`, which is exactly what leaves the future out.
        val windows = DashboardWindows.around(today, ZoneOffset.UTC)
        val monthStart = windows.monthStart.toEpochMilli()
        val cutoff = windows.todayEnd.toEpochMilli()

        val statsTotal = transactionDao.observeStatsSpendTotal(monthStart, cutoff, "EUR").first()
        val monthly = transactionDao.observeMonthlyTotals(monthStart, cutoff, "EUR").first()

        assertEquals(-25_00L, statsTotal)
        assertEquals(-25_00L, monthly.single().expenseMinor)
        assertTrue(spent > 0)
    }

    @Test
    fun aFutureMovementStaysOutOfTheDashboardMonthCardUntilItsDay() = runBlocking {
        transactionDao.insert(movement(today.minusDays(1), amountMinor = -25_00L))
        transactionDao.insert(movement(today.plusDays(5), amountMinor = -30_00L))
        transactionDao.insert(movement(today.plusDays(5), amountMinor = 500_00L, type = TransactionType.INCOME))
        val windows = DashboardWindows.around(today, ZoneOffset.UTC)

        val totals = transactionDao.observeDashboardTotals(
            todayStart = windows.todayStart.toEpochMilli(),
            todayEnd = windows.todayEnd.toEpochMilli(),
            monthStart = windows.monthStart.toEpochMilli(),
            previousStart = windows.previousStart.toEpochMilli(),
            previousToDateEnd = windows.previousToDateEnd.toEpochMilli(),
            currency = "EUR",
        ).first()

        // The month figures are month-to-date: the two future rows wait for their day.
        assertEquals(-25_00L, totals.monthSpendMinor)
        assertEquals(0L, totals.monthIncomeMinor)
        assertEquals(-25_00L, totals.monthToDateSpendMinor)
    }

    @Test
    fun theReminderScanReturnsOnlyFlaggedMovementsInsideTheWindow() = runBlocking {
        val due = transactionDao.insert(movement(today.plusDays(2), hasReminder = true))
        // Flagged but beyond the lead time.
        transactionDao.insert(movement(today.plusDays(9), hasReminder = true))
        // Inside the window but never asked for.
        transactionDao.insert(movement(today.plusDays(2)))
        // Flagged, inside the window, but not confirmed.
        transactionDao.insert(movement(today.plusDays(2), hasReminder = true, isPending = true))

        val rows = transactionDao.getDueReminders(today.toEpochDay(), today.plusDays(3).toEpochDay())

        assertEquals(listOf(due), rows.map { it.id })
    }

    @Test
    fun aMovementAlreadyRemindedAboutIsNotReturnedAgain() = runBlocking {
        val date = today.plusDays(2)
        transactionDao.insert(
            movement(date, hasReminder = true, lastReminderEpochDay = date.toEpochDay()),
        )

        val rows = transactionDao.getDueReminders(today.toEpochDay(), today.plusDays(3).toEpochDay())

        assertTrue(rows.isEmpty())
    }

    @Test
    fun aWatermarkOlderThanTheMovementsDateRearmsTheReminder() = runBlocking {
        // The user moved the date further out: the new date has not been
        // announced, so the reminder comes due again.
        val id = transactionDao.insert(
            movement(
                today.plusDays(2),
                hasReminder = true,
                lastReminderEpochDay = today.minusDays(20).toEpochDay(),
            ),
        )

        val rows = transactionDao.getDueReminders(today.toEpochDay(), today.plusDays(3).toEpochDay())

        assertEquals(listOf(id), rows.map { it.id })
    }

    @Test
    fun updatingTheWatermarkSilencesTheMovementWithoutTouchingTheRest() = runBlocking {
        val date = today.plusDays(2)
        val id = transactionDao.insert(movement(date, amountMinor = -42_00L, hasReminder = true))

        transactionDao.updateReminderWatermark(id, date.toEpochDay())

        val row = transactionDao.getById(id)!!
        assertEquals(date.toEpochDay(), row.lastReminderEpochDay)
        assertEquals(-42_00L, row.amountMinor)
        assertTrue(
            transactionDao.getDueReminders(today.toEpochDay(), today.plusDays(3).toEpochDay())
                .isEmpty(),
        )
    }
}
