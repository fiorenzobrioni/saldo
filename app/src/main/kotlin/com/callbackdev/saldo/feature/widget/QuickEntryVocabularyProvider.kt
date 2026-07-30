package com.callbackdev.saldo.feature.widget

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import com.callbackdev.saldo.core.domain.quickentry.QuickEntryVocabulary
import com.callbackdev.saldo.core.domain.search.SearchText
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the parser vocabulary from platform locale data, never from word
 * lists hardcoded for one language (ADR 42): "ieri" and "lun" come from CLDR
 * (via ICU) and java.time, so the quick text entry understands whatever
 * language the device speaks. Built once per process; the parser itself stays
 * pure and JVM-testable, tests hand it a vocabulary of their own.
 */
@Singleton
class QuickEntryVocabularyProvider @Inject constructor() {

    val vocabulary: QuickEntryVocabulary by lazy { build(Locale.getDefault()) }

    /** The markers that flag an amount token: the currency's symbol and ISO code. */
    fun currencyMarkers(currency: Currency): Set<String> = buildSet {
        add(SearchText.normalize(currency.currencyCode))
        val symbol = currency.getSymbol(Locale.getDefault())
        if (symbol.isNotBlank()) add(SearchText.normalize(symbol))
    }

    private fun build(locale: Locale): QuickEntryVocabulary {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val relative = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
        val weekdays = buildMap {
            for (day in DayOfWeek.entries) {
                for (style in WEEKDAY_STYLES) {
                    val word = asVocabularyWord(day.getDisplayName(style, locale))
                    if (word.isNotEmpty()) put(word, day)
                }
            }
        }
        // Where the day sits relative to the month in the locale's own short
        // date pattern decides how "3/7" reads.
        val shortDatePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
        return QuickEntryVocabulary(
            groupingSeparator = symbols.groupingSeparator,
            yesterdayWords = relativeDay(relative, RelativeDateTimeFormatter.Direction.LAST),
            todayWords = relativeDay(relative, RelativeDateTimeFormatter.Direction.THIS),
            tomorrowWords = relativeDay(relative, RelativeDateTimeFormatter.Direction.NEXT),
            weekdayWords = weekdays,
            dayBeforeMonth = shortDatePattern.indexOf('d') < shortDatePattern.indexOf('M'),
        )
    }

    private fun relativeDay(
        formatter: RelativeDateTimeFormatter,
        direction: RelativeDateTimeFormatter.Direction,
    ): Set<String> {
        val word = formatter.format(direction, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            ?.let(::asVocabularyWord)
        return if (word.isNullOrEmpty()) emptySet() else setOf(word)
    }

    /**
     * Normalized like the parser normalizes tokens, with the trailing period
     * some locales put on abbreviations ("lun.") dropped, since the parser
     * trims punctuation off tokens before looking them up.
     */
    private fun asVocabularyWord(text: String): String =
        SearchText.normalize(text).trim('.').trim()

    private companion object {
        val WEEKDAY_STYLES = listOf(
            TextStyle.FULL,
            TextStyle.FULL_STANDALONE,
            TextStyle.SHORT,
            TextStyle.SHORT_STANDALONE,
        )
    }
}
