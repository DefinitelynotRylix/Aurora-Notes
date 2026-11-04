package com.aurora.notes.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aurora.notes.model.Note
import com.aurora.notes.model.NoteImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class ShareNoteFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    MARKDOWN("Markdown (.md)", "md", "text/markdown"),
    NOTE_IMAGE("Note Image (.png)", "png", "image/png")
}

object NoteShareHelper {

    const val REQUEST_CODE_SAVE_TO_FOLDER = 1042

    private var pendingSafNotes: List<Note> = emptyList()
    private var pendingSafFormat: ShareNoteFormat = ShareNoteFormat.MARKDOWN
    private var pendingSafFileName: String = "Aurora_Note.md"

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

    private fun replaceImageMarkersWithMarkdown(
        rawBody: String,
        images: List<NoteImage>,
        embedBase64DataUri: Boolean
    ): String {
        val imageById = images.associateBy { it.id }
        val referencedIds = mutableSetOf<String>()
        var imgCounter = 0

        val markerRegex = Regex("\\[\\[AURORA_IMG:([^\\]]+)\\]\\]")
        val replaced = markerRegex.replace(rawBody) { matchResult ->
            val imgId = matchResult.groupValues[1]
            referencedIds.add(imgId)
            imgCounter++
            val imgItem = imageById[imgId]
            if (imgItem != null && embedBase64DataUri) {
                val bytes = PrivateStorageManager.loadDecryptedNoteImageBytes(imgItem.uri)
                if (bytes != null && bytes.isNotEmpty()) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    "\n![Note Image $imgCounter](data:image/png;base64,$b64)\n"
                } else {
                    "\n![Note Image $imgCounter](aurora_image_$imgCounter.png)\n"
                }
            } else {
                "\n![Note Image $imgCounter](aurora_image_$imgCounter.png)\n"
            }
        }

        // Append any unreferenced images at the end so no note image is ever lost in Markdown export
        val unreferenced = images.filter { !referencedIds.contains(it.id) }
        if (unreferenced.isEmpty()) return replaced

