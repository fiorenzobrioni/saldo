package com.callbackdev.saldo.feature.transactions.filter

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

class TransactionFilterEngineTest {

    private val eur = Currency.getInstance("EUR")
    private val today = LocalDate.of(2026, 7, 10)

    private fun transaction(
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "-10.00",
        timestamp: Instant = Instant.parse("2026-07-08T08:00:00Z"),
        offsetHours: Int = 2,
        categoryId: Long? = 10L,
        accountId: Long = 1L,
        transferAccountId: Long? = null,
        description: String? = null,
        note: String? = null,
    ) = Transaction(
        type = type,
        amount = BigDecimal(amount),
        currency = eur,
        accountId = accountId,
        timestamp = timestamp,
        zoneOffset = ZoneOffset.ofHours(offsetHours),
        categoryId = categoryId,
        transferAccountId = transferAccountId,
        description = description,
        note = note,
    )

    private fun matches(
        transaction: Transaction,
        filters: TransactionFilters,
        tagIds: Set<Long> = emptySet(),
    ): Boolean = TransactionFilterEngine.matches(
        transaction = transaction,
        localDate = transaction.timestamp.atOffset(transaction.zoneOffset).toLocalDate(),
        tagIds = tagIds,
        filters = filters,
        today = today,
    )

    @Test
    fun `no filters matches everything`() {
        assertTrue(matches(transaction(), TransactionFilters.NONE))
    }

    @Test
    fun `search is case and accent insensitive`() {
        val movement = transaction(description = "Caffè PERCHÉ sì")
        assertTrue(matches(movement, TransactionFilters(query = "caffe")))
        assertTrue(matches(movement, TransactionFilters(query = "perche")))
        assertTrue(matches(movement, TransactionFilters(query = "CAFFÈ")))
        assertFalse(matches(movement, TransactionFilters(query = "espresso")))
    }

    @Test
    fun `search also covers the note`() {
        val movement = transaction(description = "Spesa", note = "regalo di compleanno")
        assertTrue(matches(movement, TransactionFilters(query = "compleanno")))
    }

    @Test
    fun `search does not match a movement without text`() {
        assertFalse(matches(transaction(), TransactionFilters(query = "x")))
    }

    @Test
    fun `date presets bound the movement's own local day`() {
        // 22:30Z on July 31st is already August 1st at UTC+2 (ADR 7).
        val endOfJuly = transaction(
            timestamp = Instant.parse("2026-07-31T22:30:00Z"),
            offsetHours = 2,
        )
        val thisMonth = TransactionFilters(datePreset = DatePreset.THIS_MONTH)
        assertFalse(matches(endOfJuly, thisMonth))

        val inJuly = transaction(
            timestamp = Instant.parse("2026-07-31T22:30:00Z"),
            offsetHours = -1,
        )
        assertTrue(matches(inJuly, thisMonth))
    }

    @Test
    fun `last month covers the whole previous calendar month`() {
        val filters = TransactionFilters(datePreset = DatePreset.LAST_MONTH)
        val range = TransactionFilterEngine.dateRange(filters, today)!!
        assertEquals(LocalDate.of(2026, 6, 1), range.start)
        assertEquals(LocalDate.of(2026, 6, 30), range.endInclusive)
    }

    @Test
    fun `last 90 days includes today and spans 90 days`() {
        val filters = TransactionFilters(datePreset = DatePreset.LAST_90_DAYS)
        val range = TransactionFilterEngine.dateRange(filters, today)!!
        assertEquals(today, range.endInclusive)
        assertEquals(today.minusDays(89), range.start)
    }

    @Test
    fun `custom range is inclusive on both ends and open when a bound is missing`() {
        val bounded = TransactionFilters(
            datePreset = DatePreset.CUSTOM,
            customStart = LocalDate.of(2026, 7, 1),
            customEnd = LocalDate.of(2026, 7, 8),
        )
        assertTrue(matches(transaction(timestamp = Instant.parse("2026-07-08T08:00:00Z")), bounded))
        assertFalse(matches(transaction(timestamp = Instant.parse("2026-07-09T08:00:00Z")), bounded))

        val openStart = bounded.copy(customStart = null)
        assertTrue(matches(transaction(timestamp = Instant.parse("2026-06-01T08:00:00Z")), openStart))

        val unbounded = bounded.copy(customStart = null, customEnd = null)
        assertNull(TransactionFilterEngine.dateRange(unbounded, today))
    }

    @Test
    fun `type filter restricts to the selected types`() {
        val filters = TransactionFilters(types = setOf(TransactionType.INCOME))
        assertFalse(matches(transaction(), filters))
        assertTrue(matches(transaction(type = TransactionType.INCOME, amount = "10.00"), filters))
    }

    @Test
    fun `category filter excludes uncategorized movements`() {
        val filters = TransactionFilters(categoryIds = setOf(10L))
        assertTrue(matches(transaction(categoryId = 10L), filters))
        assertFalse(matches(transaction(categoryId = 11L), filters))
        assertFalse(matches(transaction(categoryId = null), filters))
    }

    @Test
    fun `account filter matches either leg of a transfer`() {
        val transfer = transaction(
            type = TransactionType.TRANSFER,
            accountId = 1L,
            transferAccountId = 2L,
            categoryId = null,
        )
        assertTrue(matches(transfer, TransactionFilters(accountIds = setOf(1L))))
        assertTrue(matches(transfer, TransactionFilters(accountIds = setOf(2L))))
        assertFalse(matches(transfer, TransactionFilters(accountIds = setOf(3L))))
    }

    @Test
    fun `tag filter matches any selected tag`() {
        val filters = TransactionFilters(tagIds = setOf(5L, 6L))
        assertTrue(matches(transaction(), filters, tagIds = setOf(6L, 9L)))
        assertFalse(matches(transaction(), filters, tagIds = setOf(9L)))
        assertFalse(matches(transaction(), filters, tagIds = emptySet()))
    }

    @Test
    fun `amount bounds compare magnitudes, so they hit expenses and incomes alike`() {
        val filters = TransactionFilters(
            amountMin = BigDecimal("10.00"),
            amountMax = BigDecimal("50.00"),
        )
        assertTrue(matches(transaction(amount = "-10.00"), filters))
        assertTrue(matches(transaction(type = TransactionType.INCOME, amount = "50.00"), filters))
        assertFalse(matches(transaction(amount = "-9.99"), filters))
        assertFalse(matches(transaction(amount = "-50.01"), filters))
    }

    @Test
    fun `filters combine with AND semantics`() {
        val filters = TransactionFilters(
            query = "caffe",
            types = setOf(TransactionType.EXPENSE),
            categoryIds = setOf(10L),
            amountMin = BigDecimal("5.00"),
        )
        val matching = transaction(description = "Caffè al bar", amount = "-6.00")
        assertTrue(matches(matching, filters))
        // Fails one leg (amount below the minimum) and the whole match fails.
        assertFalse(matches(transaction(description = "Caffè al bar", amount = "-4.00"), filters))
    }

    @Test
    fun `activeCount counts filter groups, not single selections`() {
        val filters = TransactionFilters(
            types = setOf(TransactionType.EXPENSE, TransactionType.INCOME),
            categoryIds = setOf(1L, 2L, 3L),
            amountMin = BigDecimal.ONE,
        )
        assertEquals(3, filters.activeCount)
        assertTrue(filters.isActive)
        assertFalse(TransactionFilters.NONE.isActive)
        // The query alone makes the view "active" but is not a filter group.
        assertEquals(0, TransactionFilters(query = "x").activeCount)
        assertTrue(TransactionFilters(query = "x").isActive)
    }
}
