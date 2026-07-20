package com.callbackdev.saldo.feature.transactions.filter

import com.callbackdev.saldo.core.common.prefs.FirstDayOfWeek
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
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
        firstDayOfWeek = DayOfWeek.MONDAY,
    )

    private fun weekRange(anchor: LocalDate, firstDay: DayOfWeek): ClosedRange<LocalDate> =
        TransactionFilterEngine.dateRange(
            TransactionFilters(datePreset = DatePreset.THIS_WEEK),
            anchor,
            firstDay,
        )!!

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
        val range = TransactionFilterEngine.dateRange(filters, today, DayOfWeek.MONDAY)!!
        assertEquals(LocalDate.of(2026, 6, 1), range.start)
        assertEquals(LocalDate.of(2026, 6, 30), range.endInclusive)
    }

    @Test
    fun `last 90 days includes today and spans 90 days`() {
        val filters = TransactionFilters(datePreset = DatePreset.LAST_90_DAYS)
        val range = TransactionFilterEngine.dateRange(filters, today, DayOfWeek.MONDAY)!!
        assertEquals(today, range.endInclusive)
        assertEquals(today.minusDays(89), range.start)
    }

    @Test
    fun `this week spans seven days anchored on the chosen first day`() {
        // 10 July 2026 is a Friday.
        val monday = weekRange(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 7, 6), monday.start)
        assertEquals(LocalDate.of(2026, 7, 12), monday.endInclusive)

        val sunday = weekRange(today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 7, 5), sunday.start)
        assertEquals(LocalDate.of(2026, 7, 11), sunday.endInclusive)

        val saturday = weekRange(today, DayOfWeek.SATURDAY)
        assertEquals(LocalDate.of(2026, 7, 4), saturday.start)
        assertEquals(LocalDate.of(2026, 7, 10), saturday.endInclusive)
    }

    @Test
    fun `this week starts today when today is the first day of the week`() {
        // 6 July 2026 is a Monday.
        val range = weekRange(LocalDate.of(2026, 7, 6), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 7, 6), range.start)
        assertEquals(LocalDate.of(2026, 7, 12), range.endInclusive)
    }

    @Test
    fun `this week crosses month and year boundaries`() {
        // 1 July 2026 is a Wednesday: the Monday week starts back in June.
        val acrossMonths = weekRange(LocalDate.of(2026, 7, 1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 29), acrossMonths.start)
        assertEquals(LocalDate.of(2026, 7, 5), acrossMonths.endInclusive)

        // 1 January 2026 is a Thursday: the Monday week starts back in 2025.
        val acrossYears = weekRange(LocalDate.of(2026, 1, 1), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2025, 12, 29), acrossYears.start)
        assertEquals(LocalDate.of(2026, 1, 4), acrossYears.endInclusive)
    }

    @Test
    fun `unsupported locale week starts snap to Monday`() {
        // Arabic (Egypt) weeks start on Saturday: supported, kept as is.
        assertEquals(DayOfWeek.SATURDAY, FirstDayOfWeek.coerce(DayOfWeek.SATURDAY))
        // Some locales report Friday, which Settings does not offer.
        assertEquals(DayOfWeek.MONDAY, FirstDayOfWeek.coerce(DayOfWeek.FRIDAY))
        assertEquals(DayOfWeek.MONDAY, FirstDayOfWeek.coerce(DayOfWeek.WEDNESDAY))
        assertEquals(DayOfWeek.SUNDAY, FirstDayOfWeek.coerce(DayOfWeek.SUNDAY))
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

        // "Until" only: open at the start, still bounded at the end.
        val openStart = bounded.copy(customStart = null)
        assertTrue(matches(transaction(timestamp = Instant.parse("2026-06-01T08:00:00Z")), openStart))
        assertFalse(matches(transaction(timestamp = Instant.parse("2026-07-09T08:00:00Z")), openStart))

        // "From" only: open at the end, still bounded at the start.
        val openEnd = bounded.copy(customEnd = null)
        assertTrue(matches(transaction(timestamp = Instant.parse("2026-12-31T08:00:00Z")), openEnd))
        assertFalse(matches(transaction(timestamp = Instant.parse("2026-06-30T08:00:00Z")), openEnd))

        val unbounded = bounded.copy(customStart = null, customEnd = null)
        assertNull(TransactionFilterEngine.dateRange(unbounded, today, DayOfWeek.MONDAY))
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
