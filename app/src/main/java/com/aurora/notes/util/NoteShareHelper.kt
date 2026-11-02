package com.aurora.notes.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aurora.notes.model.Note
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShareNoteFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    PLAIN_TEXT("Plain Text (.txt)", "txt", "text/plain")
}

object NoteShareHelper {

    private fun stripHtmlTags(raw: String): String {
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>|</div>|</li>|</h1>|</h2>|</h3>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    fun formatSingleNote(note: Note, format: ShareNoteFormat): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val updatedStr = dateFormat.format(Date(note.updatedAt))
        val title = note.title.trim().ifBlank { "Untitled Note" }
        val cleanBody = stripHtmlTags(note.content)
        val tagsList = note.tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotBlank() }

        return when (format) {
            ShareNoteFormat.MARKDOWN -> {
                buildString {
                    appendLine("# $title")
                    appendLine()
                    val metaParts = mutableListOf<String>()
                    if (note.category.isNotBlank()) {
                        metaParts.add("**Category:** ${note.category}")
                    }
                    if (tagsList.isNotEmpty()) {
                        metaParts.add("**Tags:** ${tagsList.joinToString(" ") { "#$it" }}")
                    }
                    metaParts.add("**Updated:** $updatedStr")
                    appendLine("> ${metaParts.joinToString(" | ")}")
                    appendLine()

                    if (cleanBody.isNotBlank()) {
                        appendLine(cleanBody)
                        appendLine()
                    }

                    if (note.checklist.isNotEmpty()) {
                        appendLine("### Checklist")
                        note.checklist.forEach { item ->
                            val mark = if (item.completed) "x" else " "
                            appendLine("- [$mark] ${item.text}")
                        }
                        appendLine()
                    }
                }.trimEnd() + "\n"
            }

            ShareNoteFormat.PLAIN_TEXT -> {
                buildString {
                    appendLine(title.uppercase(Locale.getDefault()))
                    appendLine("=".repeat(title.length.coerceIn(8, 40)))
                    if (note.category.isNotBlank()) {
                        appendLine("Category: ${note.category}")
                    }
                    if (tagsList.isNotEmpty()) {
                        appendLine("Tags: ${tagsList.joinToString(", ") { "#$it" }}")
                    }
                    appendLine("Updated: $updatedStr")
                    appendLine()

                    if (cleanBody.isNotBlank()) {
                        appendLine(cleanBody)
                        appendLine()
                    }

                    if (note.checklist.isNotEmpty()) {
                        appendLine("Checklist:")
                        note.checklist.forEach { item ->
                            val mark = if (item.completed) "[x]" else "[ ]"
                            appendLine("$mark ${item.text}")
                        }
                        appendLine()
                    }
                }.trimEnd() + "\n"
            }
        }
    }

    fun formatNotes(notes: List<Note>, format: ShareNoteFormat): String {
        if (notes.isEmpty()) return ""
        if (notes.size == 1) return formatSingleNote(notes.first(), format)

        val separator = when (format) {
            ShareNoteFormat.MARKDOWN -> "\n\n---\n\n"
            ShareNoteFormat.PLAIN_TEXT -> "\n\n" + "-".repeat(40) + "\n\n"
        }
        return notes.joinToString(separator) { formatSingleNote(it, format).trimEnd() } + "\n"
    }

    fun buildSuggestedFileName(notes: List<Note>, format: ShareNoteFormat): String {
        val baseName = if (notes.size == 1) {
            val rawTitle = notes.first().title.trim().ifBlank { "Untitled_Note" }
            rawTitle
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .take(48)
                .ifBlank { "Aurora_Note" }
        } else {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            "Aurora_Notes_${notes.size}_$dateStr"
        }
        return "$baseName.${format.extension}"
    }

    fun shareToAppsAndContacts(
        context: Context,
        notes: List<Note>,
        format: ShareNoteFormat,
        asFileAttachment: Boolean = false
    ) {
        if (notes.isEmpty()) return
        val formattedText = formatNotes(notes, format)
        val fileName = buildSuggestedFileName(notes, format)
        val subject = if (notes.size == 1) {
            notes.first().title.ifBlank { "Aurora Note" }
        } else {
            "${notes.size} Aurora Notes"
        }

        try {
            if (asFileAttachment) {
                val sharedDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
                val file = File(sharedDir, fileName)
                file.writeText(formattedText, Charsets.UTF_8)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, formattedText)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, "Share ${format.label} File")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, formattedText)
                }
                val chooser = Intent.createChooser(sendIntent, "Share Note to Apps & Contacts")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
            LocalLogger.security("SHARE", "Shared ${notes.size} note(s) in ${format.name} format")
        } catch (e: Exception) {
            LocalLogger.error("SHARE", "Failed to share note(s): ${e.localizedMessage}")
            Toast.makeText(context, "Unable to open share sheet: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun writeNoteToChosenUri(
        context: Context,
        uri: Uri,
        notes: List<Note>,
        format: ShareNoteFormat
    ): Boolean {
        return try {
            val content = formatNotes(notes, format)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: return false
            LocalLogger.security("SHARE", "Saved ${notes.size} note(s) as .${format.extension} to chosen folder")
            true
        } catch (e: Exception) {
            LocalLogger.error("SHARE", "Failed saving note to chosen folder: ${e.localizedMessage}")
            false
        }
    }
}
