package com.callbackdev.saldo.core.common.applock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives "the app left/entered the foreground" from per-activity
 * started/stopped events. Pure and synchronous so the transition logic is
 * unit-testable without Robolectric; [AppLockLifecycleObserver] feeds it the
 * real callbacks.
 *
 * A stop with `isChangingConfigurations` marks the next start as the same
 * visit: a rotation never counts as leaving the app. This replaces a
 * `ProcessLifecycleOwner` dependency (`lifecycle-process`) with ~20 lines
 * (ADR 39).
 */
class ForegroundTracker(
    private val onAppBackgrounded: () -> Unit,
    private val onAppForegrounded: () -> Unit,
) {

    private var startedActivities = 0
    private var configurationChangeInFlight = false

    fun onActivityStarted() {
        startedActivities++
        if (startedActivities == 1) {
            if (configurationChangeInFlight) {
                configurationChangeInFlight = false
            } else {
                onAppForegrounded()
            }
        }
    }

    fun onActivityStopped(isChangingConfigurations: Boolean) {
        startedActivities--
        if (startedActivities == 0) {
            if (isChangingConfigurations) {
                configurationChangeInFlight = true
            } else {
                onAppBackgrounded()
            }
        }
    }
}

/**
 * Bridges every activity's lifecycle to [AppLockManager], so the re-lock
 * timer covers MainActivity and the widget activities alike. Registered once
 * in `SaldoApplication.onCreate`.
 */
@Singleton
class AppLockLifecycleObserver @Inject constructor(
    appLockManager: AppLockManager,
) : Application.ActivityLifecycleCallbacks {

    private val tracker = ForegroundTracker(
        onAppBackgrounded = appLockManager::onAppBackgrounded,
        onAppForegrounded = appLockManager::onAppForegrounded,
    )

    override fun onActivityStarted(activity: Activity) {
        tracker.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        tracker.onActivityStopped(activity.isChangingConfigurations)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
