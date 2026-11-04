package com.aurora.notes.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.aurora.notes.model.NoteImage
import com.aurora.notes.ui.components.ShareNoteDialog
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.LinkHelper
import com.aurora.notes.util.PrivateStorageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

private const val UNCHECKED_BOX_PREFIX = "☐ "
private const val CHECKED_BOX_PREFIX = "☑ "
private const val BULLET_LINE_PREFIX = "• "

private data class ParsedLinePrefix(
    val hasCheckbox: Boolean,
    val checkboxPrefix: String,
    val hasBullet: Boolean,
    val cleanBody: String
) {
    val totalPrefixLength: Int
        get() = (if (hasCheckbox) 2 else 0) + (if (hasBullet) 2 else 0)

    val canonicalPrefix: String
        get() = (if (hasCheckbox) checkboxPrefix else "") + (if (hasBullet) BULLET_LINE_PREFIX else "")
}

private fun parseLinePrefixes(line: String): ParsedLinePrefix {
    var rem = line
    var hasCheckbox = false
    var checkboxPrefix = UNCHECKED_BOX_PREFIX
    var hasBullet = false

    repeat(2) {
        when {
            !hasCheckbox && (rem.startsWith(UNCHECKED_BOX_PREFIX) || rem.startsWith(CHECKED_BOX_PREFIX)) -> {
                hasCheckbox = true
                checkboxPrefix = rem.substring(0, 2)
                rem = rem.substring(2)
            }
            !hasBullet && rem.startsWith(BULLET_LINE_PREFIX) -> {
                hasBullet = true
                rem = rem.substring(2)
            }
        }
    }
    return ParsedLinePrefix(
        hasCheckbox = hasCheckbox,
        checkboxPrefix = checkboxPrefix,
        hasBullet = hasBullet,
        cleanBody = rem
    )
}

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

private fun getParsedLineAt(text: String, lineStart: Int): ParsedLinePrefix {
    val safeStart = lineStart.coerceIn(0, text.length)
    val lineEnd = findLineEnd(text, safeStart)
    return parseLinePrefixes(text.substring(safeStart, lineEnd))
}

private fun hasCheckboxAtLineStart(text: String, lineStart: Int): Boolean {
    return getParsedLineAt(text, lineStart).hasCheckbox
}

private fun hasBulletAtLineStart(text: String, lineStart: Int): Boolean {
    return getParsedLineAt(text, lineStart).hasBullet
}

/**
 * Enforces the -1 rule: on any line that has a checkbox and/or bullet prefix,
 * the cursor/selection cannot enter before the user text start (`lineStart + totalPrefixLength`).
 */
private fun clampSelectionToMinusOneRule(text: String, selection: TextRange): TextRange {
    fun clampOffset(offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        val lineStart = findLineStart(text, safe)
        val parsed = getParsedLineAt(text, lineStart)
        val minAllowed = lineStart + parsed.totalPrefixLength
        return if ((parsed.hasCheckbox || parsed.hasBullet) && safe < minAllowed) {
            minAllowed.coerceAtMost(text.length)
        } else {
            safe
        }
    }
    return TextRange(clampOffset(selection.start), clampOffset(selection.end))
}

/**
 * Intercepts taps directly on the `☐ ` / `☑ ` checkbox prefix in `PointerEventPass.Initial`
 * before `BasicTextField` consumes the pointer event or clamps the cursor, so tapping any
 * checkbox inside the note editor always checks/unchecks it on the first tap.
 */
private fun Modifier.checkboxPrefixTapDetector(
    enabled: Boolean,
    hitWidthPx: Float,
    getText: () -> String,
    getLayout: () -> TextLayoutResult?,
    onToggleLineCheckbox: (lineStart: Int, parsed: ParsedLinePrefix) -> Unit
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val layout = getLayout() ?: return@awaitEachGesture
            val text = getText()
            if (text.isEmpty() || layout.lineCount == 0) return@awaitEachGesture

            val tapY = down.position.y
            val lineIdx = layout.getLineForVerticalPosition(tapY).coerceIn(0, layout.lineCount - 1)
            val lineTop = layout.getLineTop(lineIdx) - 8f
            val lineBottom = layout.getLineBottom(lineIdx) + 8f
            if (tapY !in lineTop..lineBottom) return@awaitEachGesture

            val lineStart = layout.getLineStart(lineIdx).coerceIn(0, text.length)
            val parsed = getParsedLineAt(text, lineStart)
            if (!parsed.hasCheckbox) return@awaitEachGesture

            val prefixRightX = try {
                layout.getHorizontalPosition((lineStart + 2).coerceAtMost(text.length), usePrimaryDirection = true)
            } catch (_: Exception) {
                0f
            }
            val maxCheckboxX = maxOf(prefixRightX + 20f, hitWidthPx)
            if (down.position.x !in 0f..maxCheckboxX) return@awaitEachGesture

            down.consume()
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
            if (up != null) {
                up.consume()
                onToggleLineCheckbox(lineStart, parsed)
            }
        }
    }
}

/**
 * Normalizes line prefixes and handles Enter key continuation when Bullet List and/or Checkbox
 * mode is active so pressing Enter NEVER deletes the bullet or checkbox and continues on every new line
 * until the user disables the button.
 */
