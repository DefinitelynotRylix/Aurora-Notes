package com.aurora.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.model.ChecklistItem
import com.aurora.notes.model.Note
import com.aurora.notes.ui.components.ShareNoteDialog
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.LinkHelper
import com.aurora.notes.util.PrivateStorageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlin.math.min

private const val UNCHECKED_BOX_PREFIX = "☐ "
private const val CHECKED_BOX_PREFIX = "☑ "
private const val BULLET_LINE_PREFIX = "• "

private fun findLineStart(text: String, offset: Int): Int {
    val safe = offset.coerceIn(0, text.length)
    if (safe == 0) return 0
    val prevNewline = text.lastIndexOf('\n', safe - 1)
    return if (prevNewline == -1) 0 else prevNewline + 1
}

private fun findLineEnd(text: String, lineStart: Int): Int {
    val nextNewline = text.indexOf('\n', lineStart.coerceIn(0, text.length))
    return if (nextNewline == -1) text.length else nextNewline
}

private fun hasCheckboxAtLineStart(text: String, lineStart: Int): Boolean {
    return text.startsWith(UNCHECKED_BOX_PREFIX, lineStart) ||
        text.startsWith(CHECKED_BOX_PREFIX, lineStart)
}

/**
 * Enforces the -1 rule: on any line that has a checkbox in its -1 prefix slot (`lineStart..lineStart+1`),
 * the cursor/selection cannot enter before `lineStart + 2` (position 0 of the line's user text).
 */
private fun clampSelectionToMinusOneRule(text: String, selection: TextRange): TextRange {
    fun clampOffset(offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        val lineStart = findLineStart(text, safe)
        return if (hasCheckboxAtLineStart(text, lineStart) && safe < lineStart + 2) {
            (lineStart + 2).coerceAtMost(text.length)
        } else {
            safe
        }
    }
    return TextRange(clampOffset(selection.start), clampOffset(selection.end))
}

/**
 * Cleans up broken prefix states when the user presses Backspace at position 0 (right after `☐ ` or `☑ `)
 * and prevents duplicate checkboxes on a single line.
 */
private fun normalizeCheckboxLinesAfterEdit(
    oldText: String,
    newText: String,
    newSelection: TextRange
): Pair<String, TextRange> {
    if (newText.isEmpty()) return newText to TextRange(0)
    val lines = newText.split('\n')
    var modified = false
    var cursor = newSelection.start.coerceIn(0, newText.length)
    var charOffset = 0

    val normalizedLines = lines.map { line ->
        val lStart = charOffset
        charOffset += line.length + 1

        // If user pressed Backspace right after `☐ ` or `☑ `, the space was deleted leaving `☐` or `☑`
        if ((line.startsWith("☐") && !line.startsWith(UNCHECKED_BOX_PREFIX)) ||
            (line.startsWith("☑") && !line.startsWith(CHECKED_BOX_PREFIX))
        ) {
            modified = true
            if (cursor > lStart) {
                cursor = (cursor - 1).coerceAtLeast(lStart)
            }
            line.substring(1)
        } else if (line.startsWith("☐ ☐ ") || line.startsWith("☑ ☐ ") || line.startsWith("☐ ☑ ") || line.startsWith("☑ ☑ ")) {
            modified = true
            if (cursor > lStart + 2) {
                cursor = (cursor - 2).coerceAtLeast(lStart + 2)
            }
            line.substring(0, 2) + line.substring(4)
        } else {
            line
        }
    }

    val finalString = if (modified) normalizedLines.joinToString("\n") else newText
    val finalRange = if (modified) {
        clampSelectionToMinusOneRule(finalString, TextRange(cursor.coerceIn(0, finalString.length)))
    } else {
        clampSelectionToMinusOneRule(finalString, newSelection)
    }
    return finalString to finalRange
}

enum class TextSizeMode {
    SMALL,
    MEDIUM,
    LARGE
}

data class StyledCharSpan(
    val start: Int,
    val end: Int,
    val sizeMode: TextSizeMode,
    val isBold: Boolean,
    val isItalic: Boolean
)

private val spanGson = Gson()

