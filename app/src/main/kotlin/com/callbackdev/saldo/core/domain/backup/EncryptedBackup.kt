package com.callbackdev.saldo.core.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The encrypted container (Fase 22, ADR 44): it **wraps** the JSON document of
 * [BackupFile], it does not replace it. Inside the ciphertext travels exactly
 * the file that an unencrypted export would have produced, byte for byte, so
 * the schema of [BackupData] and its [BackupFile.CURRENT_VERSION] are untouched
 * by encryption and there is still one export/restore code path (ADR 5).
 *
 * The container is itself a small JSON object with the payload in Base64: the
 * file keeps the `.json` extension and its mime type, and the restore path
 * recognises it from its [format] marker exactly as it recognises a plain
 * backup - from the content, never from the extension. What a text editor shows
 * is only the header: what kind of file this is, how the key was derived, and an
 * opaque payload.
 *
 * The header is versioned separately from the JSON schema ([CONTAINER_VERSION]
 * against [BackupFile.CURRENT_VERSION]): the two evolve for different reasons,
 * and a change of cipher must not look like a change of data model.
 */
@Serializable
data class EncryptedBackup(
    /** Discriminator that identifies the container; always [FORMAT]. */
    val format: String = FORMAT,
    /** Version of the container itself, not of the data inside it. */
    val container: Int = CONTAINER_VERSION,
    /** Key derivation function, recorded so the file states how it was made. */
    val kdf: String = KDF,
    /** Cipher of the payload, for the same reason as [kdf]. */
    val cipher: String = CIPHER,
    /**
     * PBKDF2 iteration count that produced the key. It travels with the file so
     * the work factor can be raised later without orphaning existing backups:
     * an old file keeps decrypting with its own count (same reasoning as the
     * PIN's stored iterations, ADR 39).
     */
    val iterations: Int,
    @SerialName("salt")
    val saltBase64: String,
    @SerialName("iv")
    val ivBase64: String,
    /** AES-GCM output (ciphertext and authentication tag) of the JSON document. */
    @SerialName("payload")
    val payloadBase64: String,
) {
    companion object {
        const val FORMAT = "saldo-backup-encrypted"
        const val CONTAINER_VERSION = 1
        const val KDF = "PBKDF2WithHmacSHA256"
        const val CIPHER = "AES/GCM/NoPadding"
    }
}

/** Why an encrypted container could not be opened, for an honest message. */
sealed class BackupCryptoException(message: String) : Exception(message) {

    /**
     * GCM authentication failed: with a well-formed container this means the
     * passphrase is wrong. It can also mean tampered bytes, which is exactly
     * what authenticated encryption cannot tell apart from a wrong key - and
     * "wrong passphrase" is the honest first guess to show the user.
     */
    class WrongPassphrase(cause: Throwable? = null) : BackupCryptoException(
        "Passphrase does not open this container${cause?.let { ": ${it.message}" }.orEmpty()}",
    )

    /** The container declares a version this app does not know how to open. */
    class UnsupportedContainer(val version: Int) : BackupCryptoException(
        "Container version $version is newer than ${EncryptedBackup.CONTAINER_VERSION}",
    )

    /** Header or payload are malformed (bad Base64, missing bytes, wrong sizes). */
    class Corrupted(cause: Throwable? = null) : BackupCryptoException(
        "Encrypted backup cannot be read${cause?.let { ": ${it.message}" }.orEmpty()}",
    )
}
