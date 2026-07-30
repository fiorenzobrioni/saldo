package com.callbackdev.saldo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.core.database.repository.RoomAccountRepository
import com.callbackdev.saldo.core.database.repository.RoomTransactionRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.usecase.AdjustBalanceUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

/**
 * End-to-end balance adjustment on a real (in-memory) database: the use case
 * must align the computed balance with the typed one and be a no-op when the
 * balance already matches.
 */
@RunWith(AndroidJUnit4::class)
class BalanceAdjustmentTest {

    private val eur = Currency.getInstance("EUR")

    private lateinit var database: SaldoDatabase
    private lateinit var accountRepository: RoomAccountRepository
    private lateinit var transactionRepository: RoomTransactionRepository
    private lateinit var adjustBalance: AdjustBalanceUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SaldoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountRepository = RoomAccountRepository(database.accountDao())
        transactionRepository =
            RoomTransactionRepository(database.transactionDao(), database.foreignFlowDao())
        adjustBalance = AdjustBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            clock = Clock.fixed(Instant.parse("2026-07-08T10:15:00Z"), ZoneId.of("Europe/Rome")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun adjustmentAlignsTheBalanceAndRepeatingItIsANoOp() = runBlocking {
        val accountId = accountRepository.upsert(
            Account(
                name = "Checking",
                type = AccountType.CHECKING,
                currency = eur,
                initialBalance = BigDecimal("100.00"),
            ),
        )
        transactionRepository.upsert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("-25.50"),
                currency = eur,
                accountId = accountId,
                timestamp = Instant.parse("2026-07-01T09:00:00Z"),
                zoneOffset = ZoneOffset.ofHours(2),
            ),
        )
        assertEquals(
            BigDecimal("74.50"),
            accountRepository.observeAccountBalance(accountId).first(),
        )

        val result = adjustBalance(accountId, BigDecimal("80.00"))

        assertEquals(AdjustBalanceUseCase.Result.Adjusted(BigDecimal("5.50")), result)
        assertEquals(
            BigDecimal("80.00"),
            accountRepository.observeAccountBalance(accountId).first(),
        )
        assertEquals(2, transactionRepository.countForAccount(accountId))

        val repeated = adjustBalance(accountId, BigDecimal("80.00"))

        assertEquals(AdjustBalanceUseCase.Result.NoChange, repeated)
        assertEquals(2, transactionRepository.countForAccount(accountId))
    }
}
