package com.callbackdev.saldo.core.domain.quickentry

import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategorySuggesterTest {

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        color = 0x66BB6A,
        icon = "shopping_cart",
    )

    private val fuel = category(1L, "Benzina")
    private val dining = category(2L, "Bar e ristoranti")
    private val coffee = category(3L, "Caffè")

    private val categories = listOf(fuel, dining, coffee)

    @Test
    fun `a word that is a category name suggests that category`() {
        val suggestion = CategorySuggester.byName(listOf("benzina"), categories)
        assertEquals(fuel.id, suggestion?.categoryId)
        assertEquals(SuggestionOrigin.CATEGORY_NAME, suggestion?.origin)
    }

    @Test
    fun `a word of a multi-word category name matches too`() {
        assertEquals(dining.id, CategorySuggester.byName(listOf("ristoranti"), categories)?.categoryId)
    }

    @Test
    fun `accents and case never block a name match`() {
        assertEquals(coffee.id, CategorySuggester.byName(listOf("caffe"), categories)?.categoryId)
    }

    @Test
    fun `two matching categories are an ambiguity and produce silence`() {
        val ambiguous = listOf(category(7L, "Bar"), dining)
        assertNull(CategorySuggester.byName(listOf("bar"), ambiguous))
    }

    @Test
    fun `a word never seen before produces no suggestion at all`() {
        assertNull(
            CategorySuggester.suggest(listOf("zumba"), categories, usage = mapOf("zumba" to emptyList())),
        )
    }

    @Test
    fun `history suggests the dominant category above both thresholds`() {
        val usage = mapOf("pizza" to listOf(2L, 2L, 2L, 3L))
        val suggestion = CategorySuggester.suggest(listOf("pizza"), categories, usage)
        assertEquals(dining.id, suggestion?.categoryId)
        assertEquals(SuggestionOrigin.HISTORY, suggestion?.origin)
    }

    @Test
    fun `fewer than three occurrences is not a habit yet`() {
        val usage = mapOf("pizza" to listOf(2L, 2L))
        assertNull(CategorySuggester.suggest(listOf("pizza"), categories, usage))
    }

    @Test
    fun `a split habit below the two-thirds majority stays silent`() {
        val usage = mapOf("pizza" to listOf(2L, 2L, 2L, 3L, 3L, 3L))
        assertNull(CategorySuggester.suggest(listOf("pizza"), categories, usage))
    }

    @Test
    fun `the strongest word wins and an even conflict cancels out`() {
        val stronger = mapOf(
            "pizza" to listOf(2L, 2L, 2L, 2L),
            "taxi" to listOf(1L, 1L, 1L),
        )
        assertEquals(2L, CategorySuggester.suggest(listOf("pizza", "taxi"), categories, stronger)?.categoryId)

        val even = mapOf(
            "pizza" to listOf(2L, 2L, 2L),
            "taxi" to listOf(1L, 1L, 1L),
        )
        assertNull(CategorySuggester.suggest(listOf("pizza", "taxi"), categories, even))
    }

    @Test
    fun `a category name outranks the history`() {
        val usage = mapOf("benzina" to listOf(2L, 2L, 2L))
        val suggestion = CategorySuggester.suggest(listOf("benzina"), categories, usage)
        assertEquals(fuel.id, suggestion?.categoryId)
        assertEquals(SuggestionOrigin.CATEGORY_NAME, suggestion?.origin)
    }

    @Test
    fun `matches wants whole words, folded`() {
        assertTrue(CategorySuggester.matches("pizza", "Pizza da Mario"))
        assertTrue(CategorySuggester.matches("caffe", "CAFFÈ Vergnano"))
        assertTrue(CategorySuggester.matches("mario", "Pizza da Mario"))
        assertFalse(CategorySuggester.matches("pizza", "pizzeria napoletana"))
        assertFalse(CategorySuggester.matches("caffe", "decaffeinato"))
    }
}
