package com.callbackdev.saldo.core.database.seed

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Seeds the localized default categories the first time the database is created.
 *
 * Inserts synchronously on the very connection that is creating the schema,
 * before the database becomes visible to anyone: if the process dies mid-way,
 * SQLite rolls the creation back and [onCreate] runs again on the next launch,
 * so the database can never exist without its default categories.
 */
class DatabaseSeedCallback @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DefaultCategories.build(context).forEach { category ->
            db.insert(
                "categories",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("name", category.name)
                    put("type", category.type.name)
                    put("color", category.color)
                    put("icon", category.icon)
                    put("sortOrder", category.sortOrder)
                    put("isDefault", if (category.isDefault) 1 else 0)
                },
            )
        }
    }
}
