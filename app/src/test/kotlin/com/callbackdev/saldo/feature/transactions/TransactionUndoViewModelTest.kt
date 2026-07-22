package com.callbackdev.saldo.feature.transactions

import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency

@ExtendWith(MainDispatcherExtension::class)
class TransactionUndoViewModelTest {

    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val tagRepository = mockk<TagRepository>(relaxUnitFun = true)
    private val coordinator = TransactionUndoCoordinator()

    private val deleted = Transaction(
        id = 7L,
        type = TransactionType.EXPENSE,
        amount = BigDecimal("-12.00"),
        currency = Currency.getInstance("EUR"),
        accountId = 1L,
        timestamp = Instant.parse("2026-07-01T10:00:00Z"),
        zoneOffset = ZoneOffset.ofHours(2),
        categoryId = 10L,
    )

    private fun viewModel() = TransactionUndoViewModel(
        coordinator = coordinator,
        transactionRepository = transactionRepository,
        tagRepository = tagRepository,
    )

    @Test
    fun `undo re-inserts the movement with a fresh id and re-attaches its tags`() = runTest {
        val saved = slot<Transaction>()
        coEvery { transactionRepository.upsert(capture(saved)) } returns 42L
        val viewModel = viewModel()

        viewModel.undo(TransactionUndoCoordinator.DeletedTransaction(deleted, listOf(5L)))

        assertEquals(0L, saved.captured.id)
        assertEquals(deleted.amount, saved.captured.amount)
        coVerify { tagRepository.setTagsForTransaction(42L, listOf(5L)) }
    }

    @Test
    fun `undo without tags skips the tag write`() = runTest {
        coEvery { transactionRepository.upsert(any()) } returns 42L
        val viewModel = viewModel()

        viewModel.undo(TransactionUndoCoordinator.DeletedTransaction(deleted, emptyList()))

        coVerify(exactly = 0) { tagRepository.setTagsForTransaction(any(), any()) }
    }

    @Test
    fun `a failed undo surfaces the failure event`() = runTest {
        coEvery { transactionRepository.upsert(any()) } throws IllegalStateException("boom")
        val viewModel = viewModel()

        viewModel.undo(TransactionUndoCoordinator.DeletedTransaction(deleted, emptyList()))

        viewModel.undoFailed.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
