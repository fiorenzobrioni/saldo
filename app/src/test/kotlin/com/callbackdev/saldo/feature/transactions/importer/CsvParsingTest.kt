package com.callbackdev.saldo.feature.transactions.importer

import org.junit.jupiter.api.Assertions.assertEquals
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
        val records = CsvReader.parse("﻿a;b\n", ';')
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
    fun `returns null when date or amount cannot be found`() {
        assertNull(CsvHeaderMapper.map(listOf("foo", "bar"), emptyMap()))
        assertNull(CsvHeaderMapper.map(listOf("date", "foo"), emptyMap()))
    }
}