private fun normalizeCheckboxLinesAfterEdit(
    oldText: String,
    newText: String,
    newSelection: TextRange,
    isCheckboxModeActive: Boolean,
    isBulletModeActive: Boolean
): Pair<String, TextRange> {
    if (newText.isEmpty()) return newText to TextRange(0)

    var workingText = newText
    var workingCursor = newSelection.start.coerceIn(0, workingText.length)

    // Detect if the user just pressed Enter / Send (inserted a single '\n' at cursor - 1)
    if (newText.length == oldText.length + 1 &&
        workingCursor > 0 &&
        workingText[workingCursor - 1] == '\n'
    ) {
        val prevLineStart = findLineStart(workingText, (workingCursor - 1).coerceAtLeast(0))
        val prevParsed = getParsedLineAt(workingText, prevLineStart)
        val continueCb = isCheckboxModeActive || prevParsed.hasCheckbox
        val continueBullet = isBulletModeActive || prevParsed.hasBullet
        val autoPrefix =
            (if (continueCb) UNCHECKED_BOX_PREFIX else "") +
                (if (continueBullet) BULLET_LINE_PREFIX else "")

        if (autoPrefix.isNotEmpty() && !workingText.startsWith(autoPrefix, workingCursor)) {
            workingText =
                workingText.substring(0, workingCursor) +
                    autoPrefix +
                    workingText.substring(workingCursor)
            workingCursor += autoPrefix.length
        }
    }

    val lines = workingText.split('\n')
    var modified = workingText != newText
    var charOffset = 0

    val normalizedLines = lines.map { line ->
        val lStart = charOffset
        charOffset += line.length + 1

        // Canonicalize `• ☐ ` -> `☐ • ` and `• ☑ ` -> `☑ • ` so checkbox and bullet stay side-by-side on the same line
        when {
            line.startsWith("$BULLET_LINE_PREFIX$UNCHECKED_BOX_PREFIX") -> {
                modified = true
                "$UNCHECKED_BOX_PREFIX$BULLET_LINE_PREFIX" + line.substring(4)
            }
            line.startsWith("$BULLET_LINE_PREFIX$CHECKED_BOX_PREFIX") -> {
                modified = true
                "$CHECKED_BOX_PREFIX$BULLET_LINE_PREFIX" + line.substring(4)
            }
            (line.startsWith("☐") && !line.startsWith(UNCHECKED_BOX_PREFIX)) ||
                (line.startsWith("☑") && !line.startsWith(CHECKED_BOX_PREFIX)) -> {
                modified = true
                if (workingCursor > lStart) {
                    workingCursor = (workingCursor - 1).coerceAtLeast(lStart)
                }
                line.substring(1)
            }
            line.startsWith("☐ ☐ ") || line.startsWith("☑ ☐ ") || line.startsWith("☐ ☑ ") || line.startsWith("☑ ☑ ") -> {
                modified = true
                if (workingCursor > lStart + 2) {
                    workingCursor = (workingCursor - 2).coerceAtLeast(lStart + 2)
                }
                line.substring(0, 2) + line.substring(4)
            }
            else -> line
        }
    }

    val finalString = if (modified) normalizedLines.joinToString("\n") else workingText
    val finalRange = clampSelectionToMinusOneRule(
        finalString,
        TextRange(workingCursor.coerceIn(0, finalString.length))
    )
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

    // Add a span for newly typed text if a non-default style is active
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

/**
 * Applies a style update across a selected character range `[selStart, selEnd)` in `oldSpans`,
 * splitting any partially overlapping spans and applying the updated style to the selected slice.
 */
private fun applyStyleToSelectionRange(
    oldSpans: List<StyledCharSpan>,
    rangeStart: Int,
    rangeEnd: Int,
    targetSizeMode: TextSizeMode,
    targetBold: Boolean,
    targetItalic: Boolean
): List<StyledCharSpan> {
    if (rangeEnd <= rangeStart) return oldSpans
    val result = ArrayList<StyledCharSpan>(oldSpans.size + 3)

    for (sp in oldSpans) {
        when {
            sp.end <= rangeStart || sp.start >= rangeEnd -> result.add(sp)
            else -> {
                if (sp.start < rangeStart) {
                    result.add(sp.copy(end = rangeStart))
                }
                if (sp.end > rangeEnd) {
                    result.add(sp.copy(start = rangeEnd))
                }
            }
        }
    }

    if (targetSizeMode != TextSizeMode.SMALL || targetBold || targetItalic) {
        result.add(
            StyledCharSpan(
                start = rangeStart,
                end = rangeEnd,
                sizeMode = targetSizeMode,
                isBold = targetBold,
                isItalic = targetItalic
            )
        )
    }

    return mergeSpans(result)
}

/**
 * Value-equal `VisualTransformation` implementation backed by an immutable `spans` snapshot
 * and `baseOffset` so Compose's `BasicTextField` always detects span updates and re-filters immediately.
 */
private data class RichNoteVisualTransformation(
    val spans: List<StyledCharSpan>,
    val baseOffset: Int = 0
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val len = raw.length
        val annotated = buildAnnotatedString {
            append(raw)
            for (span in spans) {
                val localStart = (span.start - baseOffset).coerceIn(0, len)
                val localEnd = (span.end - baseOffset).coerceIn(0, len)
                if (localStart < localEnd) {
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
                        start = localStart,
                        end = localEnd
                    )
                }
            }

            // Style inline per-line checkboxes (`☐ ` and `☑ `) and bullets (`• `) on the same line
            var lineStart = 0
            while (lineStart <= len) {
                val lineEnd = findLineEnd(raw, lineStart)
                val parsed = getParsedLineAt(raw, lineStart)
                if (parsed.hasCheckbox) {
                    addStyle(
                        style = SpanStyle(
                            color = AuroraRoyalViolet,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        start = lineStart,
                        end = (lineStart + 1).coerceAtMost(len)
                    )
                    if (parsed.checkboxPrefix == CHECKED_BOX_PREFIX && lineStart + parsed.totalPrefixLength < lineEnd) {
                        addStyle(
                            style = SpanStyle(
                                color = AuroraRoyalViolet.copy(alpha = 0.58f),
                                textDecoration = TextDecoration.LineThrough
                            ),
                            start = lineStart + parsed.totalPrefixLength,
                            end = lineEnd
                        )
                    }
                }
                if (parsed.hasBullet) {
                    val bulletPos = lineStart + (if (parsed.hasCheckbox) 2 else 0)
                    if (bulletPos < len) {
                        addStyle(
                            style = SpanStyle(
                                color = AuroraRoyalViolet,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            start = bulletPos,
                            end = (bulletPos + 1).coerceAtMost(len)
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
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    note: Note,
    userTags: List<String> = emptyList(),
    onSaveAndClose: (Note) -> Unit,
    onSaveInPlace: (Note) -> Unit = {},
    onDiscardAndClose: () -> Unit = {},
    onUnarchiveNote: ((String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    onDeleteTagGlobal: ((String) -> Unit)? = null,
    onPredictiveBackProgress: (progress: Float, swipeEdge: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isReadOnly = note.isArchived
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var isPinned by remember(note.id) { mutableStateOf(note.isPinned) }
    val tags = remember(note.id) { mutableStateListOf(*note.tags.toTypedArray()) }
    val checklistItems = remember(note.id) { mutableStateListOf(*note.checklist.toTypedArray()) }
    val noteImages = remember(note.id) { mutableStateListOf(*note.images.toTypedArray()) }
    var activeImageMenuId by remember(note.id) { mutableStateOf<String?>(null) }

    // Save indicator & anti-spam clean state for the top-bar Checkmark button
    var isSavedClean by remember(note.id) { mutableStateOf(false) }
    var showSavedIndicator by remember(note.id) { mutableStateOf(false) }

    fun markNoteDirty() {
        isSavedClean = false
        showSavedIndicator = false
    }

    var newChecklistInput by remember { mutableStateOf("") }
    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    // Default size is SMALL (normal), and NO size button is highlighted by default
    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var activeSizeMode by remember { mutableStateOf(TextSizeMode.SMALL) }

    // Explicit active / inactive toggle states for Bullet List and Checkbox
    var isBulletModeActive by remember(note.id) { mutableStateOf(false) }
    var isCheckboxModeActive by remember(note.id) { mutableStateOf(false) }
    var lastSyncedLineStart by remember(note.id) { mutableIntStateOf(-1) }
    var activeSegIndex by remember(note.id) { mutableIntStateOf(0) }
    var pendingToolbarCursorOverride by remember(note.id) { mutableIntStateOf(-1) }
    var pendingToolbarTimestamp by remember(note.id) { mutableLongStateOf(0L) }

    var bodyValue by remember(note.id) {
        val canonicalInitialContent = note.content.replace(
            Regex("(?<!\\n)(\\[\\[AURORA_IMG:[^\\]]+\\]\\])"),
            "\n$1"
        )
        mutableStateOf(
            TextFieldValue(
                text = canonicalInitialContent,
                selection = TextRange(canonicalInitialContent.length)
            )
        )
    }

    var charSpans by remember(note.id) {
        mutableStateOf(parseSpansJson(note.styleSpansJson, bodyValue.text.length))
    }

    // Sync active/inactive button states whenever the cursor line or its prefix changes
    LaunchedEffect(bodyValue.selection.start, bodyValue.text) {
        val safeCursor = bodyValue.selection.start.coerceIn(0, bodyValue.text.length)
        val lineStart = findLineStart(bodyValue.text, safeCursor)
        lastSyncedLineStart = lineStart
        val parsed = getParsedLineAt(bodyValue.text, lineStart)
        isCheckboxModeActive = parsed.hasCheckbox
        isBulletModeActive = parsed.hasBullet
    }

    val editorScrollState = rememberScrollState()
    val bodyFocusRequester = remember { FocusRequester() }
    val segFocusRequesters = remember(note.id, noteImages.size) {
        List(noteImages.size + 1) { FocusRequester() }
    }

    fun requestActiveEditorFocus() {
        if (noteImages.isEmpty()) {
            runCatching { bodyFocusRequester.requestFocus() }
        } else {
            val targetIdx = activeSegIndex.coerceIn(0, segFocusRequesters.lastIndex)
            runCatching { segFocusRequesters[targetIdx].requestFocus() }
        }
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var bodyTopInColumnPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val checkboxHitWidthPx = with(density) { 34.dp.toPx() }
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

    // Native Android File Picker for inserting lossless images into the note
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { pickedUri: Uri? ->
        if (pickedUri != null && !isReadOnly) {
            val savedPath = PrivateStorageManager.saveLosslessNoteImage(context, pickedUri)
            if (savedPath != null) {
                val newImg = NoteImage(
                    id = UUID.randomUUID().toString(),
                    uri = savedPath,
                    isExpanded = false
                )
                noteImages.add(newImg)
                val marker = "[[AURORA_IMG:${newImg.id}]]"
                val curText = bodyValue.text
                val curCursor = bodyValue.selection.end.coerceIn(0, curText.length)
                val lineEnd = findLineEnd(curText, findLineStart(curText, curCursor))
                val prefixBefore = if (lineEnd == 0 || curText[lineEnd - 1] != '\n') "\n" else ""
                val suffixAfter = "\n"
                val inserted = "$prefixBefore$marker$suffixAfter"
                val updatedText = curText.substring(0, lineEnd) + inserted + curText.substring(lineEnd)
                val newCursorPos = (lineEnd + inserted.length).coerceIn(0, updatedText.length)
                charSpans = applyTextEditToSpans(
                    oldText = curText,
                    newText = updatedText,
                    oldSpans = charSpans,
                    activeSizeMode = TextSizeMode.SMALL,
                    isBoldActive = false,
                    isItalicActive = false
                )
                bodyValue = TextFieldValue(
                    text = updatedText,
                    selection = TextRange(newCursorPos)
                )
                activeSegIndex = noteImages.size
                markNoteDirty()
            }
        }
    }

    val detectedBodyLinks = remember(bodyValue.text) {
        LinkHelper.extractLinks(bodyValue.text)
    }

    val wordLetterStats = remember(title, bodyValue.text) {
        PrivateStorageManager.computeWordAndLetterCounts(title, bodyValue.text)
    }

    // Value-based VisualTransformation keeps IME typing 100% lag-free and immediately reflects charSpans updates
    val richVisualTransformation = remember(charSpans) {
        RichNoteVisualTransformation(spans = charSpans.toList(), baseOffset = 0)
    }

    fun handleExitEditor() {
        onPredictiveBackProgress(0f, 0)
        if (isReadOnly) {
            onDiscardAndClose()
            return
        }
        val cleanTitle = title.trim()
        val cleanBody = bodyValue.text
            .replace(Regex("\\[\\[AURORA_IMG:[^\\]]+\\]\\]"), "")
            .trim()
        val validChecklists = checklistItems.filter { it.text.trim().isNotEmpty() }

        // If user created a note and typed nothing in title, body, checklist, or images, do not save it
        if (cleanTitle.isEmpty() && cleanBody.isEmpty() && validChecklists.isEmpty() && noteImages.isEmpty()) {
            onDiscardAndClose()
            return
        }

        val updatedNote = note.copy(
            title = cleanTitle,
            content = bodyValue.text,
            styleSpansJson = if (charSpans.isEmpty()) "" else spanGson.toJson(charSpans),
            checklist = validChecklists,
            images = noteImages.toList(),
            tags = tags.toList(),
            category = tags.firstOrNull() ?: "",
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
        onSaveAndClose(updatedNote)
    }

    fun handleSaveStayInEditor() {
        if (isReadOnly || isSavedClean) return

        // Close user keyboard and clear focus while staying in the Note Editor
        focusManager.clearFocus()
        keyboardController?.hide()
        activeImageMenuId = null

        val cleanTitle = title.trim()
        val cleanBody = bodyValue.text
            .replace(Regex("\\[\\[AURORA_IMG:[^\\]]+\\]\\]"), "")
            .trim()
        val validChecklists = checklistItems.filter { it.text.trim().isNotEmpty() }

        if (cleanTitle.isNotEmpty() || cleanBody.isNotEmpty() || validChecklists.isNotEmpty() || noteImages.isNotEmpty()) {
            val updatedNote = note.copy(
                title = cleanTitle,
                content = bodyValue.text,
                styleSpansJson = if (charSpans.isEmpty()) "" else spanGson.toJson(charSpans),
                checklist = validChecklists,
                images = noteImages.toList(),
                tags = tags.toList(),
                category = tags.firstOrNull() ?: "",
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            )
            onSaveInPlace(updatedNote)
        }

        isSavedClean = true
        showSavedIndicator = true
    }

    val isSubDialogOpen = showAddChecklistDialog || pendingDeleteTag != null

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
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { handleExitEditor() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isReadOnly) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Archived",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            color = AuroraRoyalViolet
                                        )
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (tags.isNotEmpty()) "#${tags.first().take(10)}" else "Tag",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Compact Live Word & Letter Counter Pill on Top of Note Editor
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuroraRoyalViolet.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = "${wordLetterStats.words}w • ${wordLetterStats.letters}l",
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    color = AuroraRoyalViolet
                                ),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isReadOnly) {
                            if (onUnarchiveNote != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AuroraRoyalViolet,
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clickable {
                                            onUnarchiveNote(note.id)
                                            onDiscardAndClose()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Unarchive,
                                            contentDescription = "Restore Note",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Restore",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            if (onDeleteNote != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AuroraDangerRed,
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clickable {
                                            onDeleteNote(note.id)
                                            onDiscardAndClose()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Note",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        } else {
                            if (showSavedIndicator) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Saved",
                                            maxLines = 1,
                                            softWrap = false,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                            )
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showShareDialog = true },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Note",
                                    tint = AuroraRoyalViolet,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Checkmark Icon Button: saves note in place without popup, closes keyboard, stays in editor,
                            // and turns grey/disabled after saving until the user changes something in the note.
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSavedClean) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                                } else {
                                    AuroraRoyalViolet
                                },
                                border = if (isSavedClean) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable(enabled = !isSavedClean) { handleSaveStayInEditor() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = if (isSavedClean) "Saved" else "Save Note",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSavedClean) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                                        } else {
                                            Color.White
                                        }
                                    )
                                }
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
                                    val nextBold = !isBoldActive
                                    isBoldActive = nextBold
                                    if (!bodyValue.selection.collapsed) {
                                        val s = bodyValue.selection.min.coerceIn(0, bodyValue.text.length)
                                        val e = bodyValue.selection.max.coerceIn(0, bodyValue.text.length)
                                        charSpans = applyStyleToSelectionRange(
                                            oldSpans = charSpans,
                                            rangeStart = s,
                                            rangeEnd = e,
                                            targetSizeMode = activeSizeMode,
                                            targetBold = nextBold,
                                            targetItalic = isItalicActive
                                        )
                                    }
                                    markNoteDirty()
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
                                    val nextItalic = !isItalicActive
                                    isItalicActive = nextItalic
                                    if (!bodyValue.selection.collapsed) {
                                        val s = bodyValue.selection.min.coerceIn(0, bodyValue.text.length)
                                        val e = bodyValue.selection.max.coerceIn(0, bodyValue.text.length)
                                        charSpans = applyStyleToSelectionRange(
                                            oldSpans = charSpans,
                                            rangeStart = s,
                                            rangeEnd = e,
                                            targetSizeMode = activeSizeMode,
                                            targetBold = isBoldActive,
                                            targetItalic = nextItalic
                                        )
                                    }
                                    markNoteDirty()
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

                        // Compact H2 and H1 Text Size Buttons:
                        // Default is Small (neither H2 nor H1 is enabled by default).
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
                                label = "H2",
                                selected = activeSizeMode == TextSizeMode.MEDIUM,
                                onClick = {
                                    val nextMode = if (activeSizeMode == TextSizeMode.MEDIUM) {
                                        TextSizeMode.SMALL
                                    } else {
                                        TextSizeMode.MEDIUM
                                    }
                                    activeSizeMode = nextMode
                                    if (!bodyValue.selection.collapsed) {
                                        val s = bodyValue.selection.min.coerceIn(0, bodyValue.text.length)
                                        val e = bodyValue.selection.max.coerceIn(0, bodyValue.text.length)
                                        charSpans = applyStyleToSelectionRange(
                                            oldSpans = charSpans,
                                            rangeStart = s,
                                            rangeEnd = e,
                                            targetSizeMode = nextMode,
                                            targetBold = isBoldActive,
                                            targetItalic = isItalicActive
                                        )
                                    }
                                    markNoteDirty()
                                }
                            )
                            TextSizeButton(
                                label = "H1",
                                selected = activeSizeMode == TextSizeMode.LARGE,
                                onClick = {
                                    val nextMode = if (activeSizeMode == TextSizeMode.LARGE) {
                                        TextSizeMode.SMALL
                                    } else {
                                        TextSizeMode.LARGE
                                    }
                                    activeSizeMode = nextMode
                                    if (!bodyValue.selection.collapsed) {
                                        val s = bodyValue.selection.min.coerceIn(0, bodyValue.text.length)
                                        val e = bodyValue.selection.max.coerceIn(0, bodyValue.text.length)
                                        charSpans = applyStyleToSelectionRange(
                                            oldSpans = charSpans,
                                            rangeStart = s,
                                            rangeEnd = e,
                                            targetSizeMode = nextMode,
                                            targetBold = isBoldActive,
                                            targetItalic = isItalicActive
                                        )
                                    }
                                    markNoteDirty()
                                }
                            )
                        }

                        // Current cursor line metadata for Bullet & Checkbox (-1 rule + coexistence)
                        val activeCursor = bodyValue.selection.start.coerceIn(0, bodyValue.text.length)
                        val activeLineStart = findLineStart(bodyValue.text, activeCursor)
                        val activeLineParsed = getParsedLineAt(bodyValue.text, activeLineStart)
                        val isBulletHighlighted = isBulletModeActive || activeLineParsed.hasBullet
                        val isCheckboxHighlighted = isCheckboxModeActive || activeLineParsed.hasCheckbox

                        fun applyLineFormatToggle(toggleBullet: Boolean, toggleCheckbox: Boolean) {
                            if (noteImages.isNotEmpty()) {
                                val markerRegex = Regex("\\n?\\[\\[AURORA_IMG:([^\\]]+)\\]\\]\\n?")
                                val matches = markerRegex.findAll(bodyValue.text).toList()
                                val segList = ArrayList<String>()
                                var lastIdx = 0
                                for (m in matches) {
                                    segList.add(bodyValue.text.substring(lastIdx, m.range.first))
                                    lastIdx = m.range.last + 1
                                }
                                segList.add(bodyValue.text.substring(lastIdx))
                                while (segList.size <= noteImages.size) {
                                    segList.add("")
                                }

                                val targetSegIdx = activeSegIndex.coerceIn(0, segList.lastIndex)
                                var segOffsetStart = 0
                                for (i in 0 until targetSegIdx) {
                                    segOffsetStart += segList[i].length + "\n[[AURORA_IMG:${noteImages[i].id}]]\n".length
                                }
                                val segText = segList[targetSegIdx]
                                val localCursor = (bodyValue.selection.start - segOffsetStart).coerceIn(0, segText.length)
                                val lineStart = findLineStart(segText, localCursor)
                                val lineEnd = findLineEnd(segText, lineStart)
                                val parsed = getParsedLineAt(segText, lineStart)

                                val nextBulletState = if (toggleBullet) !parsed.hasBullet else parsed.hasBullet
                                val nextCheckboxState = if (toggleCheckbox) !parsed.hasCheckbox else parsed.hasCheckbox
                                isBulletModeActive = nextBulletState
                                isCheckboxModeActive = nextCheckboxState

                                val newPrefix = (if (nextCheckboxState) {
                                    if (parsed.hasCheckbox) parsed.checkboxPrefix else UNCHECKED_BOX_PREFIX
                                } else "") + (if (nextBulletState) BULLET_LINE_PREFIX else "")

                                val rebuiltLine = newPrefix + parsed.cleanBody
                                val newSegText = segText.substring(0, lineStart) + rebuiltLine + segText.substring(lineEnd)
                                val offsetInBody = (localCursor - (lineStart + parsed.totalPrefixLength)).coerceIn(0, parsed.cleanBody.length)
                                val targetInBody = if (localCursor >= lineEnd - 1) parsed.cleanBody.length else offsetInBody
                                val newLocalCursor = (lineStart + newPrefix.length + targetInBody).coerceIn(0, newSegText.length)

                                segList[targetSegIdx] = newSegText
                                val sb = StringBuilder()
                                var newGlobalCursor = 0
                                for (i in segList.indices) {
                                    if (i == targetSegIdx) {
                                        newGlobalCursor = sb.length + newLocalCursor
                                    }
                                    sb.append(segList[i])
                                    if (i < noteImages.size) {
                                        sb.append("\n[[AURORA_IMG:${noteImages[i].id}]]\n")
                                    }
                                }
                                val fullNewText = sb.toString()
                                val clampedGlobal = clampSelectionToMinusOneRule(
                                    fullNewText,
                                    TextRange(newGlobalCursor.coerceIn(0, fullNewText.length))
                                )
                                pendingToolbarCursorOverride = clampedGlobal.start
                                pendingToolbarTimestamp = System.currentTimeMillis()
                                charSpans = applyTextEditToSpans(
                                    oldText = bodyValue.text,
                                    newText = fullNewText,
                                    oldSpans = charSpans,
                                    activeSizeMode = TextSizeMode.SMALL,
                                    isBoldActive = false,
                                    isItalicActive = false
                                )
                                bodyValue = TextFieldValue(
                                    text = fullNewText,
                                    selection = clampedGlobal,
                                    composition = null
                                )
                                requestActiveEditorFocus()
                                markNoteDirty()
                            } else {
                                val text = bodyValue.text
                                val cursor = bodyValue.selection.start.coerceIn(0, text.length)
                                val lineStart = findLineStart(text, cursor)
                                val lineEnd = findLineEnd(text, lineStart)
                                val parsed = getParsedLineAt(text, lineStart)

                                val nextBulletState = if (toggleBullet) !parsed.hasBullet else parsed.hasBullet
                                val nextCheckboxState = if (toggleCheckbox) !parsed.hasCheckbox else parsed.hasCheckbox
                                isBulletModeActive = nextBulletState
                                isCheckboxModeActive = nextCheckboxState

                                val newPrefix = (if (nextCheckboxState) {
                                    if (parsed.hasCheckbox) parsed.checkboxPrefix else UNCHECKED_BOX_PREFIX
                                } else "") + (if (nextBulletState) BULLET_LINE_PREFIX else "")

                                val rebuiltLine = newPrefix + parsed.cleanBody
                                val newText = text.substring(0, lineStart) + rebuiltLine + text.substring(lineEnd)
                                val offsetInBody = (cursor - (lineStart + parsed.totalPrefixLength)).coerceIn(0, parsed.cleanBody.length)
                                val targetInBody = if (cursor >= lineEnd - 1) parsed.cleanBody.length else offsetInBody
                                val newCursor = (lineStart + newPrefix.length + targetInBody).coerceIn(0, newText.length)
                                val clampedSel = clampSelectionToMinusOneRule(newText, TextRange(newCursor))

                                pendingToolbarCursorOverride = clampedSel.start
                                pendingToolbarTimestamp = System.currentTimeMillis()

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
                                    selection = clampedSel,
                                    composition = null
                                )
                                requestActiveEditorFocus()
                                markNoteDirty()
                            }
                        }

                        // Toggle Bullet List (Active / Inactive state; continues on Enter until disabled)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBulletHighlighted) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isBulletHighlighted) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(36.dp)
                                .clickable {
                                    applyLineFormatToggle(toggleBullet = true, toggleCheckbox = false)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Bullet List",
                                    tint = if (isBulletHighlighted) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Toggle Checkbox (Active / Inactive state; coexists with Bullet List on the same line and can always be disabled)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCheckboxHighlighted) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isCheckboxHighlighted) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(36.dp)
                                .clickable {
                                    applyLineFormatToggle(toggleBullet = false, toggleCheckbox = true)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckBox,
                                    contentDescription = "Toggle Line Checkbox",
                                    tint = if (isCheckboxHighlighted) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Insert Image Button (Gallery Icon -> Opens Native Android File Picker)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .width(36.dp)
                                .clickable {
                                    imagePickerLauncher.launch("image/*")
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Insert Image",
                                    tint = MaterialTheme.colorScheme.onSurface,
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
                    onValueChange = {
                        if (!isReadOnly) {
                            title = it
                            markNoteDirty()
                        }
                    },
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
                        .padding(top = 8.dp, bottom = 4.dp)
                )

                // Last Edited Timestamp with Day, Month, Hour, and Minute
                val editorDateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
                val formattedLastEdited = remember(note.updatedAt) {
                    editorDateFormat.format(Date(note.updatedAt))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edited $formattedLastEdited",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }

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
                                            markNoteDirty()
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
                                        onClick = {
                                            checklistItems.removeAt(idx)
                                            markNoteDirty()
                                        },
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

                // Segmented Note Body + Dedicated Lossless Image Blocks:
                // Users can only write ABOVE and UNDER an image (never behind or next to it).
                // Clicking an image brings up a small window next to the pushed image with Resize (extends downward) and Delete (removes image and pulls text up).
                if (noteImages.isEmpty()) {
                    BasicTextField(
                        value = bodyValue,
                        onValueChange = { newValue ->
                            activeImageMenuId = null
                            val oldText = bodyValue.text
                            val newText = newValue.text

                            if (oldText == newText && newValue.selection.collapsed && newValue.selection != bodyValue.selection) {
                                val tappedOffset = newValue.selection.start.coerceIn(0, newText.length)
                                val tappedLineStart = findLineStart(newText, tappedOffset)
                                val parsed = getParsedLineAt(newText, tappedLineStart)

                                if (!isReadOnly && parsed.hasCheckbox && tappedOffset in tappedLineStart..(tappedLineStart + 1)) {
                                    val isCurrentlyChecked = newText.startsWith(CHECKED_BOX_PREFIX, tappedLineStart)
                                    val replacementPrefix = if (isCurrentlyChecked) UNCHECKED_BOX_PREFIX else CHECKED_BOX_PREFIX
                                    val toggledText = newText.substring(0, tappedLineStart) +
                                        replacementPrefix +
                                        newText.substring(tappedLineStart + 2)
                                    bodyValue = TextFieldValue(
                                        text = toggledText,
                                        selection = TextRange((tappedLineStart + parsed.totalPrefixLength).coerceAtMost(toggledText.length))
                                    )
                                    markNoteDirty()
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

                            val effectiveSelection = if (
                                oldText == newText &&
                                pendingToolbarCursorOverride >= 0 &&
                                (System.currentTimeMillis() - pendingToolbarTimestamp) < 350L
                            ) {
                                TextRange(pendingToolbarCursorOverride.coerceIn(0, newText.length))
                            } else {
                                newValue.selection
                            }

                            val (normalizedText, clampedSelection) = normalizeCheckboxLinesAfterEdit(
                                oldText = oldText,
                                newText = newText,
                                newSelection = effectiveSelection,
                                isCheckboxModeActive = isCheckboxModeActive,
                                isBulletModeActive = isBulletModeActive
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
                                markNoteDirty()
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
                            .focusRequester(bodyFocusRequester)
                            .checkboxPrefixTapDetector(
                                enabled = !isReadOnly,
                                hitWidthPx = checkboxHitWidthPx,
                                getText = { bodyValue.text },
                                getLayout = { textLayoutResult },
                                onToggleLineCheckbox = { tappedLineStart, parsed ->
                                    val curText = bodyValue.text
                                    if (tappedLineStart + 2 <= curText.length) {
                                        val isCurrentlyChecked = curText.startsWith(CHECKED_BOX_PREFIX, tappedLineStart)
                                        val replacementPrefix = if (isCurrentlyChecked) UNCHECKED_BOX_PREFIX else CHECKED_BOX_PREFIX
                                        val toggledText = curText.substring(0, tappedLineStart) +
                                            replacementPrefix +
                                            curText.substring(tappedLineStart + 2)
                                        bodyValue = TextFieldValue(
                                            text = toggledText,
                                            selection = TextRange((tappedLineStart + parsed.totalPrefixLength).coerceAtMost(toggledText.length))
                                        )
                                        markNoteDirty()
                                    }
                                }
                            )
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
                } else {
                    // Split note content by [[AURORA_IMG:<id>]] markers so text is strictly ABOVE and UNDER each image
                    val markerRegex = remember { Regex("\\n?\\[\\[AURORA_IMG:([^\\]]+)\\]\\]\\n?") }
                    val matches = remember(bodyValue.text) { markerRegex.findAll(bodyValue.text).toList() }
                    val textSegments = remember(bodyValue.text, noteImages.size) {
                        val list = ArrayList<String>()
                        var lastIdx = 0
                        for (m in matches) {
                            list.add(bodyValue.text.substring(lastIdx, m.range.first))
                            lastIdx = m.range.last + 1
                        }
                        list.add(bodyValue.text.substring(lastIdx))
                        while (list.size <= noteImages.size) {
                            list.add("")
                        }
                        list
                    }

                    val segmentStartOffsets = remember(textSegments, noteImages.toList()) {
                        val offsets = IntArray(textSegments.size)
                        var running = 0
                        for (i in textSegments.indices) {
                            offsets[i] = running
                            running += textSegments[i].length
                            if (i < noteImages.size) {
                                running += "\n[[AURORA_IMG:${noteImages[i].id}]]\n".length
                            }
                        }
                        offsets
                    }

                    fun rebuildBodyWithSegments(updatedSegments: List<String>, activeSegIdx: Int, segCursor: Int) {
                        val oldFull = bodyValue.text
                        val sb = StringBuilder()
                        var globalCursor = 0
                        for (i in updatedSegments.indices) {
                            if (i == activeSegIdx) {
                                globalCursor = sb.length + segCursor.coerceIn(0, updatedSegments[i].length)
                            }
                            sb.append(updatedSegments[i])
                            if (i < noteImages.size) {
                                val imgId = noteImages[i].id
                                sb.append("\n[[AURORA_IMG:$imgId]]\n")
                            }
                        }
                        val full = sb.toString()
                        if (oldFull != full) {
                            charSpans = applyTextEditToSpans(
                                oldText = oldFull,
                                newText = full,
                                oldSpans = charSpans,
                                activeSizeMode = activeSizeMode,
                                isBoldActive = isBoldActive,
                                isItalicActive = isItalicActive
                            )
                            markNoteDirty()
                        }
                        bodyValue = TextFieldValue(
                            text = full,
                            selection = TextRange(globalCursor.coerceIn(0, full.length))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                bodyTopInColumnPx = coords.positionInParent().y
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (segIdx in 0..noteImages.size) {
                            val segText = textSegments.getOrElse(segIdx) { "" }
                            val segStartOffset = segmentStartOffsets.getOrElse(segIdx) { 0 }
                            val segVisualTransformation = remember(charSpans, segStartOffset) {
                                RichNoteVisualTransformation(spans = charSpans.toList(), baseOffset = segStartOffset)
                            }
                            var segLayoutResult by remember(note.id, segIdx, noteImages.size) {
                                mutableStateOf<TextLayoutResult?>(null)
                            }
                            var segFieldValue by remember(note.id, segIdx, noteImages.size) {
                                mutableStateOf(TextFieldValue(text = segText, selection = TextRange(segText.length)))
                            }
                            if (segFieldValue.text != segText) {
                                val desiredLocalCursor = if (segIdx == activeSegIndex) {
                                    (bodyValue.selection.start - segStartOffset).coerceIn(0, segText.length)
                                } else {
                                    segFieldValue.selection.start.coerceIn(0, segText.length)
                                }
                                segFieldValue = TextFieldValue(
                                    text = segText,
                                    selection = clampSelectionToMinusOneRule(
                                        segText,
                                        TextRange(desiredLocalCursor)
                                    ),
                                    composition = null
                                )
                            }

                            // Editable text block ABOVE / UNDER the image
                            BasicTextField(
                                value = segFieldValue,
                                onValueChange = { newSegVal ->
                                    activeImageMenuId = null
                                    if (isReadOnly) return@BasicTextField
                                    activeSegIndex = segIdx
                                    val oldSegText = segFieldValue.text
                                    val effectiveSegSelection = if (
                                        oldSegText == newSegVal.text &&
                                        pendingToolbarCursorOverride >= 0 &&
                                        (System.currentTimeMillis() - pendingToolbarTimestamp) < 350L
                                    ) {
                                        TextRange((pendingToolbarCursorOverride - segStartOffset).coerceIn(0, newSegVal.text.length))
                                    } else {
                                        newSegVal.selection
                                    }
                                    val (normSeg, clampedSegSel) = normalizeCheckboxLinesAfterEdit(
                                        oldText = oldSegText,
                                        newText = newSegVal.text,
                                        newSelection = effectiveSegSelection,
                                        isCheckboxModeActive = isCheckboxModeActive,
                                        isBulletModeActive = isBulletModeActive
                                    )
                                    segFieldValue = newSegVal.copy(text = normSeg, selection = clampedSegSel)
                                    val updatedList = textSegments.toMutableList()
                                    if (segIdx < updatedList.size) {
                                        updatedList[segIdx] = normSeg
                                    }
                                    rebuildBodyWithSegments(updatedList, segIdx, clampedSegSel.start)
                                },
                                readOnly = isReadOnly,
                                singleLine = false,
                                onTextLayout = { segLayoutResult = it },
                                visualTransformation = segVisualTransformation,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
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
                                            .defaultMinSize(minHeight = if (segIdx == noteImages.size) 160.dp else 32.dp)
                                    ) {
                                        if (segFieldValue.text.isEmpty() && !isReadOnly) {
                                            Text(
                                                text = if (segIdx == 0) "Write above image..." else "Write under image...",
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { mod ->
                                        if (segIdx in segFocusRequesters.indices) {
                                            mod.focusRequester(segFocusRequesters[segIdx])
                                        } else {
                                            mod
                                        }
                                    }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            activeSegIndex = segIdx
                                            val globalPos = (segStartOffset + segFieldValue.selection.start.coerceIn(0, segFieldValue.text.length))
                                                .coerceIn(0, bodyValue.text.length)
                                            if (bodyValue.selection.start != globalPos) {
                                                bodyValue = bodyValue.copy(selection = TextRange(globalPos))
                                            }
                                        }
                                    }
                                    .checkboxPrefixTapDetector(
                                        enabled = !isReadOnly,
                                        hitWidthPx = checkboxHitWidthPx,
                                        getText = { segFieldValue.text },
                                        getLayout = { segLayoutResult },
                                        onToggleLineCheckbox = { tappedLineStart, parsed ->
                                            val curSegText = segFieldValue.text
                                            if (tappedLineStart + 2 <= curSegText.length) {
                                                activeSegIndex = segIdx
                                                val isCurrentlyChecked = curSegText.startsWith(CHECKED_BOX_PREFIX, tappedLineStart)
                                                val replacementPrefix = if (isCurrentlyChecked) UNCHECKED_BOX_PREFIX else CHECKED_BOX_PREFIX
                                                val toggledSeg = curSegText.substring(0, tappedLineStart) +
                                                    replacementPrefix +
                                                    curSegText.substring(tappedLineStart + 2)
                                                val nextCursor = (tappedLineStart + parsed.totalPrefixLength).coerceAtMost(toggledSeg.length)
                                                segFieldValue = TextFieldValue(
                                                    text = toggledSeg,
                                                    selection = TextRange(nextCursor)
                                                )
                                                val updatedList = textSegments.toMutableList()
                                                if (segIdx < updatedList.size) {
                                                    updatedList[segIdx] = toggledSeg
                                                }
                                                markNoteDirty()
                                                rebuildBodyWithSegments(updatedList, segIdx, nextCursor)
                                            }
                                        }
                                    )
                            )

                            // Dedicated Image Block (Cannot write behind or next to the image; only above and under it)
                            if (segIdx < noteImages.size) {
                                val imgItem = noteImages[segIdx]
                                val isMenuOpen = activeImageMenuId == imgItem.id
                                val bitmap = remember(imgItem.uri) {
                                    PrivateStorageManager.loadDecryptedNoteImageBitmap(imgItem.uri)?.asImageBitmap()
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Image stays anchored at its first reserved top line and only expands downward
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            if (isMenuOpen) 2.dp else 1.dp,
                                            if (isMenuOpen) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        ),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .let { mod ->
                                                if (imgItem.isExpanded) {
                                                    mod
                                                        .widthIn(min = 180.dp, max = 260.dp)
                                                        .heightIn(min = 160.dp, max = 280.dp)
                                                } else {
                                                    mod
                                                        .width(132.dp)
                                                        .heightIn(max = 132.dp)
                                                }
                                            }
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = !isReadOnly) {
                                                activeImageMenuId = if (isMenuOpen) null else imgItem.id
                                            }
                                    ) {
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "Inserted note image",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .background(AuroraRoyalViolet.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = AuroraRoyalViolet
                                                )
                                            }
                                        }
                                    }

                                    // Small floating window right next to the clicked image (Resize downward/smaller + Delete)
                                    if (isMenuOpen && !isReadOnly) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 6.dp,
                                            shadowElevation = 6.dp,
                                            border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Resize Option: Extends image downward & pushes text under it down; clicking again shrinks back & pulls text up
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                                    modifier = Modifier.clickable {
                                                        noteImages[segIdx] = imgItem.copy(isExpanded = !imgItem.isExpanded)
                                                        markNoteDirty()
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (imgItem.isExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                                            contentDescription = null,
                                                            tint = AuroraRoyalViolet,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = if (imgItem.isExpanded) "Smaller" else "Resize",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = AuroraRoyalViolet
                                                        )
                                                    }
                                                }

                                                // Delete Option: Removes image completely and fills text under it back into the empty space above
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = AuroraDangerRed.copy(alpha = 0.12f),
                                                    modifier = Modifier.clickable {
                                                        val updatedSegments = textSegments.toMutableList()
                                                        val above = updatedSegments.getOrElse(segIdx) { "" }
                                                        val below = updatedSegments.getOrElse(segIdx + 1) { "" }
                                                        val merged = when {
                                                            above.isEmpty() -> below
                                                            below.isEmpty() -> above
                                                            else -> "$above\n$below"
                                                        }
                                                        updatedSegments[segIdx] = merged
                                                        if (segIdx + 1 < updatedSegments.size) {
                                                            updatedSegments.removeAt(segIdx + 1)
                                                        }
                                                        noteImages.removeAt(segIdx)
                                                        activeImageMenuId = null
                                                        markNoteDirty()
                                                        rebuildBodyWithSegments(updatedSegments, segIdx, above.length)
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = null,
                                                            tint = AuroraDangerRed,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Delete",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = AuroraDangerRed
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
            fontWeight = FontWeight.Bold,
            color = if (selected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

