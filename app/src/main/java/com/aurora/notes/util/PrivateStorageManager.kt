package com.aurora.notes.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aurora.notes.model.ChecklistItem
import com.aurora.notes.model.Note
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PrivateStorageStats(
    val directoryPath: String = "",
    val syncedFileCount: Int = 0,
    val orphanedFileCount: Int = 0,
    val totalBytes: Long = 0L,
    val isEncrypted: Boolean = false
)

data class StorageCleanupReport(
    val orphanedFilesRemoved: Int = 0,
    val tempFilesRemoved: Int = 0,
    val bytesFreed: Long = 0L
)

data class WordLetterCount(
    val words: Int,
    val letters: Int
)

object PrivateStorageManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "aurora_notes_room_storage_aes_key_v1"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val ENC_PREFIX = "ENCv1:"
    private const val PRIVATE_NOTES_DIR_NAME = "aurora_private_notes"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun isEncryptedValue(value: String): Boolean {
        return value.startsWith(ENC_PREFIX)
    }

    fun encryptText(plainText: String): String {
        if (plainText.isEmpty() || isEncryptedValue(plainText)) return plainText
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            LocalLogger.warn("CRYPTO", "AES-GCM encryption fallback: ${e.localizedMessage ?: "error"}")
            plainText
        }
    }

    fun decryptText(rawText: String): String {
        if (rawText.isEmpty() || !isEncryptedValue(rawText)) return rawText
        return try {
            val encodedPayload = rawText.removePrefix(ENC_PREFIX)
            val combined = Base64.decode(encodedPayload, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_BYTES) return rawText
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            LocalLogger.warn("CRYPTO", "AES-GCM decryption warning: ${e.localizedMessage ?: "error"}")
            rawText
        }
    }

    fun encryptNoteForRoom(note: Note, encryptionEnabled: Boolean): Note {
        val plain = decryptNoteFromRoom(note)
        if (!encryptionEnabled) return plain
        return plain.copy(
            title = encryptText(plain.title),
            content = encryptText(plain.content),
            styleSpansJson = encryptText(plain.styleSpansJson),
            checklist = plain.checklist.map { item ->
                item.copy(text = encryptText(item.text))
            },
            tags = plain.tags.map { tag -> encryptText(tag) },
            category = encryptText(plain.category)
        )
    }

    fun decryptNoteFromRoom(note: Note): Note {
        val needsDecrypt = isEncryptedValue(note.title) ||
            isEncryptedValue(note.content) ||
            isEncryptedValue(note.styleSpansJson) ||
            isEncryptedValue(note.category) ||
            note.tags.any { isEncryptedValue(it) } ||
            note.checklist.any { isEncryptedValue(it.text) }

        if (!needsDecrypt) return note

        return note.copy(
            title = decryptText(note.title),
            content = decryptText(note.content),
            styleSpansJson = decryptText(note.styleSpansJson),
            checklist = note.checklist.map { item ->
                ChecklistItem(
                    id = item.id,
                    text = decryptText(item.text),
                    completed = item.completed
                )
            },
            tags = note.tags.map { tag -> decryptText(tag) },
            category = decryptText(note.category)
        )
    }

    /**
     * Resolves the app's private external storage directory on the device
     * (`/storage/emulated/0/Android/data/com.aurora.notes/files/aurora_private_notes`),
     * falling back to internal app files storage if external storage is unmounted.
     */
    fun getPrivateExternalNotesDir(context: Context): File {
        val externalDir = try {
            context.getExternalFilesDir(PRIVATE_NOTES_DIR_NAME)
        } catch (_: Exception) {
            null
        }
        val targetDir = externalDir ?: File(context.filesDir, PRIVATE_NOTES_DIR_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun getInternalFallbackNotesDir(context: Context): File {
        val dir = File(context.filesDir, PRIVATE_NOTES_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeNoteId(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun fileNameForNoteId(id: String): String {
        return "note_${sanitizeNoteId(id)}.json"
    }

    private fun extractNoteIdFromFileName(name: String): String? {
        if (!name.startsWith("note_")) return null
        return when {
            name.endsWith(".json") -> name.removePrefix("note_").removeSuffix(".json")
            name.endsWith(".md") -> name.removePrefix("note_").removeSuffix(".md")
            else -> null
        }
    }

    fun writeNoteToPrivateStorage(
        context: Context,
        note: Note,
        encryptFileContent: Boolean
    ) {
        if (note.isDeleted) {
            deleteNoteFromPrivateStorage(context, note.id)
            return
        }
        try {
            val dir = getPrivateExternalNotesDir(context)
            val plainNote = decryptNoteFromRoom(note)
            val jsonPayload = gson.toJson(plainNote)
            val diskContent = if (encryptFileContent) {
                encryptText(jsonPayload)
            } else {
                jsonPayload
            }

            val finalFile = File(dir, fileNameForNoteId(plainNote.id))
            val tempFile = File(dir, "${fileNameForNoteId(plainNote.id)}.tmp")
            tempFile.writeText(diskContent, StandardCharsets.UTF_8)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                finalFile.writeText(diskContent, StandardCharsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            LocalLogger.error(
                "STORAGE",
                "Failed writing note ${note.id} to external private storage: ${e.localizedMessage ?: "I/O error"}"
            )
        }
    }

    fun deleteNoteFromPrivateStorage(context: Context, noteId: String) {
        val targetName = fileNameForNoteId(noteId)
        val dirs = listOf(getPrivateExternalNotesDir(context), getInternalFallbackNotesDir(context)).distinctBy { it.absolutePath }
        dirs.forEach { dir ->
            try {
                val f = File(dir, targetName)
                if (f.exists()) f.delete()
                val tmp = File(dir, "$targetName.tmp")
                if (tmp.exists()) tmp.delete()
            } catch (_: Exception) {
            }
        }
    }

    fun syncAllNotesToPrivateStorage(
        context: Context,
        allNotes: List<Note>,
        encryptFileContent: Boolean
    ): StorageCleanupReport {
        val activeNonDeleted = allNotes.filter { !it.isDeleted }
        activeNonDeleted.forEach { note ->
            writeNoteToPrivateStorage(context, note, encryptFileContent)
        }
        val validIds = activeNonDeleted.map { sanitizeNoteId(it.id) }.toSet()
        return cleanupOrphanedFiles(context, validIds)
    }

    /**
     * Scans private external and internal storage directories for:
     * 1. Orphaned note files whose note ID no longer exists in the Room database (or was deleted)
     * 2. Incomplete `.tmp` or 0-byte corrupted files
     * 3. Stale temporary share/update files in externalCacheDir and cacheDir
     */
    fun cleanupOrphanedFiles(
        context: Context,
        validNoteIds: Set<String>
    ): StorageCleanupReport {
        val normalizedValidIds = validNoteIds.map { sanitizeNoteId(it) }.toSet()
        var orphanedRemoved = 0
        var tempRemoved = 0
        var bytesFreed = 0L

        val storageDirs = listOf(
            getPrivateExternalNotesDir(context),
            getInternalFallbackNotesDir(context)
        ).distinctBy { it.absolutePath }

        storageDirs.forEach { dir ->
            val files = dir.listFiles() ?: emptyArray()
            files.forEach { file ->
                if (file.isFile) {
                    val len = file.length()
                    val name = file.name
                    val extractedId = extractNoteIdFromFileName(name)
                    val isTempFile = name.endsWith(".tmp") || name.endsWith(".bak")
                    val isZeroByte = len == 0L
                    val isOrphanedNote = extractedId == null || !normalizedValidIds.contains(extractedId)

                    if (isTempFile || isZeroByte || isOrphanedNote) {
                        if (file.delete()) {
                            if (isTempFile) tempRemoved++ else orphanedRemoved++
                            bytesFreed += len
                        }
                    }
                }
            }
        }

        // Also automatically clean up stale shared_notes & updates cache files in externalCacheDir & cacheDir
        val cacheDirs = listOfNotNull(
            context.externalCacheDir?.let { File(it, "shared_notes") },
            File(context.cacheDir, "shared_notes"),
            context.externalCacheDir?.let { File(it, "updates") },
            File(context.cacheDir, "updates")
        ).distinctBy { it.absolutePath }

        val now = System.currentTimeMillis()
        val staleThresholdMs = 10 * 60 * 1000L // 10 minutes automatic TTL for temp share/update artifacts
        cacheDirs.forEach { cacheDir ->
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val age = now - file.lastModified()
                        val isTempArtifact = file.name.endsWith(".tmp") || file.name.endsWith(".bak")
                        if (file.length() == 0L || isTempArtifact || age >= staleThresholdMs || normalizedValidIds.isEmpty()) {
                            val len = file.length()
                            if (file.delete()) {
                                tempRemoved++
                                bytesFreed += len
                            }
                        }
                    }
                }
            }
        }

        return StorageCleanupReport(
            orphanedFilesRemoved = orphanedRemoved,
            tempFilesRemoved = tempRemoved,
            bytesFreed = bytesFreed
        )
    }

    fun computeStorageStats(
        context: Context,
        validNoteIds: Set<String>,
        isEncrypted: Boolean
    ): PrivateStorageStats {
        val normalizedValidIds = validNoteIds.map { sanitizeNoteId(it) }.toSet()
        val dir = getPrivateExternalNotesDir(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()

        var syncedCount = 0
        var orphanedCount = 0
        var totalBytes = 0L

        files.forEach { file ->
            totalBytes += file.length()
            val extractedId = extractNoteIdFromFileName(file.name)
            if (extractedId != null && normalizedValidIds.contains(extractedId) && !file.name.endsWith(".tmp")) {
                syncedCount++
            } else {
                orphanedCount++
            }
        }

        return PrivateStorageStats(
            directoryPath = dir.absolutePath,
            syncedFileCount = syncedCount,
            orphanedFileCount = orphanedCount,
            totalBytes = totalBytes,
            isEncrypted = isEncrypted
        )
    }

    /**
     * Computes live word and letter counts for the Note Editor header.
     * Strips inline checkbox (`☐ `, `☑ `) and bullet (`• `) prefixes so formatting markers
     * are not counted as words or letters.
     */
    fun computeWordAndLetterCounts(title: String, body: String): WordLetterCount {
        val sanitizedBody = body
            .replace("☐ ", " ")
            .replace("☑ ", " ")
            .replace("• ", " ")
            .replace("☐", " ")
            .replace("☑", " ")

        val combined = buildString {
            if (title.isNotBlank()) {
                append(title.trim())
                append(' ')
            }
            if (sanitizedBody.isNotBlank()) {
                append(sanitizedBody.trim())
            }
        }.trim()

        if (combined.isEmpty()) {
            return WordLetterCount(words = 0, letters = 0)
        }

        val words = combined
            .split(Regex("\\s+"))
            .count { token -> token.any { ch -> ch.isLetterOrDigit() } }

        val letters = combined.count { ch -> !ch.isWhitespace() }

        return WordLetterCount(words = words, letters = letters)
    }
}
