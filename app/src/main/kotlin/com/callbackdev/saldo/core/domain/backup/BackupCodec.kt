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

/** What a picked file turned out to be, once its marker has been read. */
sealed interface BackupContent {

    /** A readable, validated backup document. */
    data class Plain(val file: BackupFile) : BackupContent

    /** An encrypted container: the data is there, the passphrase is not. */
    data class Encrypted(val envelope: EncryptedBackup) : BackupContent
}

/**
 * Encodes and decodes [BackupFile] to/from its JSON document, and recognises the
 * encrypted container that can wrap it (Fase 22).
 *
 * Encoding is pretty-printed on purpose: an unencrypted backup is the user's own
 * data and should be inspectable with any text editor (privacy-first means no
 * opaque blobs). Decoding validates the format marker and the schema version
 * *before* deserializing the payload, so foreign files and future formats fail
 * with a specific [BackupDecodeException] instead of a generic parse error. A
 * decoded payload is then semantically validated (enum names, currency codes,
 * transfer invariants: see [validatePayload]) so that a file the read path
 * cannot trust is refused at inspect time, never after the restore has replaced
 * the data.
 *
 * Both kinds of file are told apart by their `format` marker inside the JSON, so
 * recognition is by content and an extension is never trusted.
 */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun encode(envelope: EncryptedBackup): String =
        json.encodeToString(EncryptedBackup.serializer(), envelope)

    /**
     * Reads [content] far enough to say what it is: a validated backup or a
     * container waiting for a passphrase.
     *
     * @throws BackupDecodeException.NotABackup when the JSON carries neither marker.
     * @throws BackupDecodeException.UnsupportedVersion when the declared schema
     *   version is newer than this app understands.
     * @throws BackupDecodeException.Corrupted when the content is not JSON or the
     *   payload does not match the schema.
     */
    fun read(content: String): BackupContent {
        val root = parseObject(content)
        return when (root["format"]?.jsonPrimitive?.contentOrNull) {
            BackupFile.FORMAT -> BackupContent.Plain(root.decodeBackupFile())
            EncryptedBackup.FORMAT -> BackupContent.Encrypted(root.decodeEnvelope())
            else -> throw BackupDecodeException.NotABackup()
        }
    }

    /**
     * Parses [content] as a plain backup document. Used on the JSON that comes
     * out of a container too: what was sealed is the very same document.
     *
     * @throws BackupDecodeException as documented on [read]; an encrypted
     *   container reaching here is [BackupDecodeException.NotABackup], because at
     *   this point the caller already expects the decrypted document.
     */
    fun decode(content: String): BackupFile =
        when (val parsed = read(content)) {
            is BackupContent.Plain -> parsed.file
            is BackupContent.Encrypted -> throw BackupDecodeException.NotABackup()
        }

    private fun JsonObject.decodeBackupFile(): BackupFile {
        val version = get("version")?.jsonPrimitive?.intOrNull
            ?: throw BackupDecodeException.Corrupted()
        if (version > BackupFile.CURRENT_VERSION) {
            throw BackupDecodeException.UnsupportedVersion(version)
        }
        // The semantic validation runs inside the same guard as the parsing: a
        // payload that decodes but cannot be trusted is just as corrupted.
        return orCorrupted {
            json.decodeFromJsonElement(BackupFile.serializer(), this)
                .also { it.data.validatePayload() }
        }
    }

    /**
     * The container's own version is *not* checked here: an unopenable container
     * is a matter for [BackupCrypto.open], which is where the user finds out
     * with the passphrase dialog in front of them.
     */
    private fun JsonObject.decodeEnvelope(): EncryptedBackup =
        orCorrupted { json.decodeFromJsonElement(EncryptedBackup.serializer(), this) }

    private inline fun <T> orCorrupted(block: () -> T): T =
        try {
            block()
        } catch (error: SerializationException) {
            throw BackupDecodeException.Corrupted(error)
        } catch (error: IllegalArgumentException) {
            // Not only a parse failure: `require` and `valueOf` inside
            // [validatePayload] land here too, and mean the same thing.
            throw BackupDecodeException.Corrupted(error)
        }

    private fun parseObject(content: String): JsonObject =
        try {
            json.parseToJsonElement(content) as? JsonObject
                ?: throw BackupDecodeException.NotABackup()
        } catch (error: SerializationException) {
            throw BackupDecodeException.Corrupted(error)
        }
}
