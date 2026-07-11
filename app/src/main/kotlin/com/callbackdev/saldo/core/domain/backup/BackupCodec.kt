package com.callbackdev.saldo.core.domain.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Why a file could not be read back as a backup, for a precise user message. */
sealed class BackupDecodeException(message: String) : Exception(message) {

    /** The file parses as JSON but is not a Saldo backup at all. */
    class NotABackup : BackupDecodeException("Missing or wrong format marker")

    /** The file was produced by a newer app; [version] is what it declares. */
    class UnsupportedVersion(val version: Int) :
        BackupDecodeException("Backup version $version is newer than ${BackupFile.CURRENT_VERSION}")

    /** The file is not valid JSON or its payload does not match the schema. */
    class Corrupted(cause: Throwable? = null) :
        BackupDecodeException("Backup file cannot be parsed${cause?.let { ": ${it.message}" }.orEmpty()}")
}

/**
 * Encodes and decodes [BackupFile] to/from its JSON document.
 *
 * Encoding is pretty-printed on purpose: a backup is the user's own data and
 * should be inspectable with any text editor (privacy-first means no opaque
 * blobs). Decoding validates the format marker and the schema version *before*
 * deserializing the payload, so foreign files and future formats fail with a
 * specific [BackupDecodeException] instead of a generic parse error.
 */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Parses [content] into a [BackupFile].
     *
     * @throws BackupDecodeException.NotABackup when the JSON lacks the Saldo marker.
     * @throws BackupDecodeException.UnsupportedVersion when the declared schema
     *   version is newer than this app understands.
     * @throws BackupDecodeException.Corrupted when the content is not JSON or the
     *   payload does not match the schema.
     */
    @Suppress("ThrowsCount") // Typed failures are this function's contract.
    fun decode(content: String): BackupFile {
        val root = parseObject(content)
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        if (format != BackupFile.FORMAT) throw BackupDecodeException.NotABackup()
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: throw BackupDecodeException.Corrupted()
        if (version > BackupFile.CURRENT_VERSION) {
            throw BackupDecodeException.UnsupportedVersion(version)
        }
        return try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (error: SerializationException) {
            throw BackupDecodeException.Corrupted(error)
        } catch (error: IllegalArgumentException) {
            throw BackupDecodeException.Corrupted(error)
        }
    }

    private fun parseObject(content: String): JsonObject =
        try {
            json.parseToJsonElement(content) as? JsonObject
                ?: throw BackupDecodeException.NotABackup()
        } catch (error: SerializationException) {
            throw BackupDecodeException.Corrupted(error)
        }
}
