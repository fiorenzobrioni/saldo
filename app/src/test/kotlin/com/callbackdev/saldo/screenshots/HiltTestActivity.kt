package com.callbackdev.saldo.screenshots

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.saldo.R
import dagger.hilt.android.AndroidEntryPoint
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * A bare Hilt host for the real screens, so `hiltViewModel()` builds their
 * ViewModels from the test graph. It lives in the test sources only: nothing
 * of it reaches the app.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()

/**
 * Registers [HiltTestActivity] with Robolectric's package manager before the
 * Compose rule launches it: the merged manifest belongs to the app and does not
 * (and must not) declare a test activity.
 */
class RegisterHiltTestActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val activityInfo = ActivityInfo().apply {
                name = HiltTestActivity::class.java.name
                packageName = context.packageName
                theme = R.style.Theme_Saldo
                // As a manifest entry would get by default: Compose's capture
                // waits for a hardware-accelerated frame.
                flags = ActivityInfo.FLAG_HARDWARE_ACCELERATED
            }
            shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
            base.evaluate()
        }
    }
}