private fun parseSpansJson(json: String, maxLen: Int): List<StyledCharSpan> {
    if (json.isBlank()) return emptyList()
    return try {
        val type = object : TypeToken<List<StyledCharSpan>>() {}.type
        val raw: List<StyledCharSpan> = spanGson.fromJson(json, type) ?: emptyList()
        mergeSpans(
            raw.mapNotNull { sp ->
                val s = sp.start.coerceIn(0, maxLen)
                val e = sp.end.coerceIn(0, maxLen)
                if (s < e) sp.copy(start = s, end = e) else null
            }
        )
    } catch (_: Exception) {
        emptyList()
    }
}

private fun mergeSpans(spans: List<StyledCharSpan>): List<StyledCharSpan> {
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.filter { it.start < it.end }.sortedBy { it.start }
    if (sorted.isEmpty()) return emptyList()
    val result = ArrayList<StyledCharSpan>(sorted.size)
    var current = sorted[0]
    for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.start <= current.end &&
            next.sizeMode == current.sizeMode &&
            next.isBold == current.isBold &&
            next.isItalic == current.isItalic
        ) {
            current = current.copy(end = maxOf(current.end, next.end))
        } else {
            result.add(current)
            current = next
        }
    }
    result.add(current)
    return result
}

private fun applyTextEditToSpans(
    oldText: String,
    newText: String,
    oldSpans: List<StyledCharSpan>,
    activeSizeMode: TextSizeMode,
    isBoldActive: Boolean,
    isItalicActive: Boolean
): List<StyledCharSpan> {
    if (newText.isEmpty()) return emptyList()
    if (oldText == newText) return oldSpans

    val minLen = min(oldText.length, newText.length)
    var prefixLen = 0
    while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
        prefixLen++
    }

    var suffixLen = 0
    while (
        suffixLen < (oldText.length - prefixLen) &&
        suffixLen < (newText.length - prefixLen) &&
        oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
    ) {
        suffixLen++
    }

    val delStart = prefixLen
    val delEnd = oldText.length - suffixLen
    val insStart = prefixLen
    val insEnd = newText.length - suffixLen
    val delta = newText.length - oldText.length

    val adjusted = ArrayList<StyledCharSpan>(oldSpans.size + 2)
    for (sp in oldSpans) {
        when {
            sp.end <= delStart -> adjusted.add(sp)
            sp.start >= delEnd -> adjusted.add(sp.copy(start = sp.start + delta, end = sp.end + delta))
            else -> {
                if (sp.start < delStart) {
                    adjusted.add(sp.copy(end = delStart))
                }
                if (sp.end > delEnd) {
                    adjusted.add(sp.copy(start = insEnd, end = sp.end + delta))
                }
            }
        }
    }

    // Only add a span for newly typed text if a non-default style is active
    if (insEnd > insStart && (activeSizeMode != TextSizeMode.SMALL || isBoldActive || isItalicActive)) {
        adjusted.add(
            StyledCharSpan(
                start = insStart,
                end = insEnd,
                sizeMode = activeSizeMode,
                isBold = isBoldActive,
                isItalic = isItalicActive
            )
        )
    }

    return mergeSpans(adjusted)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    note: Note,
    userTags: List<String> = emptyList(),
    onSaveAndClose: (Note) -> Unit,
    onDiscardAndClose: () -> Unit = {},
    onUnarchiveNote: ((String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    onDeleteTagGlobal: ((String) -> Unit)? = null,
    onPredictiveBackProgress: (progress: Float, swipeEdge: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isReadOnly = note.isArchived
    var title by remember(note.id) { mutableStateOf(note.title) }
    var isPinned by remember(note.id) { mutableStateOf(note.isPinned) }
    val tags = remember(note.id) { mutableStateListOf(*note.tags.toTypedArray()) }
    val checklistItems = remember(note.id) { mutableStateListOf(*note.checklist.toTypedArray()) }

    var newTagInput by remember { mutableStateOf("") }
    var newChecklistInput by remember { mutableStateOf("") }
    var showDoneDetailsDialog by remember { mutableStateOf(false) }
    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    // Default size is SMALL (normal), and NO size button is highlighted by default
    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var activeSizeMode by remember { mutableStateOf(TextSizeMode.SMALL) }

    var bodyValue by remember(note.id) {
        mutableStateOf(
            TextFieldValue(
                text = note.content,
                selection = TextRange(note.content.length)
            )
        )
    }

    var charSpans by remember(note.id) {
        mutableStateOf(parseSpansJson(note.styleSpansJson, note.content.length))
    }

    val editorScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var bodyTopInColumnPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    // Automatically scroll the editor container down with the cursor BEFORE it reaches
    // the bottom formatting bar and keyboard so the cursor and current line stay in view.
    LaunchedEffect(
        bodyValue.text,
        bodyValue.selection,
        textLayoutResult,
        viewportHeightPx,
        imeBottomPx,
        editorScrollState.maxValue
    ) {
        if (isReadOnly) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        if (viewportHeightPx <= 0) return@LaunchedEffect

        val cursorOffset = bodyValue.selection.end.coerceIn(0, bodyValue.text.length)
        val cursorRect = try {
            layout.getCursorRect(cursorOffset)
        } catch (_: Exception) {
            return@LaunchedEffect
        }

        val bottomClearancePx = with(density) { 104.dp.toPx() }
        val topClearancePx = with(density) { 36.dp.toPx() }

        val cursorBottomInContent = bodyTopInColumnPx + cursorRect.bottom
        val cursorTopInContent = bodyTopInColumnPx + cursorRect.top

        val visibleTop = editorScrollState.value.toFloat()
        val visibleBottomThreshold = visibleTop + viewportHeightPx - bottomClearancePx

        if (cursorBottomInContent > visibleBottomThreshold) {
            val targetScroll = (cursorBottomInContent - viewportHeightPx + bottomClearancePx)
                .toInt()
                .coerceIn(0, editorScrollState.maxValue)
            if (targetScroll > editorScrollState.value) {
                editorScrollState.animateScrollTo(targetScroll)
            }
        } else if (cursorTopInContent < visibleTop + topClearancePx) {
            val targetScroll = (cursorTopInContent - topClearancePx)
                .toInt()
                .coerceIn(0, editorScrollState.maxValue)
            if (targetScroll < editorScrollState.value) {
                editorScrollState.animateScrollTo(targetScroll)
            }
        }
    }

    val context = LocalContext.current
    val detectedBodyLinks = remember(bodyValue.text) {
        LinkHelper.extractLinks(bodyValue.text)
    }

    val wordLetterStats = remember(title, bodyValue.text) {
        PrivateStorageManager.computeWordAndLetterCounts(title, bodyValue.text)
    }

    // VisualTransformation keeps IME typing 100% lag-free, styles user spans, inline checkboxes, and highlights links in purple
    val richVisualTransformation = remember(charSpans) {
        VisualTransformation { text ->
            val raw = text.text
            val annotated = buildAnnotatedString {
                append(raw)
                val len = raw.length
                for (span in charSpans) {
                    val s = span.start.coerceIn(0, len)
                    val e = span.end.coerceIn(0, len)
                    if (s < e) {
                        val fontSize = when (span.sizeMode) {
                            TextSizeMode.SMALL -> 15.sp
                            TextSizeMode.MEDIUM -> 19.sp
                            TextSizeMode.LARGE -> 24.sp
                        }
                        addStyle(
                            style = SpanStyle(
                                fontSize = fontSize,
                                fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                            ),
                            start = s,
                            end = e
                        )
                    }
                }

                // Style inline per-line checkboxes (`☐ ` and `☑ `) in the -1 prefix slot
                var lineStart = 0
                while (lineStart <= len) {
                    val lineEnd = findLineEnd(raw, lineStart)
                    if (raw.startsWith(UNCHECKED_BOX_PREFIX, lineStart)) {
                        addStyle(
                            style = SpanStyle(
                                color = AuroraRoyalViolet,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            start = lineStart,
                            end = (lineStart + 1).coerceAtMost(len)
                        )
                    } else if (raw.startsWith(CHECKED_BOX_PREFIX, lineStart)) {
                        addStyle(
                            style = SpanStyle(
                                color = AuroraRoyalViolet,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            start = lineStart,
                            end = (lineStart + 1).coerceAtMost(len)
                        )
                        if (lineStart + 2 < lineEnd) {
                            addStyle(
                                style = SpanStyle(
                                    color = AuroraRoyalViolet.copy(alpha = 0.58f),
                                    textDecoration = TextDecoration.LineThrough
                                ),
                                start = lineStart + 2,
                                end = lineEnd
                            )
                        }
                    }
                    lineStart = lineEnd + 1
                }

                val links = LinkHelper.extractLinks(raw)
                for (link in links) {
                    val ls = link.start.coerceIn(0, len)
                    val le = link.end.coerceIn(0, len)
                    if (ls < le) {
                        addStyle(
                            style = SpanStyle(
                                color = LinkHelper.LinkPurpleColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = ls,
                            end = le
                        )
                    }
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    fun handleExitEditor() {
        onPredictiveBackProgress(0f, 0)
        if (isReadOnly) {
            onDiscardAndClose()
            return
        }
        val cleanTitle = title.trim()
        val cleanBody = bodyValue.text.trim()
        val validChecklists = checklistItems.filter { it.text.trim().isNotEmpty() }

        // If user created a note and typed nothing in title, body, or checklist, do not save it
        if (cleanTitle.isEmpty() && cleanBody.isEmpty() && validChecklists.isEmpty()) {
            onDiscardAndClose()
            return
        }

        val updatedNote = note.copy(
            title = cleanTitle,
            content = bodyValue.text,
            styleSpansJson = if (charSpans.isEmpty()) "" else spanGson.toJson(charSpans),
            checklist = validChecklists,
            tags = tags.toList(),
            category = tags.firstOrNull() ?: "",
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
        onSaveAndClose(updatedNote)
    }

    val isSubDialogOpen = showDoneDetailsDialog || showAddChecklistDialog || pendingDeleteTag != null

    PredictiveBackHandler(enabled = !isSubDialogOpen) { progress ->
        try {
            progress.collect { backEvent ->
                onPredictiveBackProgress(backEvent.progress, backEvent.swipeEdge)
            }
            handleExitEditor()
        } catch (_: CancellationException) {
            onPredictiveBackProgress(0f, 0)
        }
    }

    BackHandler(enabled = isSubDialogOpen) {
        when {
            showDoneDetailsDialog -> showDoneDetailsDialog = false
            showAddChecklistDialog -> showAddChecklistDialog = false
            pendingDeleteTag != null -> pendingDeleteTag = null
        }
    }

    if (pendingDeleteTag != null && !isReadOnly) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Delete Tag #${pendingDeleteTag}?") },
            text = {
                Text(
                    "Remove #${pendingDeleteTag} from your tags? Your notes will not be deleted and will return to All.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tag = pendingDeleteTag
                        pendingDeleteTag = null
                        if (tag != null) {
                            tags.remove(tag)
                            onDeleteTagGlobal?.invoke(tag)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Text("Delete Tag", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteTag = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddChecklistDialog && !isReadOnly) {
        AlertDialog(
            onDismissRequest = { showAddChecklistDialog = false },
            title = { Text("Add Checklist Item", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newChecklistInput,
                    onValueChange = { newChecklistInput = it },
                    placeholder = { Text("Task description...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newChecklistInput.trim()
                        if (clean.isNotEmpty()) {
                            checklistItems.add(ChecklistItem(text = clean, completed = false))
                            newChecklistInput = ""
                        }
                        showAddChecklistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                ) {
                    Text("Add Item", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChecklistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDoneDetailsDialog && !isReadOnly) {
        AlertDialog(
            onDismissRequest = { showDoneDetailsDialog = false },
            title = {
                Text(text = "Save Note Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Tags (Long-press a tag to delete)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Existing user tags for quick toggle + note's current tags
                    val combinedTags = remember(userTags, tags.size) {
                        (userTags + tags).distinct()
                    }
                    if (combinedTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            combinedTags.forEach { tag ->
                                val isAttached = tags.any { it.equals(tag, ignoreCase = true) }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAttached) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.4f)),
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            if (isAttached) {
                                                tags.removeAll { it.equals(tag, ignoreCase = true) }
                                            } else {
                                                tags.add(tag)
                                            }
                                        },
                                        onLongClick = { pendingDeleteTag = tag }
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAttached) Color.White else AuroraRoyalViolet
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            placeholder = { Text("Create new tag...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val clean = newTagInput.trim().removePrefix("#").trim()
                                if (clean.isNotBlank() && tags.none { it.equals(clean, ignoreCase = true) }) {
                                    tags.add(clean)
                                    newTagInput = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pin note to top", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDoneDetailsDialog = false
                        handleExitEditor()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                ) {
                    Text("Save Note", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoneDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var showShareDialog by remember { mutableStateOf(false) }

    if (showShareDialog) {
        val currentSnapshot = note.copy(
            title = title.trim().ifBlank { "Untitled Note" },
            content = bodyValue.text,
            styleSpansJson = if (charSpans.isEmpty()) "" else spanGson.toJson(charSpans),
            checklist = checklistItems.filter { it.text.trim().isNotEmpty() },
            tags = tags.toList(),
            category = tags.firstOrNull() ?: "",
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
        ShareNoteDialog(
            notes = listOf(currentSnapshot),
            onDismiss = { showShareDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { handleExitEditor() },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }

                        if (isReadOnly) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Archived • Read-Only",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = AuroraRoyalViolet
                                        )
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { showDoneDetailsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (tags.isNotEmpty()) "#${tags.first()}" else "Add Tag",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                        // Live Word & Letter Counter Pill on Top of Note Editor
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AuroraRoyalViolet.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = "${wordLetterStats.words} ${if (wordLetterStats.words == 1) "word" else "words"} • ${wordLetterStats.letters} ${if (wordLetterStats.letters == 1) "letter" else "letters"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = AuroraRoyalViolet
                                ),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isReadOnly) {
                            if (onUnarchiveNote != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onUnarchiveNote(note.id)
                                        onDiscardAndClose()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AuroraRoyalViolet,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Unarchive,
                                        contentDescription = "Restore Note",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (onDeleteNote != null) {
                                Button(
                                    onClick = {
                                        onDeleteNote(note.id)
                                        onDiscardAndClose()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Note",
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { showShareDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Note",
                                    tint = AuroraRoyalViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Button(
                                onClick = { showDoneDetailsDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isReadOnly) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Narrower Bold Toggle Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBoldActive) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isBoldActive) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(32.dp)
                                .clickable {
                                    isBoldActive = !isBoldActive
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBoldActive) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Narrower Italic Toggle Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isItalicActive) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isItalicActive) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(32.dp)
                                .clickable {
                                    isItalicActive = !isItalicActive
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FormatItalic,
                                    contentDescription = "Italic",
                                    tint = if (isItalicActive) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Text Sizing Buttons:
                        // Default is Small (neither Medium nor Large is enabled by default).
                        // Tapping Medium toggles Medium on/off (reverting to Small when turned off).
                        // Tapping Large toggles Large on/off (reverting to Small when turned off).
                        Row(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextSizeButton(
                                label = "Medium",
                                selected = activeSizeMode == TextSizeMode.MEDIUM,
                                onClick = {
                                    activeSizeMode = if (activeSizeMode == TextSizeMode.MEDIUM) {
                                        TextSizeMode.SMALL
                                    } else {
                                        TextSizeMode.MEDIUM
                                    }
                                }
                            )
                            TextSizeButton(
                                label = "Large",
                                selected = activeSizeMode == TextSizeMode.LARGE,
                                onClick = {
                                    activeSizeMode = if (activeSizeMode == TextSizeMode.LARGE) {
                                        TextSizeMode.SMALL
                                    } else {
                                        TextSizeMode.LARGE
                                    }
                                }
                            )
                        }

                        // Current cursor line metadata for Bullet & Checkbox (-1 rule)
                        val activeCursor = bodyValue.selection.start.coerceIn(0, bodyValue.text.length)
                        val activeLineStart = findLineStart(bodyValue.text, activeCursor)
                        val isCurrentLineBullet = bodyValue.text.startsWith(BULLET_LINE_PREFIX, activeLineStart)
                        val isCurrentLineCheckbox = hasCheckboxAtLineStart(bodyValue.text, activeLineStart)

                        // Insert / Toggle Bullet on Current Line
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrentLineBullet) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentLineBullet) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(36.dp)
                                .clickable {
                                    val text = bodyValue.text
                                    val cursor = bodyValue.selection.start.coerceIn(0, text.length)
                                    val lineStart = findLineStart(text, cursor)
                                    val hasBullet = text.startsWith(BULLET_LINE_PREFIX, lineStart)
                                    val hasCheckbox = hasCheckboxAtLineStart(text, lineStart)
                                    val newText = when {
                                        hasBullet -> text.removeRange(lineStart, lineStart + 2)
                                        hasCheckbox -> text.substring(0, lineStart) + BULLET_LINE_PREFIX + text.substring(lineStart + 2)
                                        else -> text.substring(0, lineStart) + BULLET_LINE_PREFIX + text.substring(lineStart)
                                    }
                                    val newCursor = when {
                                        hasBullet -> (cursor - 2).coerceAtLeast(lineStart)
                                        hasCheckbox -> cursor.coerceAtLeast(lineStart + 2)
                                        else -> cursor + 2
                                    }
                                    charSpans = applyTextEditToSpans(
                                        oldText = text,
                                        newText = newText,
                                        oldSpans = charSpans,
                                        activeSizeMode = TextSizeMode.SMALL,
                                        isBoldActive = false,
                                        isItalicActive = false
                                    )
                                    bodyValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursor.coerceIn(0, newText.length))
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = "Bullet List",
                                    tint = if (isCurrentLineBullet) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Per-Line Checkbox Toggle (-1 Rule: enables/disables checkbox in the -1 slot of the current line)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrentLineCheckbox) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentLineCheckbox) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(36.dp)
                                .clickable {
                                    val text = bodyValue.text
                                    val cursor = bodyValue.selection.start.coerceIn(0, text.length)
                                    val lineStart = findLineStart(text, cursor)
                                    val hasCheckbox = hasCheckboxAtLineStart(text, lineStart)
                                    val hasBullet = text.startsWith(BULLET_LINE_PREFIX, lineStart)
                                    val newText = when {
                                        hasCheckbox -> text.removeRange(lineStart, lineStart + 2)
                                        hasBullet -> text.substring(0, lineStart) + UNCHECKED_BOX_PREFIX + text.substring(lineStart + 2)
                                        else -> text.substring(0, lineStart) + UNCHECKED_BOX_PREFIX + text.substring(lineStart)
                                    }
                                    val newCursor = when {
                                        hasCheckbox -> (cursor - 2).coerceAtLeast(lineStart)
                                        hasBullet -> cursor.coerceAtLeast(lineStart + 2)
                                        else -> (cursor + 2).coerceAtLeast(lineStart + 2)
                                    }
                                    charSpans = applyTextEditToSpans(
                                        oldText = text,
                                        newText = newText,
                                        oldSpans = charSpans,
                                        activeSizeMode = TextSizeMode.SMALL,
                                        isBoldActive = false,
                                        isItalicActive = false
                                    )
                                    bodyValue = TextFieldValue(
                                        text = newText,
                                        selection = clampSelectionToMinusOneRule(
                                            newText,
                                            TextRange(newCursor.coerceIn(0, newText.length))
                                        )
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckBox,
                                    contentDescription = "Toggle Line Checkbox",
                                    tint = if (isCurrentLineCheckbox) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onSizeChanged { viewportHeightPx = it.height }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(editorScrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isReadOnly) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AuroraRoyalViolet.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This note is archived and read-only. Tap Restore above to edit it, or Delete to move it to Trash.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }

                // Note Title Input
                BasicTextField(
                    value = title,
                    onValueChange = { if (!isReadOnly) title = it },
                    readOnly = isReadOnly,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(AuroraRoyalViolet),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "Title",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Checklist Section (read-only when archived)
                if (checklistItems.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        checklistItems.forEachIndexed { idx, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = item.completed,
                                    enabled = !isReadOnly,
                                    onCheckedChange = { checked ->
                                        if (!isReadOnly) {
                                            checklistItems[idx] = item.copy(completed = checked)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AuroraRoyalViolet),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val itemLinks = remember(item.text) { LinkHelper.extractLinks(item.text) }
                                Text(
                                    text = LinkHelper.buildLinkAnnotatedString(item.text),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (item.completed) {
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .let { mod ->
                                            if (itemLinks.isNotEmpty()) {
                                                mod.clickable {
                                                    LinkHelper.openUrlInDefaultBrowser(context, itemLinks.first().normalizedUrl)
                                                }
                                            } else {
                                                mod
                                            }
                                        }
                                )
                                if (!isReadOnly) {
                                    IconButton(
                                        onClick = { checklistItems.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove item",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                        if (!isReadOnly) {
                            TextButton(
                                onClick = { showAddChecklistDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = AuroraRoyalViolet)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add checklist item", fontSize = 12.sp, color = AuroraRoyalViolet)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), thickness = 0.6.dp)
                    }
                }

                // Interactive Clickable Purple Link Pills when URLs are present in the note
                if (detectedBodyLinks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        detectedBodyLinks.distinctBy { it.normalizedUrl }.forEach { linkItem ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LinkHelper.LinkPurpleColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, LinkHelper.LinkPurpleColor.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    LinkHelper.openUrlInDefaultBrowser(context, linkItem.normalizedUrl)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Open link in browser",
                                        tint = LinkHelper.LinkPurpleColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = linkItem.rawText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                        color = LinkHelper.LinkPurpleColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Unified Multiline Note Body Editor (Read-only when archived; tapping purple links opens Android default browser; tapping inline checkbox toggles it)
                BasicTextField(
                    value = bodyValue,
                    onValueChange = { newValue ->
                        val oldText = bodyValue.text
                        val newText = newValue.text

                        // Handle tap inside text when characters did not change
                        if (oldText == newText && newValue.selection.collapsed && newValue.selection != bodyValue.selection) {
                            val tappedOffset = newValue.selection.start.coerceIn(0, newText.length)
                            val tappedLineStart = findLineStart(newText, tappedOffset)

                            // If the user tapped on the -1 checkbox slot (`☐ ` or `☑ `) on this line, toggle checked/unchecked!
                            if (!isReadOnly && hasCheckboxAtLineStart(newText, tappedLineStart) && tappedOffset in tappedLineStart..(tappedLineStart + 1)) {
                                val isCurrentlyChecked = newText.startsWith(CHECKED_BOX_PREFIX, tappedLineStart)
                                val replacementPrefix = if (isCurrentlyChecked) UNCHECKED_BOX_PREFIX else CHECKED_BOX_PREFIX
                                val toggledText = newText.substring(0, tappedLineStart) +
                                    replacementPrefix +
                                    newText.substring(tappedLineStart + 2)
                                bodyValue = TextFieldValue(
                                    text = toggledText,
                                    selection = TextRange((tappedLineStart + 2).coerceAtMost(toggledText.length))
                                )
                                return@BasicTextField
                            }

                            val clickedLink = detectedBodyLinks.firstOrNull { link ->
                                tappedOffset > link.start && tappedOffset < link.end
                            }
                            if (clickedLink != null) {
                                LinkHelper.openUrlInDefaultBrowser(context, clickedLink.normalizedUrl)
                            }
                        }

                        if (isReadOnly) return@BasicTextField

                        val (normalizedText, clampedSelection) = normalizeCheckboxLinesAfterEdit(
                            oldText = oldText,
                            newText = newText,
                            newSelection = newValue.selection
                        )

                        if (oldText != normalizedText) {
                            charSpans = applyTextEditToSpans(
                                oldText = oldText,
                                newText = normalizedText,
                                oldSpans = charSpans,
                                activeSizeMode = activeSizeMode,
                                isBoldActive = isBoldActive,
                                isItalicActive = isItalicActive
                            )
                        }
                        bodyValue = newValue.copy(
                            text = normalizedText,
                            selection = clampedSelection
                        )
                    },
                    readOnly = isReadOnly,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    onTextLayout = { textLayoutResult = it },
                    visualTransformation = richVisualTransformation,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(AuroraRoyalViolet),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 360.dp)
                        ) {
                            if (bodyValue.text.isEmpty() && !isReadOnly) {
                                Text(
                                    text = "Start typing your note here...",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 360.dp)
                        .let { mod ->
                            if (isReadOnly && detectedBodyLinks.isNotEmpty()) {
                                mod.pointerInput(bodyValue.text, detectedBodyLinks) {
                                    detectTapGestures { tapOffset ->
                                        val layout = textLayoutResult ?: return@detectTapGestures
                                        val charOffset = layout.getOffsetForPosition(tapOffset)
                                        val hitLink = detectedBodyLinks.firstOrNull { link ->
                                            charOffset in link.start..link.end
                                        }
                                        if (hitLink != null) {
                                            LinkHelper.openUrlInDefaultBrowser(context, hitLink.normalizedUrl)
                                        }
                                    }
                                }
                            } else {
                                mod
                            }
                        }
                        .onGloballyPositioned { coords ->
                            bodyTopInColumnPx = coords.positionInParent().y
                        }
                )

                // Extra bottom space so the scroll container can always push the cursor and
                // surrounding lines up well above the bottom formatting bar and keyboard
                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }
}

@Composable
private fun TextSizeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AuroraRoyalViolet.copy(alpha = 0.16f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, AuroraRoyalViolet) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
