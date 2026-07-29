package com.callbackdev.saldo.core.domain.tag

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TagNamesTest {

    @Test
    fun `normalize trims and collapses inner whitespace, keeping the casing`() {
        assertEquals("Job Hunt", TagNames.normalize("  Job \t  Hunt  "))
    }

    @Test
    fun `a blank input normalizes to the empty string`() {
        assertEquals("", TagNames.normalize("   \t "))
    }

    @Test
    fun `the key folds the case but keeps the accents`() {
        assertEquals("job hunt", TagNames.key("  Job \t HUNT "))
        assertEquals("perù", TagNames.key(" PERÙ "))
    }

    @Test
    fun `sameName matches spellings of the same tag and keeps accented ones apart`() {
        assertTrue(TagNames.sameName("Spesa ", " spesa"))
        assertTrue(TagNames.sameName("job  hunt", "Job Hunt"))
        assertFalse(TagNames.sameName("peru", "perù"))
    }
}
