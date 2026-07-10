package com.callbackdev.saldo.feature.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalTime

class GreetingBandTest {

    @Test
    fun `maps each hour to its band at the boundaries`() {
        assertEquals(GreetingBand.NIGHT, GreetingBand.of(LocalTime.of(0, 0)))
        assertEquals(GreetingBand.NIGHT, GreetingBand.of(LocalTime.of(5, 59)))
        assertEquals(GreetingBand.MORNING, GreetingBand.of(LocalTime.of(6, 0)))
        assertEquals(GreetingBand.MORNING, GreetingBand.of(LocalTime.of(11, 59)))
        assertEquals(GreetingBand.AFTERNOON, GreetingBand.of(LocalTime.of(12, 0)))
        assertEquals(GreetingBand.AFTERNOON, GreetingBand.of(LocalTime.of(17, 59)))
        assertEquals(GreetingBand.EVENING, GreetingBand.of(LocalTime.of(18, 0)))
        assertEquals(GreetingBand.EVENING, GreetingBand.of(LocalTime.of(23, 59)))
    }
}
