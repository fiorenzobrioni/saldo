package com.callbackdev.saldo.core.common.date

import java.util.Locale

/**
 * Normalizes a formatted date's casing for locales whose date names are
 * lowercase in prose (Italian: "lunedì 13 luglio"). Stock CLDR data already
 * produces lowercase, but some OEM ICU builds ship weekday and month names
 * titlecased for standalone contexts ("Lunedì 13 Luglio"): for those locales
 * the whole string is lowercased explicitly, while proper-noun locales
 * (English) pass through untouched. Apply to every user-facing date built
 * from a skeleton containing EEEE/MMM/MMMM.
 */
fun String.withLocaleDateCasing(locale: Locale): String =
    if (locale.language == "it") lowercase(locale) else this
