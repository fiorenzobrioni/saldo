package com.callbackdev.saldo.core.common.applock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForegroundTrackerTest {

    private var backgrounded = 0
    private var foregrounded = 0

    private val tracker = ForegroundTracker(
        onAppBackgrounded = { backgrounded++ },
        onAppForegrounded = { foregrounded++ },
    )

    @Test
    fun `first start is a foreground transition, last stop a background one`() {
        tracker.onActivityStarted()
        assertEquals(1, foregrounded)

        tracker.onActivityStopped(isChangingConfigurations = false)
        assertEquals(1, backgrounded)

        tracker.onActivityStarted()
        assertEquals(2, foregrounded)
    }

    @Test
    fun `a configuration change never counts as leaving the app`() {
        tracker.onActivityStarted()

        // Rotation: the activity stops flagged as changing configurations,
        // then its recreation starts again.
        tracker.onActivityStopped(isChangingConfigurations = true)
        tracker.onActivityStarted()

        assertEquals(0, backgrounded)
        // Still the single initial foreground: the rotation added none.
        assertEquals(1, foregrounded)
    }

    @Test
    fun `moving between two activities of the app is one continuous foreground`() {
        tracker.onActivityStarted()

        // MainActivity -> QuickEntryActivity: the new one starts before the
        // old one stops, so the started count never touches zero.
        tracker.onActivityStarted()
        tracker.onActivityStopped(isChangingConfigurations = false)

        assertEquals(0, backgrounded)
        assertEquals(1, foregrounded)

        tracker.onActivityStopped(isChangingConfigurations = false)
        assertEquals(1, backgrounded)
    }

    @Test
    fun `a real background after a rotation still registers`() {
        tracker.onActivityStarted()
        tracker.onActivityStopped(isChangingConfigurations = true)
        tracker.onActivityStarted()

        tracker.onActivityStopped(isChangingConfigurations = false)

        assertEquals(1, backgrounded)
    }
}
