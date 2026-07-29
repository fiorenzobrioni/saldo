package com.callbackdev.saldo.core.common.prefs

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The DI door for [UserPreferencesRepository], for code that Hilt does not
 * inject. Today that means the instrumented tests, which must seed the launch
 * state before `MainActivity` starts: the preferences store is one instance
 * per process (a `Context.preferencesDataStore` delegate), so a second one
 * built by the test would clash with the app's own on the same file.
 *
 * Same pattern as `WidgetEntryPoint`, which exists for the same reason on the
 * other side of the app.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserPreferencesEntryPoint {

    fun userPreferences(): UserPreferencesRepository
}
