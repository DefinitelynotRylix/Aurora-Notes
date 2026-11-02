package com.aurora.notes.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.model.ChecklistItem
import com.aurora.notes.model.Note
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import java.util.UUID

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

data class EditorLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecklist: Boolean = false,
    val isChecked: Boolean = false,
    val isBullet: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val sizeMode: TextSizeMode = TextSizeMode.SMALL,
    val spans: List<StyledCharSpan> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    note: Note,
    onSaveAndClose: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(note.title) }
    var category by remember { mutableStateOf(note.category.ifBlank { "General" }) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    val tags = remember { mutableStateListOf(*note.tags.toTypedArray()) }
    var newTagInput by remember { mutableStateOf("") }
    var showDoneDetailsDialog by remember { mutableStateOf(false) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var activeSizeMode by remember { mutableStateOf(TextSizeMode.SMALL) }

    val lines = remember {
        mutableStateListOf<EditorLine>().apply {
            if (note.content.isBlank()) {
                add(EditorLine(text = ""))
            } else {
                note.content.lines().forEach { lineText ->
                    val isChecked = lineText.startsWith("[x] ")
                    val isUnchecked = lineText.startsWith("[ ] ")
                    val isChecklist = isChecked || isUnchecked
                    val isBullet = lineText.startsWith("• ")
                    val cleaned = when {
                        isChecked -> lineText.removePrefix("[x] ")
                        isUnchecked -> lineText.removePrefix("[ ] ")
                        isBullet -> lineText.removePrefix("• ")
                        else -> lineText
                    }
                    add(
                        EditorLine(
                            text = cleaned,
                            isChecklist = isChecklist,
                            isChecked = isChecked,
                            isBullet = isBullet,
                            spans = if (cleaned.isNotEmpty()) {
                                listOf(
                                    StyledCharSpan(
                                        start = 0,
                                        end = cleaned.length,
                                        sizeMode = TextSizeMode.SMALL,
                                        isBold = false,
                                        isItalic = false
                                    )
                                )
                            } else emptyList()
                        )
                    )
                }
            }
        }
    }

    var focusedLineIndex by remember { mutableStateOf(0) }
    var shouldRequestFocusOnActiveLine by remember { mutableStateOf(false) }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(focusedLineIndex, shouldRequestFocusOnActiveLine) {
        if (shouldRequestFocusOnActiveLine) {
            val targetLine = lines.getOrNull(focusedLineIndex)
            if (targetLine != null) {
                val requester = focusRequesters.getOrPut(targetLine.id) { FocusRequester() }
                try {
                    requester.requestFocus()
                } catch (_: Exception) {
                }
            }
            shouldRequestFocusOnActiveLine = false
        }
    }

    fun constructUpdatedNote(): Note {
        val serializedContent = lines.joinToString("\n") { line ->
            when {
                line.isChecklist && line.isChecked -> "[x] ${line.text}"
                line.isChecklist -> "[ ] ${line.text}"
                line.isBullet -> "• ${line.text}"
                else -> line.text
            }
        }

        val checklistItems = lines.filter { it.isChecklist }.map { line ->
            ChecklistItem(
                id = line.id,
                text = line.text,
                completed = line.isChecked
            )
        }

        return note.copy(
            title = title.trim().ifBlank { "Untitled Note" },
            content = serializedContent,
            checklist = checklistItems,
            tags = tags.toList(),
            category = category,
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
    }

    BackHandler {
        onSaveAndClose(constructUpdatedNote())
    }

    if (pendingDeleteTag != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Delete Tag #${pendingDeleteTag}?") },
            text = {
                Text(
                    "Remove #${pendingDeleteTag} from this note? The note itself will not be deleted.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteTag?.let { tags.remove(it) }
                        pendingDeleteTag = null
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

    if (showDoneDetailsDialog) {
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

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Tags (Tap or long-press a tag to delete)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AuroraRoyalViolet.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.35f)),
                                modifier = Modifier.combinedClickable(
                                    onClick = { pendingDeleteTag = tag },
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
                                        color = AuroraRoyalViolet
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✕",
                                        fontSize = 11.sp,
                                        color = AuroraDangerRed
                                    )
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
                            placeholder = { Text("New tag...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val clean = newTagInput.trim().removePrefix("#")
                                if (clean.isNotBlank() && !tags.contains(clean)) {
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
                        onSaveAndClose(constructUpdatedNote())
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
                            onClick = { onSaveAndClose(constructUpdatedNote()) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Save and Close")
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = AuroraRoyalViolet,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
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
        },
        bottomBar = {
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Narrower Bold Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBoldActive) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isBoldActive) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .width(34.dp)
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

                    // Narrower Italic Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isItalicActive) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isItalicActive) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .width(34.dp)
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

                    // Text Sizing Buttons (Applies only to newly typed text or empty line)
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
                            label = "Small",
                            selected = activeSizeMode == TextSizeMode.SMALL,
                            onClick = {
                                activeSizeMode = TextSizeMode.SMALL
                                val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                                if (lines[idx].text.isEmpty()) {
                                    lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.SMALL)
                                }
                            }
                        )
                        TextSizeButton(
                            label = "Medium",
                            selected = activeSizeMode == TextSizeMode.MEDIUM,
                            onClick = {
                                activeSizeMode = TextSizeMode.MEDIUM
                                val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                                if (lines[idx].text.isEmpty()) {
                                    lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.MEDIUM)
                                }
                            }
                        )
                        TextSizeButton(
                            label = "Large",
                            selected = activeSizeMode == TextSizeMode.LARGE,
                            onClick = {
                                activeSizeMode = TextSizeMode.LARGE
                                val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                                if (lines[idx].text.isEmpty()) {
                                    lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.LARGE)
                                }
                            }
                        )
                    }

                    FilterChip(
                        selected = lines.getOrNull(focusedLineIndex)?.isBullet == true,
                        onClick = {
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            val current = lines[idx]
                            lines[idx] = current.copy(isBullet = !current.isBullet)
                        },
                        label = { Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List", modifier = Modifier.size(16.dp)) }
                    )

                    val isLineChecklist = lines.getOrNull(focusedLineIndex)?.isChecklist == true
                    FilterChip(
                        selected = isLineChecklist,
                        onClick = {
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            val current = lines[idx]
                            lines[idx] = current.copy(isChecklist = !current.isChecklist)
                        },
                        label = { Icon(Icons.Default.CheckBox, contentDescription = "Checklist", modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
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

            lines.forEachIndexed { index, line ->
                val requester = focusRequesters.getOrPut(line.id) { FocusRequester() }
                val baseFontSize = when (line.sizeMode) {
                    TextSizeMode.SMALL -> 14.sp
                    TextSizeMode.MEDIUM -> 18.sp
                    TextSizeMode.LARGE -> 22.sp
                }
                val textDecoration = if (line.isChecklist && line.isChecked) TextDecoration.LineThrough else TextDecoration.None
                val textColor = if (line.isChecklist && line.isChecked) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                }

                // Build AnnotatedString so only newly typed characters get the new size/bold/italic
                val annotatedText = remember(line.text, line.spans, line.sizeMode) {
                    buildAnnotatedString {
                        append(line.text)
                        line.spans.forEach { span ->
                            val s = span.start.coerceIn(0, line.text.length)
                            val e = span.end.coerceIn(0, line.text.length)
                            if (s < e) {
                                val spanSize = when (span.sizeMode) {
                                    TextSizeMode.SMALL -> 14.sp
                                    TextSizeMode.MEDIUM -> 18.sp
                                    TextSizeMode.LARGE -> 22.sp
                                }
                                addStyle(
                                    style = SpanStyle(
                                        fontSize = spanSize,
                                        fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal
                                    ),
                                    start = s,
                                    end = e
                                )
                            }
                        }
                    }
                }

                var textFieldValue by remember(line.id) {
                    mutableStateOf(
                        TextFieldValue(
                            annotatedString = annotatedText,
                            selection = TextRange(line.text.length)
                        )
                    )
                }

                // Keep TextFieldValue synced with annotatedText
                val currentFieldValue = textFieldValue.copy(annotatedString = annotatedText)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            focusedLineIndex = index
                            shouldRequestFocusOnActiveLine = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (line.isChecklist) {
                        Checkbox(
                            checked = line.isChecked,
                            onCheckedChange = { checked ->
                                lines[index] = lines[index].copy(isChecked = checked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = AuroraRoyalViolet),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 6.dp)
                        )
                    } else if (line.isBullet) {
                        Text(
                            text = "•",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraRoyalViolet,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    BasicTextField(
                        value = currentFieldValue,
                        onValueChange = { newValue ->
                            focusedLineIndex = index
                            val newText = newValue.text
                            if (newText.contains("\n")) {
                                val parts = newText.split("\n", limit = 2)
                                val firstPart = parts[0]
                                val secondPart = parts.getOrNull(1) ?: ""

                                val updatedSpans = line.spans.mapNotNull { sp ->
                                    if (sp.start >= firstPart.length) null
                                    else sp.copy(end = sp.end.coerceAtMost(firstPart.length))
                                }

                                lines[index] = line.copy(
                                    text = firstPart,
                                    spans = updatedSpans
                                )
                                textFieldValue = TextFieldValue(
                                    text = firstPart,
                                    selection = TextRange(firstPart.length)
                                )

                                val newLine = EditorLine(
                                    text = secondPart,
                                    isChecklist = false,
                                    isBullet = false,
                                    isBold = isBoldActive,
                                    isItalic = isItalicActive,
                                    sizeMode = activeSizeMode,
                                    spans = if (secondPart.isNotEmpty()) {
                                        listOf(
                                            StyledCharSpan(
                                                start = 0,
                                                end = secondPart.length,
                                                sizeMode = activeSizeMode,
                                                isBold = isBoldActive,
                                                isItalic = isItalicActive
                                            )
                                        )
                                    } else emptyList()
                                )
                                lines.add(index + 1, newLine)
                                focusedLineIndex = index + 1
                                shouldRequestFocusOnActiveLine = true
                            } else {
                                val oldLen = line.text.length
                                val newLen = newText.length
                                val updatedSpans = if (newLen > oldLen) {
                                    // Append span only for newly typed characters
                                    line.spans + StyledCharSpan(
                                        start = oldLen,
                                        end = newLen,
                                        sizeMode = activeSizeMode,
                                        isBold = isBoldActive,
                                        isItalic = isItalicActive
                                    )
                                } else {
                                    line.spans.mapNotNull { sp ->
                                        if (sp.start >= newLen) null
                                        else sp.copy(end = sp.end.coerceAtMost(newLen))
                                    }
                                }

                                lines[index] = line.copy(
                                    text = newText,
                                    spans = updatedSpans
                                )
                                textFieldValue = newValue
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = baseFontSize,
                            textDecoration = textDecoration,
                            color = textColor
                        ),
                        cursorBrush = SolidColor(AuroraRoyalViolet),
                        decorationBox = { innerTextField ->
                            if (line.text.isEmpty() && index == 0 && lines.size == 1) {
                                Text(
                                    text = "Start typing your note here...",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                    )
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRequester(requester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    focusedLineIndex = index
                                }
                            }
                    )
                }
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
        color = if (selected) AuroraRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, AuroraRoyalViolet) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
