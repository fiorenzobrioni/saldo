package com.callbackdev.saldo.feature.transactions.importer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class CsvReaderTest {

    @Test
    fun `splits simple rows on the given separator`() {
        val records = CsvReader.parse("a;b;c\r\nd;e;f\r\n", ';')
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e", "f")), records)
    }

    @Test
    fun `a quoted field may contain the separator and newlines`() {
        val records = CsvReader.parse("\"a;b\";\"line1\nline2\"", ';')
        assertEquals(listOf(listOf("a;b", "line1\nline2")), records)
    }

    @Test
    fun `doubled quotes inside a quoted field are unescaped`() {
        val records = CsvReader.parse("\"a\"\"b\";c", ';')
        assertEquals(listOf(listOf("a\"b", "c")), records)
    }

    @Test
    fun `a leading BOM is stripped and a trailing newline adds no empty record`() {
        val records = CsvReader.parse("\uFEFFa;b\n", ';')
        assertEquals(listOf(listOf("a", "b")), records)
    }

    @Test
    fun `lone carriage returns and line feeds both end a record`() {
        assertEquals(2, CsvReader.parse("a;b\rc;d", ';').size)
        assertEquals(2, CsvReader.parse("a;b\nc;d", ';').size)
    }
}

class CsvSeparatorSnifferTest {

    @Test
    fun `detects the most frequent candidate on the first content line`() {
        assertEquals(';', CsvSeparatorSniffer.detect("a;b;c\n1;2;3"))
        assertEquals(',', CsvSeparatorSniffer.detect("a,b,c"))
        assertEquals('\t', CsvSeparatorSniffer.detect("a\tb\tc"))
    }

    @Test
    fun `ignores separators inside quotes and defaults when none is found`() {
        assertEquals(';', CsvSeparatorSniffer.detect("\"a,b,c,d\";x"))
        assertEquals(';', CsvSeparatorSniffer.detect("single"))
    }
}

class CsvFieldParsersTest {

    private fun amount(raw: String) = CsvFieldParsers.parseAmount(raw)

    private fun assertAmount(expected: String, raw: String) {
        val parsed = amount(raw)
        assertTrue(parsed != null && parsed.compareTo(BigDecimal(expected)) == 0, "‹$raw› -> $parsed")
    }

    @Test
    fun `amount parses both decimal conventions and grouping`() {
        assertAmount("1234.56", "1.234,56")
        assertAmount("1234.56", "1,234.56")
        assertAmount("1234.56", "1234,56")
        assertAmount("1234.56", "1234.56")
        assertAmount("1234567", "1.234.567")
        assertAmount("1234567", "1,234,567")
        assertAmount("1234.56", "€ 1 234,56")
    }

    @Test
    fun `amount reads negatives from a sign or parentheses`() {
        assertAmount("-12.50", "-12.50")
        assertAmount("-50", "(50)")
    }

    @Test
    fun `with a known decimal mark the other separator is grouping wherever it appears`() {
        fun with(mark: Char, raw: String) = CsvFieldParsers.parseAmount(raw, mark)
        assertEquals(0, with('.', "1,234")!!.compareTo(BigDecimal("1234")))
        assertEquals(0, with(',', "2.500")!!.compareTo(BigDecimal("2500")))
        assertEquals(0, with(',', "1.234,56")!!.compareTo(BigDecimal("1234.56")))
        assertEquals(0, with('.', "1,234,567.89")!!.compareTo(BigDecimal("1234567.89")))
        assertEquals(0, with(',', "12,5")!!.compareTo(BigDecimal("12.5")))
        // A repeated decimal mark is not a number in that convention.
        assertNull(with('.', "1.234.567"))
    }

    @Test
    fun `the decimal mark is inferred from the cells that settle it`() {
        val infer = CsvFieldParsers::inferDecimalMark
        // A cell with both separators names the decimal one.
        assertEquals(',', infer(listOf("-1.234,56", "-2.500", "10")))
        assertEquals('.', infer(listOf("1,234.56", "2,500")))
        // One separator with other than three trailing digits names it too.
        assertEquals(',', infer(listOf("12,5", "1,234")))
        assertEquals('.', infer(listOf("3.1415", "2.500")))
        // A repeated separator is grouping, so the decimal mark is the other one.
        assertEquals(',', infer(listOf("1.234.567", "2.500")))
        assertEquals('.', infer(listOf("1,234,567")))
    }

