package com.callbackdev.saldo.core.common.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the zone the phone is in now, read at every call.
 *
 * `Clock.systemDefaultZone()` captures the zone once, when it is built: held as a
 * singleton it kept the zone the process started in for as long as the process
 * lived, so after a trip "today", the month windows, the editor's default time and
 * the offset stored with a new movement all stayed in the old zone. Android updates
 * the process's default zone on `ACTION_TIMEZONE_CHANGED`, and this clock reads it.
 */
object SystemZoneClock : Clock() {

    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
