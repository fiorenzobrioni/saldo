package com.callbackdev.saldo.core.common.time

import com.callbackdev.saldo.core.common.di.ClockModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class SystemZoneClockTest {

    private val original: TimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreZone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `the injected clock follows the phone to a new time zone`() {
        // Android updates the process's default zone on ACTION_TIMEZONE_CHANGED: a clock
        // built once at startup must not keep the zone it was built in.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val clock = ClockModule.provideClock()
        assertEquals(ZoneId.of("Europe/Rome"), clock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertEquals(ZoneId.of("America/New_York"), clock.zone)
        assertEquals(LocalDate.now(ZoneId.of("America/New_York")), LocalDate.now(clock))
    }

    @Test
    fun `a clock pinned to a zone stays there`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        val pinned = SystemZoneClock.withZone(ZoneId.of("Asia/Kolkata"))

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertEquals(ZoneId.of("Asia/Kolkata"), pinned.zone)
    }
}
