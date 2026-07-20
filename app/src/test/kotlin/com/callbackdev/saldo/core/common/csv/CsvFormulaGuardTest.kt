package com.callbackdev.saldo.core.common.csv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CsvFormulaGuardTest {

    @Test
    fun `fields starting with a formula trigger are prefixed`() {
        assertEquals("'=SUM(A1)", CsvFormulaGuard.guard("=SUM(A1)"))
        assertEquals("'+1", CsvFormulaGuard.guard("+1"))
        assertEquals("'-1", CsvFormulaGuard.guard("-1"))
        assertEquals("'@x", CsvFormulaGuard.guard("@x"))
    }

    @Test
    fun `ordinary text is left unchanged`() {
        assertEquals("Pizza", CsvFormulaGuard.guard("Pizza"))
        assertEquals("", CsvFormulaGuard.guard(""))
    }

    @Test
    fun `strip reverses guard exactly`() {
        listOf("=SUM(A1)", "+1", "-1", "@cmd", "Pizza", "conto corrente").forEach { original ->
            assertEquals(original, CsvFormulaGuard.strip(CsvFormulaGuard.guard(original)))
        }
    }

    @Test
    fun `strip leaves a legitimate leading apostrophe alone`() {
        assertEquals("'ciao", CsvFormulaGuard.strip("'ciao"))
        assertEquals("=x", CsvFormulaGuard.strip("'=x"))
    }
}
