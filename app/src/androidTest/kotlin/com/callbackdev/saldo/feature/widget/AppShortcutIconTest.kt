package com.callbackdev.saldo.feature.widget

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app shortcut's icon is drawn twice from the same resource: by Glance on
 * the widget, and by `painterResource` in the settings preview. The second one
 * is the fussy one - it takes vector drawables and rasters, and nothing else -
 * and pointing it at `@mipmap/ic_launcher`, which is an `<adaptive-icon>`,
 * crashed the settings screen the instant the toggle was switched on.
 *
 * Nothing in a build says which drawable kind a resource id resolves to, so it
 * is asserted here.
 */
@RunWith(AndroidJUnit4::class)
class AppShortcutIconTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun theShortcutIconIsAVectorPainterResourceCanLoad() {
        val drawable = context.getDrawable(AppShortcutIcon)
        assertNotNull("The shortcut icon resource does not resolve", drawable)
        assertTrue(
            "The shortcut icon must be a vector: painterResource cannot load anything else",
            drawable is VectorDrawable,
        )
    }

    @Test
    fun theShortcutIconIsNotTheAdaptiveLauncherIcon() {
        val drawable = context.getDrawable(AppShortcutIcon)
        assertFalse(
            "An adaptive icon here is the exact crash this guards against",
            drawable is AdaptiveIconDrawable,
        )
    }

    @Test
    fun theShortcutIconIsSquareSoTheButtonReadsAsOne() {
        val drawable = requireNotNull(context.getDrawable(AppShortcutIcon))
        assertTrue(
            "A non-square mark would letterbox inside a square button",
            drawable.intrinsicWidth == drawable.intrinsicHeight,
        )
    }
}