        val extraSb = StringBuilder(replaced.trimEnd())
        for (imgItem in unreferenced) {
            imgCounter++
            extraSb.append("\n\n")
            if (embedBase64DataUri) {
                val bytes = PrivateStorageManager.loadDecryptedNoteImageBytes(imgItem.uri)
                if (bytes != null && bytes.isNotEmpty()) {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    extraSb.append("![Note Image $imgCounter](data:image/png;base64,$b64)")
                } else {
                    extraSb.append("![Note Image $imgCounter](aurora_image_$imgCounter.png)")
                }
            } else {
                extraSb.append("![Note Image $imgCounter](aurora_image_$imgCounter.png)")
            }
        }
        return extraSb.toString()
    }

    private fun convertLinesToMarkdown(bodyWithImages: String): String {
        return bodyWithImages.lines().joinToString("\n") { line ->
            when {
                line.startsWith("☐ • ") -> "- [ ] ${line.removePrefix("☐ • ")}"
                line.startsWith("☑ • ") -> "- [x] ${line.removePrefix("☑ • ")}"
                line.startsWith("☐ ") -> "- [ ] ${line.removePrefix("☐ ")}"
                line.startsWith("☑ ") -> "- [x] ${line.removePrefix("☑ ")}"
                line.startsWith("• ") -> "- ${line.removePrefix("• ")}"
                else -> line
            }
        }
    }

    fun formatSingleNote(
        rawNote: Note,
        format: ShareNoteFormat,
        embedBase64Images: Boolean = false
    ): String {
        val note = PrivateStorageManager.decryptNoteFromRoom(rawNote)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val updatedStr = dateFormat.format(Date(note.updatedAt))
        val title = note.title.trim().ifBlank { "Untitled Note" }
        val rawBodyWithImages = replaceImageMarkersWithMarkdown(
            rawBody = stripHtmlTags(note.content),
            images = note.images,
            embedBase64DataUri = embedBase64Images
        )
        val cleanBody = convertLinesToMarkdown(rawBodyWithImages)
        val tagsList = note.tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotBlank() }

        return buildString {
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

    fun formatNotes(
        notes: List<Note>,
        format: ShareNoteFormat,
        embedBase64Images: Boolean = false
    ): String {
        if (notes.isEmpty()) return ""
        val plainNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        if (plainNotes.size == 1) return formatSingleNote(plainNotes.first(), format, embedBase64Images)

        val separator = "\n\n---\n\n"
        return plainNotes.joinToString(separator) {
            formatSingleNote(it, format, embedBase64Images).trimEnd()
        } + "\n"
    }

    /**
     * Renders a full-length viewable bitmap of the note(s), including title, tags, date,
     * formatted text, checkboxes, bullets, and decrypted embedded images in their exact order.
     */
    fun renderNotesToFullLengthBitmap(notes: List<Note>): Bitmap {
        val plainNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        val canvasWidth = 1080
        val outerPadding = 48
        val cardPadding = 44
        val contentWidth = canvasWidth - (outerPadding * 2) - (cardPadding * 2)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D0B14")
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161224")
        }
        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4C2E85")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2A243D")
            strokeWidth = 2f
        }
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#24193C")
        }
        val tagStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D28D9")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F3FF")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 23f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EDE9FE")
            textSize = 30f
        }
        val checkedBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 30f
            flags = Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C3AED")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun buildStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
            val safeWidth = max(width, 120)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(6f, 1.12f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.12f, 6f, false)
            }
        }

        // First pass: measure exact full height required
        val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US)
        var totalHeight = outerPadding

        data class PreparedLine(
            val hasCheckbox: Boolean,
            val isChecked: Boolean,
            val hasBullet: Boolean,
            val layout: StaticLayout
        )

        data class PreparedImage(
            val bitmap: Bitmap,
            val drawWidth: Int,
            val drawHeight: Int
        )

        sealed class PreparedBlock {
            data class LinesBlock(val lines: List<PreparedLine>) : PreparedBlock()
            data class ImageBlock(val image: PreparedImage) : PreparedBlock()
        }

        data class PreparedNoteCard(
            val titleLayout: StaticLayout,
            val tagsText: String,
            val dateText: String,
            val blocks: List<PreparedBlock>,
            val cardHeight: Int
        )

        val preparedCards = mutableListOf<PreparedNoteCard>()
        val markerRegex = Regex("\\[\\[AURORA_IMG:([^\\]]+)\\]\\]")

        for (note in plainNotes) {
            var cardInnerHeight = cardPadding
            val titleStr = note.title.trim().ifBlank { "Untitled Note" }
            val titleLayout = buildStaticLayout(titleStr, titlePaint, contentWidth)
            cardInnerHeight += titleLayout.height + 16

            val tagsList = note.tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotBlank() }
            val tagsText = tagsList.joinToString("   ") { "#$it" }
            val dateText = "Updated ${dateFormat.format(Date(note.updatedAt))}"
            cardInnerHeight += 38
            if (tagsText.isNotEmpty()) {
                cardInnerHeight += 46
            }
            cardInnerHeight += 28 // Divider + spacing

            val imageById = note.images.associateBy { it.id }
            val renderedImageIds = mutableSetOf<String>()
            val blocks = mutableListOf<PreparedBlock>()

            fun parseTextIntoLinesBlock(segmentText: String): PreparedBlock.LinesBlock {
                val cleanSeg = segmentText.trim('\r')
                if (cleanSeg.isEmpty()) return PreparedBlock.LinesBlock(emptyList())
                val lines = cleanSeg.split("\n").map { rawLine ->
                    var rem = rawLine
                    var hasCheckbox = false
                    var isChecked = false
                    var hasBullet = false

                    if (rem.startsWith("☐ ")) {
                        hasCheckbox = true
                        isChecked = false
                        rem = rem.removePrefix("☐ ")
                    } else if (rem.startsWith("☑ ")) {
                        hasCheckbox = true
                        isChecked = true
                        rem = rem.removePrefix("☑ ")
                    }
                    if (rem.startsWith("• ")) {
                        hasBullet = true
                        rem = rem.removePrefix("• ")
                    }
                    val indentPx = (if (hasCheckbox) 46 else 0) + (if (hasBullet) 32 else 0)
                    val paintToUse = if (isChecked) checkedBodyPaint else bodyPaint
                    val lineLayout = buildStaticLayout(rem, paintToUse, contentWidth - indentPx)
                    PreparedLine(
                        hasCheckbox = hasCheckbox,
                        isChecked = isChecked,
                        hasBullet = hasBullet,
                        layout = lineLayout
                    )
                }
                return PreparedBlock.LinesBlock(lines)
            }

            fun prepareBitmapBlock(imgItem: NoteImage): PreparedBlock.ImageBlock? {
                val bmp = PrivateStorageManager.loadDecryptedNoteImageBitmap(imgItem.uri) ?: return null
                val maxW = if (imgItem.isExpanded) contentWidth else (contentWidth * 0.65f).roundToInt()
                val scale = maxW.toFloat() / max(bmp.width, 1).toFloat()
                val targetW = maxW.coerceAtLeast(180)
                val targetH = (bmp.height * scale).roundToInt().coerceIn(120, 1400)
                return PreparedBlock.ImageBlock(PreparedImage(bmp, targetW, targetH))
            }

            val rawContent = stripHtmlTags(note.content)
            var lastIdx = 0
            for (match in markerRegex.findAll(rawContent)) {
                val beforeText = rawContent.substring(lastIdx, match.range.first).trim('\n')
                if (beforeText.isNotEmpty()) {
                    val lb = parseTextIntoLinesBlock(beforeText)
                    blocks.add(lb)
                    lb.lines.forEach { cardInnerHeight += max(it.layout.height, 38) + 14 }
                }
                val imgId = match.groupValues[1]
                renderedImageIds.add(imgId)
                val imgItem = imageById[imgId]
                if (imgItem != null) {
                    val ib = prepareBitmapBlock(imgItem)
                    if (ib != null) {
                        blocks.add(ib)
                        cardInnerHeight += ib.image.drawHeight + 24
                    }
                }
                lastIdx = match.range.last + 1
            }

            val tailText = rawContent.substring(lastIdx).trim('\n')
            if (tailText.isNotEmpty()) {
                val lb = parseTextIntoLinesBlock(tailText)
                blocks.add(lb)
                lb.lines.forEach { cardInnerHeight += max(it.layout.height, 38) + 14 }
            }

            // Also include any unreferenced images
            for (imgItem in note.images) {
                if (!renderedImageIds.contains(imgItem.id)) {
                    val ib = prepareBitmapBlock(imgItem)
                    if (ib != null) {
                        blocks.add(ib)
                        cardInnerHeight += ib.image.drawHeight + 24
                    }
                }
            }

            // Include separate checklist items if present
            if (note.checklist.isNotEmpty()) {
                val checklistLines = note.checklist.map { item ->
                    val paintToUse = if (item.completed) checkedBodyPaint else bodyPaint
                    val lineLayout = buildStaticLayout(item.text, paintToUse, contentWidth - 46)
                    PreparedLine(
                        hasCheckbox = true,
                        isChecked = item.completed,
                        hasBullet = false,
                        layout = lineLayout
                    )
                }
                blocks.add(PreparedBlock.LinesBlock(checklistLines))
                checklistLines.forEach { cardInnerHeight += max(it.layout.height, 38) + 14 }
            }

            cardInnerHeight += cardPadding + 36 // Footer watermark space
            preparedCards.add(
                PreparedNoteCard(
                    titleLayout = titleLayout,
                    tagsText = tagsText,
                    dateText = dateText,
                    blocks = blocks,
                    cardHeight = cardInnerHeight
                )
            )
            totalHeight += cardInnerHeight + 36
        }

        totalHeight = max(totalHeight + outerPadding - 36, 480)
        val outputBitmap = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), bgPaint)

        val checkboxBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val checkMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A78BFA")
            style = Paint.Style.FILL
        }

        var currentTop = outerPadding.toFloat()
        for (card in preparedCards) {
            val cardRect = RectF(
                outerPadding.toFloat(),
                currentTop,
                (canvasWidth - outerPadding).toFloat(),
                currentTop + card.cardHeight
            )
            canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)
            canvas.drawRoundRect(cardRect, 36f, 36f, cardStrokePaint)

            var y = currentTop + cardPadding
            val leftX = (outerPadding + cardPadding).toFloat()
            val rightX = (canvasWidth - outerPadding - cardPadding).toFloat()

            // Draw Title
            canvas.save()
            canvas.translate(leftX, y)
            card.titleLayout.draw(canvas)
            canvas.restore()
            y += card.titleLayout.height + 16f

            // Draw Date
            canvas.drawText(card.dateText, leftX, y + 22f, datePaint)
            y += 38f

            // Draw Tags
            if (card.tagsText.isNotEmpty()) {
                val tagWidth = (metaPaint.measureText(card.tagsText) + 32f).coerceAtMost(contentWidth.toFloat())
                val pillRect = RectF(leftX, y, leftX + tagWidth, y + 36f)
                canvas.drawRoundRect(pillRect, 12f, 12f, tagBgPaint)
                canvas.drawRoundRect(pillRect, 12f, 12f, tagStrokePaint)
                canvas.drawText(card.tagsText, leftX + 16f, y + 26f, metaPaint)
                y += 46f
            }

            // Divider
            y += 10f
            canvas.drawLine(leftX, y, rightX, y, dividerPaint)
            y += 18f

            // Draw Blocks (Lines + Embedded Images)
            for (block in card.blocks) {
                when (block) {
                    is PreparedBlock.LinesBlock -> {
                        for (line in block.lines) {
                            var lineX = leftX
                            if (line.hasCheckbox) {
                                val boxSize = 28f
                                val boxTop = y + 4f
                                val boxRect = RectF(lineX, boxTop, lineX + boxSize, boxTop + boxSize)
                                if (line.isChecked) {
                                    checkboxBoxPaint.style = Paint.Style.FILL
                                    checkboxBoxPaint.color = Color.parseColor("#7C3AED")
                                    canvas.drawRoundRect(boxRect, 8f, 8f, checkboxBoxPaint)
                                    val checkPath = Path().apply {
                                        moveTo(lineX + 7f, boxTop + 14f)
                                        lineTo(lineX + 12.5f, boxTop + 20f)
                                        lineTo(lineX + 21.5f, boxTop + 8.5f)
                                    }
                                    canvas.drawPath(checkPath, checkMarkPaint)
                                } else {
                                    checkboxBoxPaint.style = Paint.Style.STROKE
                                    checkboxBoxPaint.strokeWidth = 2.6f
                                    checkboxBoxPaint.color = Color.parseColor("#A78BFA")
                                    canvas.drawRoundRect(boxRect, 8f, 8f, checkboxBoxPaint)
                                }
                                lineX += 46f
                            }

                            if (line.hasBullet) {
                                canvas.drawCircle(lineX + 10f, y + 18f, 5.5f, bulletPaint)
                                lineX += 32f
                            }

                            canvas.save()
                            canvas.translate(lineX, y)
                            line.layout.draw(canvas)
                            canvas.restore()
                            y += max(line.layout.height, 38) + 14f
                        }
                    }

                    is PreparedBlock.ImageBlock -> {
                        val img = block.image
                        val destRect = RectF(
                            leftX,
                            y + 6f,
                            leftX + img.drawWidth,
                            y + 6f + img.drawHeight
                        )
                        val clipPath = Path().apply {
                            addRoundRect(destRect, 20f, 20f, Path.Direction.CW)
                        }
                        canvas.save()
                        canvas.clipPath(clipPath)
                        canvas.drawBitmap(img.bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        canvas.restore()
                        canvas.drawRoundRect(destRect, 20f, 20f, cardStrokePaint)
                        y += img.drawHeight + 24f
                    }
                }
            }

            // Footer watermark
            canvas.drawText(
                "Aurora Notes",
                leftX,
                currentTop + card.cardHeight - 24f,
                footerPaint
            )

            currentTop += card.cardHeight + 36f
        }

        return outputBitmap
    }

    /**
     * Saves the full-length rendered note image directly into the Android Gallery
     * (`Pictures/AuroraNotes`) via `MediaStore.Images.Media`.
     */
    fun saveNoteImageToGallery(context: Context, notes: List<Note>): Boolean {
        if (notes.isEmpty()) return false
        return try {
            val bitmap = renderNotesToFullLengthBitmap(notes)
            val fileName = buildSuggestedFileName(notes, ShareNoteFormat.NOTE_IMAGE)
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuroraNotes")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            LocalLogger.security("SHARE", "Saved full-length note image ($fileName) to Android Gallery")
            Toast.makeText(
                context,
                "Saved full-length note image to Android Gallery!",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            LocalLogger.error("SHARE", "Failed saving note image to Android Gallery: ${e.localizedMessage}")
            Toast.makeText(
                context,
                "Failed to save image to Gallery: ${e.localizedMessage ?: "Storage error"}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    fun buildSuggestedFileName(notes: List<Note>, format: ShareNoteFormat): String {
        val plainNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        val baseName = if (plainNotes.size == 1) {
            val rawTitle = plainNotes.first().title.trim().ifBlank { "Untitled_Note" }
            rawTitle
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .take(48)
                .ifBlank { "Aurora_Note" }
        } else {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            "Aurora_Notes_${plainNotes.size}_$dateStr"
        }
        return "$baseName.${format.extension}"
    }

    fun preparePendingSafSave(notes: List<Note>, format: ShareNoteFormat, suggestedFileName: String) {
        pendingSafNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        pendingSafFormat = format
        pendingSafFileName = suggestedFileName
    }

    fun handlePendingSafResult(context: Context, uri: Uri?) {
        if (uri == null || pendingSafNotes.isEmpty()) return
        val ok = writeNoteToChosenUri(
            context = context,
            uri = uri,
            notes = pendingSafNotes,
            format = pendingSafFormat
        )
        if (ok) {
            Toast.makeText(
                context,
                "Saved $pendingSafFileName to your chosen folder!",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context,
                "Could not write file to selected location.",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingSafNotes = emptyList()
    }

    fun shareToAppsAndContacts(
        context: Context,
        notes: List<Note>,
        format: ShareNoteFormat,
        asFileAttachment: Boolean = false
    ) {
        if (notes.isEmpty()) return
        val plainNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        val fileName = buildSuggestedFileName(plainNotes, format)
        val subject = if (plainNotes.size == 1) {
            plainNotes.first().title.ifBlank { "Aurora Note" }
        } else {
            "${plainNotes.size} Aurora Notes"
        }

        try {
            if (format == ShareNoteFormat.NOTE_IMAGE) {
                val bitmap = renderNotesToFullLengthBitmap(plainNotes)
                val sharedDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
                val file = File(sharedDir, fileName)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Share Full-Length Note Image")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } else {
                val formattedMarkdown = formatNotes(plainNotes, format, embedBase64Images = true)
                val sharedDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
                val file = File(sharedDir, fileName)
                file.writeText(formattedMarkdown, Charsets.UTF_8)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, formattedMarkdown)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Share ${format.label}")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
            LocalLogger.security("SHARE", "Shared ${plainNotes.size} note(s) in ${format.name} format")
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
            val plainNotes = notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
            if (format == ShareNoteFormat.NOTE_IMAGE) {
                val bitmap = renderNotesToFullLengthBitmap(plainNotes)
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val pngBytes = bos.toByteArray()
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(pngBytes)
                    outputStream.flush()
                } ?: context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pngBytes)
                    outputStream.flush()
                } ?: return false
            } else {
                val content = formatNotes(plainNotes, format, embedBase64Images = true)
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } ?: context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } ?: return false
            }
            LocalLogger.security("SHARE", "Saved ${plainNotes.size} note(s) as .${format.extension} to chosen folder")
            true
        } catch (e: Exception) {
            LocalLogger.error("SHARE", "Failed saving note to chosen folder: ${e.localizedMessage}")
            false
        }
    }
}
