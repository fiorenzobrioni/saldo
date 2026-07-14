package com.callbackdev.saldo.core.common.date

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class DateCasingTest {

    @Test
    fun `italian dates are lowercased even when ICU titlecases them`() {
        assertEquals(
            "lunedì 13 luglio",
            "Lunedì 13 Luglio".withLocaleDateCasing(Locale.ITALIAN),
        )
    }

    @Test
    fun `italian regional variants are covered by the language match`() {
        assertEquals(
            "luglio 2026",
            "Luglio 2026".withLocaleDateCasing(Locale.forLanguageTag("it-CH")),
        )
    }

    @Test
    fun `proper-noun locales pass through untouched`() {
        assertEquals(
            "Monday, July 13",
            "Monday, July 13".withLocaleDateCasing(Locale.ENGLISH),
        )
    }
}
