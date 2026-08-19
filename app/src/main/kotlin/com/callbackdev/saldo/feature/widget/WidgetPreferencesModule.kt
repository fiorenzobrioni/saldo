package com.callbackdev.saldo.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the DataStore holding the per-instance settings of placed widgets. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetPreferences

/**
 * One file for every placed widget, keyed by app widget id, where Glance kept
 * one file per instance.
 *
 * The count is the point. Glance's own state definition writes
 * `appWidget-<id>.preferences_pb` per widget and opens each of them separately;
 * a refresh that touched three widgets was three DataStore opens and three
 * writes. Here a render reads the single file once for every instance it is
 * about to draw, and a refresh writes nothing at all - there is no session to
 * poke, so the revision counter that used to cost one write per widget per
 * refresh is gone with it.
 *
 * Dedicated rather than folded into user preferences: a corrupted file must
 * cost widget settings (re-enterable, and the widget keeps working on its
 * defaults meanwhile), never an app setting.
 */
private val Context.widgetStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_config",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
object WidgetPreferencesModule {

    @Provides
    @Singleton
    @WidgetPreferences
    fun provideWidgetDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.widgetStore
}
