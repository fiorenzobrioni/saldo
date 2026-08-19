package com.callbackdev.saldo.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.callbackdev.saldo.core.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The per-instance settings of every placed widget, in one file keyed by app
 * widget id.
 *
 * Two hooks exist here that the Glance-backed version never had, and both fix
 * silent bugs. [forget] stops a removed widget from leaving its settings behind
 * forever, and [remap] re-keys them after a backup restore, which hands the
 * same widget a brand new id: without it a restored widget inherits whichever
 * stale record happened to reuse its number, so it would quietly start adding
 * to somebody else's account.
 */
@Singleton
class WidgetConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @WidgetPreferences private val dataStore: DataStore<Preferences>,
) {

    private val importLock = Mutex()
    private val imported = mutableSetOf<Int>()

    /** One read for the whole render, however many instances it covers. */
    suspend fun readAll(appWidgetIds: IntArray): Map<Int, QuickAddWidgetConfig> {
        appWidgetIds.forEach { importLegacyState(it) }
        val preferences = dataStore.data.first()
        return appWidgetIds.associateWith { QuickAddWidgetPrefs.read(preferences, it) }
    }

    suspend fun read(appWidgetId: Int): QuickAddWidgetConfig {
        importLegacyState(appWidgetId)
        return QuickAddWidgetPrefs.read(dataStore.data.first(), appWidgetId)
    }

    /**
     * The configuration screen's save. Writes the runtime type as well: leaving
     * the selector where it was would mean the widget ignored the "starts on"
     * value the user just chose.
     */
    suspend fun write(appWidgetId: Int, config: QuickAddWidgetConfig) {
        dataStore.edit { preferences ->
            QuickAddWidgetPrefs.write(preferences, appWidgetId, config)
        }
    }

    /**
     * The home-screen selector. Writes [QuickAddWidgetPrefs.currentType] alone:
     * this is where the widget is now, not where it starts, and writing the
     * configured value from here made "starts on" change by itself every time
     * the selector was touched.
     */
    suspend fun setCurrentType(appWidgetId: Int, type: TransactionType) {
        dataStore.edit { preferences ->
            preferences[QuickAddWidgetPrefs.currentType(appWidgetId)] = type.name
        }
    }

    /** Removed instances must not leave their settings behind in the store. */
    suspend fun forget(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        dataStore.edit { preferences ->
            appWidgetIds.forEach { QuickAddWidgetPrefs.clear(preferences, it) }
        }
    }

    /**
     * A restore gives the same widget a new id while the records are keyed by
     * the old one (the DataStore file rides along in the backup), so they must
     * be re-keyed or a widget would inherit whichever record happened to reuse
     * its number.
     */
    suspend fun remap(oldIds: IntArray, newIds: IntArray) {
        if (oldIds.isEmpty() || oldIds.size != newIds.size) return
        dataStore.edit { preferences ->
            val moved = oldIds.map { QuickAddWidgetPrefs.read(preferences, it) }
            oldIds.forEach { QuickAddWidgetPrefs.clear(preferences, it) }
            newIds.forEachIndexed { index, id ->
                QuickAddWidgetPrefs.write(preferences, id, moved[index])
            }
        }
    }

    /**
     * Carries a widget configured under the Glance build across to this store,
     * once per instance.
     *
     * Glance kept one preferences file per widget, named after the id, with the
     * very same key names this store uses unsuffixed. Reading it back is
     * therefore a plain DataStore open, and the file is deleted straight after
     * so it can never be opened twice - DataStore refuses a second instance on
     * a file already active in the process.
     *
     * Wrapped whole: a widget whose settings cannot be recovered falls back to
     * the defaults, which is a working widget, not a broken one.
     */
    private suspend fun importLegacyState(appWidgetId: Int) {
        if (appWidgetId in imported) return
        importLock.withLock {
            if (!imported.add(appWidgetId)) return
            runCatching {
                val file = File(context.filesDir, "$LEGACY_DIR/$LEGACY_PREFIX$appWidgetId$LEGACY_SUFFIX")
                if (!file.exists()) return@runCatching
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                val legacy = try {
                    PreferenceDataStoreFactory.create(scope = scope) { file }.data.first()
                } finally {
                    scope.cancel()
                }
                dataStore.edit { preferences ->
                    QuickAddWidgetPrefs.write(
                        preferences,
                        appWidgetId,
                        QuickAddWidgetPrefs.readLegacy(legacy),
                    )
                }
                file.delete()
            }
        }
    }

    private companion object {
        const val LEGACY_DIR = "datastore"

        /** Glance's own per-widget file name (`createUniqueRemoteUiName`). */
        const val LEGACY_PREFIX = "appWidget-"
        const val LEGACY_SUFFIX = ".preferences_pb"
    }
}
