package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupData
import com.callbackdev.saldo.core.domain.backup.fullyPopulatedBackupFile
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Phase 8 contract: export -> file -> import loses nothing. The repository
 * is an in-memory fake, so this exercises the whole domain path (snapshot,
 * encode, decode, inspect, restore) with the real codec in the middle.
 */
class BackupRoundTripTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC)

    private class FakeBackupRepository(var stored: BackupData) : BackupRepository {
        override suspend fun createSnapshot(): BackupData = stored
        override suspend fun restore(data: BackupData) {
            stored = data
        }
    }

    @Test
    fun `export then import restores the exact same data`() = runTest {
        val original = fullyPopulatedBackupFile().data
        val source = FakeBackupRepository(original)
        val destination = FakeBackupRepository(BackupData())

        val export = ExportBackupUseCase(source, clock).invoke(appVersion = "0.8.0")
        val importer = ImportBackupUseCase(destination)
        val inspection = importer.inspect(export.json)

        assertTrue(inspection is ImportBackupUseCase.Inspection.Valid)
        importer.restore((inspection as ImportBackupUseCase.Inspection.Valid).file)

        assertEquals(original, destination.stored)
    }

    @Test
    fun `export stamps the clock instant and the app version`() = runTest {
        val export = ExportBackupUseCase(FakeBackupRepository(BackupData()), clock)
            .invoke(appVersion = "0.8.0")

        assertEquals(clock.instant(), export.summary.exportedAt)
        assertEquals("0.8.0", export.summary.appVersion)
    }

    @Test
    fun `export summary matches the snapshot contents`() = runTest {
        val export = ExportBackupUseCase(FakeBackupRepository(fullyPopulatedBackupFile().data), clock)
            .invoke(appVersion = null)

        assertEquals(2, export.summary.accounts)
        assertEquals(2, export.summary.transactions)
        assertEquals(1, export.summary.recurringRules)
    }

    @Test
    fun `inspect maps decode failures to user-facing errors`() {
        val importer = ImportBackupUseCase(FakeBackupRepository(BackupData()))

        assertEquals(
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.NOT_A_BACKUP),
            importer.inspect("""{"hello": "world"}"""),
        )
        assertEquals(
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.UNSUPPORTED_VERSION),
            importer.inspect("""{"format": "saldo-backup", "version": 42, "data": {}}"""),
        )
        assertEquals(
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.CORRUPTED),
            importer.inspect("definitely not json"),
        )
    }

    @Test
    fun `inspect summarizes a valid file without touching the repository`() {
        val destination = FakeBackupRepository(fullyPopulatedBackupFile().data)
        val importer = ImportBackupUseCase(destination)
        val json = """
            {"format": "saldo-backup", "version": 1, "exportedAtEpochMilli": 5000, "data": {}}
        """.trimIndent()

        val inspection = importer.inspect(json)

        assertTrue(inspection is ImportBackupUseCase.Inspection.Valid)
        assertEquals(
            Instant.ofEpochMilli(5000),
            (inspection as ImportBackupUseCase.Inspection.Valid).summary.exportedAt,
        )
        // Inspection is read-only: the current data is still there.
        assertEquals(fullyPopulatedBackupFile().data, destination.stored)
    }
}
