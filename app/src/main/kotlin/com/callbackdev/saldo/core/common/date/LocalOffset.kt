package com.callbackdev.saldo.core.common.date

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The offset this zone has at [dateTime]. In the hour the clocks repeat there are
 * two, and [preferred] picks one of them when it is one (the offset of the moment
 * the date and time were read at); otherwise the earlier, as `ZoneRules.getOffset`
 * gives. In the hour the clocks skip there is none, and the offset before the gap
 * is kept, as `ZoneRules.getOffset` gives too.
 */
fun ZoneId.offsetAt(dateTime: LocalDateTime, preferred: ZoneOffset?): ZoneOffset =
    preferred?.takeIf { it in rules.getValidOffsets(dateTime) } ?: rules.getOffset(dateTime)
