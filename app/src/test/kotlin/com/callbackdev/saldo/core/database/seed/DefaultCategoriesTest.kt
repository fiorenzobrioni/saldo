package com.callbackdev.saldo.core.database.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultCategoriesTest {

    @Test
    fun `default set matches the vision list`() {
        // 17 expense + 5 income categories (see VISION.md).
        assertEquals(22, DefaultCategories.count)
    }
}
