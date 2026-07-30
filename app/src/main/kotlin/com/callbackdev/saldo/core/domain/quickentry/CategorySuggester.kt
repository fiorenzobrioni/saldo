package com.callbackdev.saldo.core.domain.quickentry

import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.search.SearchText

/** Where a suggestion came from, because the two stages behave differently. */
enum class SuggestionOrigin {
    /** The word IS a category name: the word can be dropped from the description. */
    CATEGORY_NAME,

    /** The word was used before under this category: the description keeps it. */
    HISTORY,
}

data class CategorySuggestion(
    val categoryId: Long,
    val origin: SuggestionOrigin,
    /** The (normalized) word that produced the suggestion. */
    val word: String,
)

/**
 * Suggests a category for the quick text entry from what the user has already
 * written, never from a built-in dictionary (ADR 42). Two stages: the names of
 * the user's own categories, then the descriptions used in the past. Both are
 * pure functions over data the caller fetched, so the thresholds are testable
 * on the JVM.
 *
 * The non-negotiable rule of the phase applies here at its hardest: a weak or
 * contested signal produces NO suggestion. A blank category costs one tap; a
 * wrong one costs a wrong statistic discovered a month later.
 */
object CategorySuggester {

    /** A word seen fewer times than this never drives a suggestion. */
    const val MIN_OCCURRENCES = 3

    /** The dominant category must hold at least 2/3 of the word's uses. */
    private const val MAJORITY_NUMERATOR = 2
    private const val MAJORITY_DENOMINATOR = 3

    private val NAME_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    /**
     * [usage] maps each searched word to the category ids of the past
     * movements whose description contains it as a whole word (the caller
     * filters rows with [matches]).
     */
    fun suggest(
        words: List<String>,
        categories: List<Category>,
        usage: Map<String, List<Long>>,
    ): CategorySuggestion? = byName(words, categories) ?: byHistory(words, usage)

    /**
     * Stage one: a word that is a category name (whole name, or a whole word
     * of a multi-word name). Exactly one category may match: two candidates
     * are an ambiguity, and ambiguity means silence, not a coin toss.
     */
    fun byName(words: List<String>, categories: List<Category>): CategorySuggestion? {
        val hits = categories.mapNotNull { category ->
            val name = SearchText.normalize(category.name)
            val nameWords = name.split(NAME_SEPARATORS)
                .filter { it.length >= QuickEntryParser.MIN_WORD_LENGTH }
            words.firstOrNull { it == name || it in nameWords }
                ?.let { word -> CategorySuggestion(category.id, SuggestionOrigin.CATEGORY_NAME, word) }
        }
        return hits.singleOrNull()
    }

    /**
     * Stage two: the category under which a word was filed in the past, when
     * the signal is clear (at least [MIN_OCCURRENCES] uses and a 2/3
     * majority). The strongest word wins; two words pointing to different
     * categories with the same strength cancel out.
     */
    private fun byHistory(words: List<String>, usage: Map<String, List<Long>>): CategorySuggestion? {
        var best: CategorySuggestion? = null
        var bestCount = 0
        var contested = false
        for (word in words) {
            val ids = usage[word].orEmpty()
            val top = ids.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val clearSignal = top.value >= MIN_OCCURRENCES &&
                top.value * MAJORITY_DENOMINATOR >= ids.size * MAJORITY_NUMERATOR
            when {
                !clearSignal -> Unit
                top.value > bestCount -> {
                    best = CategorySuggestion(top.key, SuggestionOrigin.HISTORY, word)
                    bestCount = top.value
                    contested = false
                }
                top.value == bestCount && top.key != best?.categoryId -> contested = true
            }
        }
        return best.takeIf { !contested }
    }

    /** Whole-word, accent- and case-insensitive match of a normalized [word]. */
    fun matches(word: String, description: String): Boolean {
        val text = SearchText.normalize(description)
        var index = text.indexOf(word)
        while (index >= 0) {
            val before = index == 0 || !text[index - 1].isLetterOrDigit()
            val end = index + word.length
            val after = end == text.length || !text[end].isLetterOrDigit()
            if (before && after) return true
            index = text.indexOf(word, index + 1)
        }
        return false
    }
}
