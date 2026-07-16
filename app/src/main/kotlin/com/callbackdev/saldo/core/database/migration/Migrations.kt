package com.callbackdev.saldo.core.database.migration

import androidx.room.migration.Migration

/**
 * Explicit, tested Room migrations (PLANNING ADR: never `fallbackToDestructiveMigration`).
 *
 * The schema history was collapsed to a single version-1 baseline while the app
 * was still unpublished (no database exists in the wild to preserve), so there
 * are no migrations yet. The policy is unchanged and applies from the next
 * schema change onward: add a `Migration(N, N+1)` here, append it to
 * [ALL_MIGRATIONS], bump the database version, and cover it with an instrumented
 * test that validates against the exported schema JSON.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
