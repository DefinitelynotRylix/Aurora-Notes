package com.aurora.notes.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aurora.notes.model.ChecklistItem
import com.aurora.notes.model.Note
import java.io.ByteArrayOutputStream
import java.util.UUID

object MarkdownNoteParser {

    private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024 // 2 MB safety cap
    private val H1_REGEX = Regex("^#\\s+(.+)$")
    private val META_LINE_REGEX = Regex("^>\\s*\\*\\*(Category|Tags|Updated):", RegexOption.IGNORE_CASE)
    private val CATEGORY_META_REGEX = Regex("\\*\\*Category:\\*\\*\\s*([^|]+)", RegexOption.IGNORE_CASE)
    private val TAGS_META_REGEX = Regex("\\*\\*Tags:\\*\\*\\s*([^|]+)", RegexOption.IGNORE_CASE)
    private val HASHTAG_REGEX = Regex("#([^\\s#|]+)")
    private val CHECKLIST_HEADER_REGEX = Regex("^###\\s+Checklist\\s*$", RegexOption.IGNORE_CASE)
    private val TASK_LINE_REGEX = Regex("^\\s*[-*]\\s+\\[([ xX])]\\s?(.*)$")
    private val BULLET_LINE_REGEX = Regex("^\\s*[-*]\\s+(.*)$")
    private val FILE_EXT_REGEX = Regex("\\.(md|markdown|txt)$", RegexOption.IGNORE_CASE)

    /**
     * Reads a `.md` (or plain-text) file from a File Manager `content://` or `file://` URI
     * and parses it into an editable Aurora [Note].
     */
    fun readNoteFromUri(context: Context, uri: Uri): Note? {
        val displayName = resolveDisplayName(context, uri)
        val rawText = readUtf8TextBounded(context, uri) ?: return null
        return parseMarkdownToNote(rawText, displayName)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to lastPathSegment
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun readUtf8TextBounded(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                val out = ByteArrayOutputStream()
                var totalRead = 0
                while (totalRead < MAX_IMPORT_BYTES) {
                    val maxStep = minOf(buffer.size, MAX_IMPORT_BYTES - totalRead)
                    val read = input.read(buffer, 0, maxStep)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    totalRead += read
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseMarkdownToNote(rawMarkdown: String, fallbackFileName: String? = null): Note {
        val normalized = rawMarkdown.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")

        var title = ""
        var category = ""
        val tags = mutableListOf<String>()
        val checklist = mutableListOf<ChecklistItem>()
        val bodyLines = mutableListOf<String>()

        var cursor = 0
        while (cursor < lines.size && lines[cursor].isBlank()) {
            cursor++
        }

        // Leading # Title heading
        if (cursor < lines.size) {
            val h1 = H1_REGEX.matchEntire(lines[cursor].trim())
            if (h1 != null) {
                title = h1.groupValues[1].trim()
                cursor++
                while (cursor < lines.size && lines[cursor].isBlank()) {
                    cursor++
                }
            }
        }

        // Aurora Notes export metadata line: > **Category:** ... | **Tags:** #a #b | **Updated:** ...
        if (cursor < lines.size && META_LINE_REGEX.containsMatchIn(lines[cursor].trim())) {
            val metaLine = lines[cursor].trim().removePrefix(">").trim()
            CATEGORY_META_REGEX.find(metaLine)?.let { match ->
                val cat = match.groupValues[1].trim()
                if (cat.isNotBlank() && !cat.equals("All", ignoreCase = true)) {
                    category = cat
                }
            }
            TAGS_META_REGEX.find(metaLine)?.let { match ->
                HASHTAG_REGEX.findAll(match.groupValues[1]).forEach { tagMatch ->
                    val clean = tagMatch.groupValues[1].trim()
                    if (clean.isNotBlank() && tags.none { it.equals(clean, ignoreCase = true) }) {
                        tags.add(clean)
                    }
                }
            }
            cursor++
            while (cursor < lines.size && lines[cursor].isBlank()) {
                cursor++
            }
        }

        var inChecklistSection = false
        for (i in cursor until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (CHECKLIST_HEADER_REGEX.matches(trimmed)) {
                inChecklistSection = true
                continue
            }

            val taskMatch = TASK_LINE_REGEX.matchEntire(line)
            if (taskMatch != null) {
                val completed = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                val itemText = taskMatch.groupValues[2]
                if (inChecklistSection) {
                    checklist.add(
                        ChecklistItem(
                            id = UUID.randomUUID().toString(),
                            text = itemText,
                            completed = completed
                        )
                    )
                } else {
                    bodyLines.add("${if (completed) "☑" else "☐"} $itemText")
                }
                continue
            }

            if (inChecklistSection && trimmed.isEmpty()) {
                continue
            }
            inChecklistSection = false

            val bulletMatch = BULLET_LINE_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                bodyLines.add("• ${bulletMatch.groupValues[1]}")
                continue
            }

            bodyLines.add(line)
        }

        if (title.isBlank() && !fallbackFileName.isNullOrBlank()) {
            title = fallbackFileName
                .replace(FILE_EXT_REGEX, "")
                .replace(Regex("_+"), " ")
                .trim()
        }

        val finalTitle = title.ifBlank { "Imported Note" }
        val finalCategory = category.ifBlank { tags.firstOrNull().orEmpty() }
        val finalContent = bodyLines.joinToString("\n").trim()
        val now = System.currentTimeMillis()

        return Note(
            id = UUID.randomUUID().toString(),
            title = finalTitle,
            content = finalContent,
            checklist = checklist,
            tags = tags,
            category = finalCategory,
            isPinned = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
    }
}
