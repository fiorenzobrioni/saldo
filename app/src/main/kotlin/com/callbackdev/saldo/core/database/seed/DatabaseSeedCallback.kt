package com.callbackdev.saldo.core.database.seed

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.callbackdev.saldo.core.database.SaldoDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

/**
 * Seeds the localized default categories the first time the database is created.
 *
 * Uses a [Provider] to obtain the database (breaking the callback -> database
 * dependency cycle) and inserts through the DAO on a background scope. Runs only
 * on [onCreate], so it never re-seeds an existing database.
 */
class DatabaseSeedCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: Provider<SaldoDatabase>,
) : RoomDatabase.Callback() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val categories = DefaultCategories.build(context)
        scope.launch {
            databaseProvider.get().categoryDao().insertAll(categories)
        }
    }
}