    @Test
    fun `the decimal mark stays open on ambiguous or conflicting cells`() {
        val infer = CsvFieldParsers::inferDecimalMark
        // Only "x,xxx" shapes and integers: undecidable without the locale.
        assertNull(infer(listOf("1,234", "2,500", "10")))
        assertNull(infer(emptyList()))
        // Two cells disagree: better undecided than a coin flip.
        assertNull(infer(listOf("12,5", "12.5")))
    }

    @Test
    fun `amount rejects non-numeric and empty input`() {
        assertNull(amount("abc"))
        assertNull(amount("   "))
    }

    @Test
    fun `date parses ISO, European layouts and a timestamp suffix`() {
        assertEquals(LocalDate.of(2026, 7, 8), CsvFieldParsers.parseDate("2026-07-08"))
        assertEquals(LocalDate.of(2026, 7, 13), CsvFieldParsers.parseDate("13/07/2026"))
        assertEquals(LocalDate.of(2026, 7, 8), CsvFieldParsers.parseDate("2026-07-08 08:00"))
        assertEquals(LocalDate.of(2026, 7, 8), CsvFieldParsers.parseDate("2026-07-08T08:00:00Z"))
        assertNull(CsvFieldParsers.parseDate("not a date"))
    }

    @Test
    fun `currency resolves ISO codes case-insensitively and rejects others`() {
        assertEquals(Currency.getInstance("EUR"), CsvFieldParsers.parseCurrency("eur"))
        assertNull(CsvFieldParsers.parseCurrency("EURO"))
        assertNull(CsvFieldParsers.parseCurrency("$"))
    }

    @Test
    fun `a flag reads the localized yes in either language and a few common spellings`() {
        listOf("Sì", "si", "SI", "Yes", "y", "TRUE", "vero", "x", "1").forEach {
            assertTrue(CsvFieldParsers.parseFlag(it), "expected \"$it\" to read as true")
        }
    }

    @Test
    fun `a blank, a no and anything unrecognized read as false`() {
        listOf("", "   ", "No", "false", "0", "boh").forEach {
            assertFalse(CsvFieldParsers.parseFlag(it), "expected \"$it\" to read as false")
        }
    }
}

class CsvHeaderMapperTest {

    private val labels = mapOf(
        CsvField.DATE to "Data",
        CsvField.AMOUNT to "Importo",
        CsvField.TYPE to "Tipo",
    )

    @Test
    fun `maps localized headers regardless of order`() {
        val mapping = CsvHeaderMapper.map(listOf("Importo", "Data", "Tipo"), labels)
        assertTrue(mapping != null)
        assertEquals("12", mapping!!.rawValue(listOf("12", "2026-07-08", "Spesa"), CsvField.AMOUNT))
        assertEquals("2026-07-08", mapping.rawValue(listOf("12", "2026-07-08", "Spesa"), CsvField.DATE))
    }

    @Test
    fun `falls back to built-in aliases and is accent-insensitive`() {
        val mapping = CsvHeaderMapper.map(listOf("date", "amount", "descrizione"), emptyMap())
        assertTrue(mapping != null)
        assertTrue(mapping!!.has(CsvField.DESCRIPTION))
    }

    @Test
    fun `recognizes the counterparty and flag columns in both languages`() {
        val italian = CsvHeaderMapper.map(
            listOf("Data", "Importo", "Controparte", "Escluso dalle statistiche", "Rimborso"),
            emptyMap(),
        )
        assertTrue(italian != null)
        assertTrue(italian!!.has(CsvField.COUNTERPARTY))
        assertTrue(italian.has(CsvField.EXCLUDED_FROM_STATS))
        assertTrue(italian.has(CsvField.REFUND))

        val english = CsvHeaderMapper.map(
            listOf("date", "amount", "counterparty", "excluded from stats", "refund"),
            emptyMap(),
        )
        assertTrue(english != null)
        assertTrue(english!!.has(CsvField.COUNTERPARTY))
        assertTrue(english.has(CsvField.EXCLUDED_FROM_STATS))
        assertTrue(english.has(CsvField.REFUND))
    }

    @Test
    fun `a file without the counterparty and flag columns still maps`() {
        val mapping = CsvHeaderMapper.map(listOf("Data", "Importo", "Conto"), emptyMap())
        assertTrue(mapping != null)
        assertTrue(!mapping!!.has(CsvField.COUNTERPARTY))
        assertTrue(!mapping.has(CsvField.EXCLUDED_FROM_STATS))
        assertTrue(!mapping.has(CsvField.REFUND))
    }

    @Test
    fun `returns null when date or amount cannot be found`() {
        assertNull(CsvHeaderMapper.map(listOf("foo", "bar"), emptyMap()))
        assertNull(CsvHeaderMapper.map(listOf("date", "foo"), emptyMap()))
    }
}
