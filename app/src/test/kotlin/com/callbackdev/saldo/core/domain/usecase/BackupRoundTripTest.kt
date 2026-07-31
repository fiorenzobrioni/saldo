package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.backup.BackupCodec
import com.callbackdev.saldo.core.domain.backup.BackupContent
import com.callbackdev.saldo.core.domain.backup.BackupCrypto
import com.callbackdev.saldo.core.domain.backup.BackupData
import com.callbackdev.saldo.core.domain.backup.EncryptedBackup
import com.callbackdev.saldo.core.domain.backup.SettingsBackup
import com.callbackdev.saldo.core.domain.backup.fullyPopulatedBackupFile
import com.callbackdev.saldo.core.domain.backup.fullyPopulatedSettings
import com.callbackdev.saldo.core.domain.repository.BackupRepository
import com.callbackdev.saldo.core.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Phase 8 contract, extended by Fase 22: export -> file -> import loses
 * nothing, data and settings alike, whether the file is in clear text or inside
 * the encrypted container. The repositories are in-memory fakes, so this
 * exercises the whole domain path (snapshot, encode, seal, open, decode,
 * inspect, restore) with the real codec and the real cipher in the middle.
 */
class BackupRoundTripTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC)
    private val passphrase = "una passphrase lunga"

    private class FakeBackupRepository(var stored: BackupData) : BackupRepository {
        override suspend fun createSnapshot(): BackupData = stored
        override suspend fun restore(data: BackupData) {
            stored = data
        }

        // Unused by the round trip; the real implementation also replants the
        // default categories, which is a Room concern and not this contract's.
        override suspend fun eraseAll() {
            stored = BackupData()
        }
    }

    private class FakeSettingsRepository(
        var stored: SettingsBackup = SettingsBackup(),
    ) : SettingsBackupRepository {
        var restoreCalls = 0
            private set

        override suspend fun snapshotSettings(): SettingsBackup = stored

        override suspend fun restoreSettings(settings: SettingsBackup) {
            stored = settings
            restoreCalls++
        }
    }

    @Test
    fun `export then import restores the exact same data and settings`() = runTest {
        val original = fullyPopulatedBackupFile().data
        val source = FakeBackupRepository(original)
        val sourceSettings = FakeSettingsRepository(fullyPopulatedSettings())
        val destination = FakeBackupRepository(BackupData())
        val destinationSettings = FakeSettingsRepository()

        val export = ExportBackupUseCase(source, sourceSettings, clock).invoke(appVersion = "2.0.0")
        val importer = ImportBackupUseCase(destination, destinationSettings)
        val inspection = importer.inspect(export.content)

        assertTrue(inspection is ImportBackupUseCase.Inspection.Valid)
        importer.restore((inspection as ImportBackupUseCase.Inspection.Valid).file)

        assertEquals(original, destination.stored)
        assertEquals(fullyPopulatedSettings(), destinationSettings.stored)
    }

    @Test
    fun `the exported file carries the settings even when the snapshot has none of its own`() = runTest {
        val source = FakeBackupRepository(BackupData())

        val export = ExportBackupUseCase(source, FakeSettingsRepository(), clock)
            .invoke(appVersion = null)

        assertTrue(export.summary.hasSettings)
        assertEquals(SettingsBackup(), BackupCodec.decode(export.content).data.settings)
    }

    @Test
    fun `a file written before settings existed restores its data and touches no setting`() = runTest {
        val destination = FakeBackupRepository(fullyPopulatedBackupFile().data)
        val destinationSettings = FakeSettingsRepository(fullyPopulatedSettings())
        val importer = ImportBackupUseCase(destination, destinationSettings)
        val json = """
            {"format": "saldo-backup", "version": 1, "exportedAtEpochMilli": 5000, "data": {}}
        """.trimIndent()

        val inspection = importer.inspect(json) as ImportBackupUseCase.Inspection.Valid
        importer.restore(inspection.file)

        assertEquals(BackupData(), destination.stored)
        assertEquals(0, destinationSettings.restoreCalls)
        assertEquals(fullyPopulatedSettings(), destinationSettings.stored)
        assertFalse(inspection.summary.hasSettings)
    }

    @Test
    fun `export stamps the clock instant and the app version`() = runTest {
        val export = ExportBackupUseCase(
            FakeBackupRepository(BackupData()),
            FakeSettingsRepository(),
            clock,
        ).invoke(appVersion = "0.8.0")

        assertEquals(clock.instant(), export.summary.exportedAt)
        assertEquals("0.8.0", export.summary.appVersion)
        assertFalse(export.summary.isEncrypted)
    }

    @Test
    fun `export summary matches the snapshot contents`() = runTest {
        val export = ExportBackupUseCase(
            FakeBackupRepository(fullyPopulatedBackupFile().data),
            FakeSettingsRepository(),
            clock,
        ).invoke(appVersion = null)

        assertEquals(2, export.summary.accounts)
        assertEquals(3, export.summary.transactions)
        assertEquals(1, export.summary.recurringRules)
    }

    @Test
    fun `inspect maps decode failures to user-facing errors`() {
        val importer = ImportBackupUseCase(FakeBackupRepository(BackupData()), FakeSettingsRepository())

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
        val importer = ImportBackupUseCase(destination, FakeSettingsRepository())
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

    @Test
    fun `an encrypted export round-trips through the passphrase`() = runTest {
        val original = fullyPopulatedBackupFile().data
        val destination = FakeBackupRepository(BackupData())
        val destinationSettings = FakeSettingsRepository()

        val export = exportEncrypted(original)
        val importer = ImportBackupUseCase(destination, destinationSettings)

        val locked = importer.inspect(export.content)
        assertTrue(locked is ImportBackupUseCase.Inspection.Locked)

        val unlocked = importer.unlock(
            (locked as ImportBackupUseCase.Inspection.Locked).envelope,
            passphrase.toCharArray(),
        )
        assertTrue(unlocked is ImportBackupUseCase.Inspection.Valid)
        val valid = unlocked as ImportBackupUseCase.Inspection.Valid
        assertTrue(valid.summary.isEncrypted)
        assertTrue(valid.summary.hasSettings)

        importer.restore(valid.file)
        assertEquals(original, destination.stored)
        assertEquals(fullyPopulatedSettings(), destinationSettings.stored)
    }

    @Test
    fun `an encrypted export never shows the data in the file`() = runTest {
        val export = exportEncrypted(fullyPopulatedBackupFile().data)

        assertFalse(export.content.contains("Netflix"))
        assertFalse(export.content.contains("saldo-backup\""))
        assertTrue(export.content.contains(EncryptedBackup.FORMAT))
        assertTrue(export.summary.isEncrypted)
        // Still a readable JSON header: what the file is, never what it holds.
        assertTrue(BackupCodec.read(export.content) is BackupContent.Encrypted)
    }

    @Test
    fun `a wrong passphrase is reported and leaves the data alone`() = runTest {
        val destination = FakeBackupRepository(fullyPopulatedBackupFile().data)
        val importer = ImportBackupUseCase(destination, FakeSettingsRepository())
        val content = relockCheaply(BackupData())

        val locked = importer.inspect(content) as ImportBackupUseCase.Inspection.Locked
        val unlocked = importer.unlock(locked.envelope, "un'altra passphrase".toCharArray())

        assertEquals(
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.WRONG_PASSPHRASE),
            unlocked,
        )
        assertEquals(fullyPopulatedBackupFile().data, destination.stored)
    }

    @Test
    fun `a container from a newer app is refused before the passphrase can matter`() = runTest {
        val importer = ImportBackupUseCase(FakeBackupRepository(BackupData()), FakeSettingsRepository())
        val locked = importer.inspect(relockCheaply(BackupData())) as ImportBackupUseCase.Inspection.Locked

        val unlocked = importer.unlock(
            locked.envelope.copy(container = 99),
            passphrase.toCharArray(),
        )

        assertEquals(
            ImportBackupUseCase.Inspection.Invalid(ImportBackupUseCase.Error.UNSUPPORTED_CONTAINER),
            unlocked,
        )
    }

    @Test
    fun `an unencrypted file keeps importing exactly as before`() = runTest {
        val destination = FakeBackupRepository(fullyPopulatedBackupFile().data)
        val importer = ImportBackupUseCase(destination, FakeSettingsRepository())
        val json = """
            {"format": "saldo-backup", "version": 1, "exportedAtEpochMilli": 5000, "data": {}}
        """.trimIndent()

        val inspection = importer.inspect(json)

        assertTrue(inspection is ImportBackupUseCase.Inspection.Valid)
        assertFalse((inspection as ImportBackupUseCase.Inspection.Valid).summary.isEncrypted)
        assertNull(inspection.file.data.settings)
    }

    /**
     * An encrypted export of [data] through the real use case, hence at the
     * shipped work factor: this is the path a user takes. The tests that only
     * need *a* container reseal a plain export at the cheapest accepted factor
     * ([relockCheaply]), so the suite pays that half second once.
     */
    private suspend fun exportEncrypted(data: BackupData): ExportBackupUseCase.Export =
        ExportBackupUseCase(
            FakeBackupRepository(data),
            FakeSettingsRepository(fullyPopulatedSettings()),
            clock,
        ).invoke(appVersion = "2.0.0", passphrase = passphrase.toCharArray())

    /** A container over [data]'s plain export, cheap enough to seal in a loop. */
    private suspend fun relockCheaply(data: BackupData): String {
        val plain = ExportBackupUseCase(
            FakeBackupRepository(data),
            FakeSettingsRepository(fullyPopulatedSettings()),
            clock,
        ).invoke(appVersion = "2.0.0")
        return BackupCodec.encode(
            BackupCrypto.seal(plain.content, passphrase.toCharArray(), CHEAP_ITERATIONS),
        )
    }

    private companion object {
        /** The floor of the accepted range: still a valid container, far faster. */
        const val CHEAP_ITERATIONS = 100_000
    }
}
