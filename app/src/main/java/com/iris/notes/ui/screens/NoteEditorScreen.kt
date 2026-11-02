package com.iris.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.notes.model.ChecklistItem
import com.iris.notes.model.Note
import com.iris.notes.ui.theme.IrisBorderDark
import com.iris.notes.ui.theme.IrisBorderLight
import com.iris.notes.ui.theme.IrisRoyalViolet
import java.util.UUID

enum class TextSizeMode {
    SMALL,
    MEDIUM,
    LARGE
}

// Internal representation of an editor line in Jetpack Compose
data class EditorLine(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var isChecklist: Boolean = false,
    var isChecked: Boolean = false,
    var isBullet: Boolean = false,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var sizeMode: TextSizeMode = TextSizeMode.SMALL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: Note,
    onSaveAndClose: (Note) -> Unit,
    onInspectCipher: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(note.title) }
    var category by remember { mutableStateOf(note.category.ifBlank { "General" }) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    val tags = remember { mutableStateListOf(*note.tags.toTypedArray()) }
    var newTagInput by remember { mutableStateOf("") }

    // Active formatting states on the toolbar
    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var activeSizeMode by remember { mutableStateOf(TextSizeMode.SMALL) }

    // Convert note content to reactive lines
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
                            isBullet = isBullet
                        )
                    )
                }
            }
        }
    }

    var focusedLineIndex by remember { mutableStateOf(0) }

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

    // Auto-save on Android back gesture or back button
    BackHandler {
        onSaveAndClose(constructUpdatedNote())
    }

    // 100% Maximized Full-Screen Layout
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Back Arrow -> Auto-Save
                        IconButton(
                            onClick = { onSaveAndClose(constructUpdatedNote()) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Save and Close")
                        }

                        // Category Pill
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
                                    tint = IrisRoyalViolet,
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

                        // Pin Toggle
                        IconButton(
                            onClick = { isPinned = !isPinned },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (isPinned) IrisRoyalViolet else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cipher Inspector
                        OutlinedButton(
                            onClick = { onInspectCipher(constructUpdatedNote()) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cipher", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Done Button
                        Button(
                            onClick = { onSaveAndClose(constructUpdatedNote()) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IrisRoyalViolet),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // STICKY KEYBOARD TOOLBAR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thick / Bold
                    FilterChip(
                        selected = isBoldActive,
                        onClick = {
                            isBoldActive = !isBoldActive
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            lines[idx] = lines[idx].copy(isBold = isBoldActive)
                        },
                        label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp)) }
                    )

                    // Italic
                    FilterChip(
                        selected = isItalicActive,
                        onClick = {
                            isItalicActive = !isItalicActive
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            lines[idx] = lines[idx].copy(isItalic = isItalicActive)
                        },
                        label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp)) }
                    )

                    // 3 Distinct Text Size Buttons
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(12.dp)
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
                                lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.SMALL)
                            }
                        )
                        TextSizeButton(
                            label = "Medium",
                            selected = activeSizeMode == TextSizeMode.MEDIUM,
                            onClick = {
                                activeSizeMode = TextSizeMode.MEDIUM
                                val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                                lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.MEDIUM)
                            }
                        )
                        TextSizeButton(
                            label = "Large",
                            selected = activeSizeMode == TextSizeMode.LARGE,
                            onClick = {
                                activeSizeMode = TextSizeMode.LARGE
                                val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                                lines[idx] = lines[idx].copy(sizeMode = TextSizeMode.LARGE)
                            }
                        )
                    }

                    // Outline Bullet List
                    FilterChip(
                        selected = lines.getOrNull(focusedLineIndex)?.isBullet == true,
                        onClick = {
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            val current = lines[idx]
                            lines[idx] = current.copy(isBullet = !current.isBullet)
                        },
                        label = { Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List", modifier = Modifier.size(18.dp)) }
                    )

                    // Checklist Button:
                    // Only 1 checkbox per line at -1 position. Toggling enables/disables on the current line only.
                    val isLineChecklist = lines.getOrNull(focusedLineIndex)?.isChecklist == true
                    FilterChip(
                        selected = isLineChecklist,
                        onClick = {
                            val idx = focusedLineIndex.coerceIn(0, lines.lastIndex)
                            val current = lines[idx]
                            // Toggle checklist on this line only
                            lines[idx] = current.copy(isChecklist = !current.isChecklist)
                        },
                        label = { Icon(Icons.Default.CheckBox, contentDescription = "Checklist", modifier = Modifier.size(18.dp)) }
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
            // Note Title Input
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(IrisRoyalViolet),
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

            // Lines Editor: Each line is rendered distinctly.
            // Checkbox stays strictly at the -1 prefix. Characters cannot be typed before it.
            lines.forEachIndexed { index, line ->
                val fontSize = when (line.sizeMode) {
                    TextSizeMode.SMALL -> 14.sp
                    TextSizeMode.MEDIUM -> 18.sp
                    TextSizeMode.LARGE -> 22.sp
                }
                val fontWeight = if (line.isBold) FontWeight.Bold else FontWeight.Normal
                val fontStyle = if (line.isItalic) FontStyle.Italic else FontStyle.Normal
                val textDecoration = if (line.isChecklist && line.isChecked) TextDecoration.LineThrough else TextDecoration.None
                val textColor = if (line.isChecklist && line.isChecked) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { focusedLineIndex = index },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Protected -1 Character Slot: Checkbox or Bullet
                    if (line.isChecklist) {
                        Checkbox(
                            checked = line.isChecked,
                            onCheckedChange = { checked ->
                                lines[index] = lines[index].copy(isChecked = checked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = IrisRoyalViolet),
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 6.dp)
                        )
                    } else if (line.isBullet) {
                        Text(
                            text = "•",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = IrisRoyalViolet,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    // Editable Text Container (Cursor is strictly after the -1 prefix)
                    BasicTextField(
                        value = line.text,
                        onValueChange = { newText ->
                            focusedLineIndex = index
                            // Handle Enter inside line:
                            // Pressing Enter does NOT delete the checkbox, and creates a NEW plain line with checklist disabled
                            if (newText.contains("\n")) {
                                val parts = newText.split("\n", limit = 2)
                                lines[index] = line.copy(text = parts[0])
                                // New line is standard plain text with checklist disabled
                                val newLine = EditorLine(
                                    text = parts.getOrNull(1) ?: "",
                                    isChecklist = false,
                                    isBullet = false,
                                    isBold = isBoldActive,
                                    isItalic = isItalicActive,
                                    sizeMode = activeSizeMode
                                )
                                lines.add(index + 1, newLine)
                                focusedLineIndex = index + 1
                            } else {
                                lines[index] = line.copy(text = newText)
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            textDecoration = textDecoration,
                            color = textColor
                        ),
                        cursorBrush = SolidColor(IrisRoyalViolet),
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
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tags Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { tags.remove(tag) },
                        label = { Text("#$tag", fontSize = 11.sp) },
                        trailingIcon = { Text("✕", fontSize = 10.sp) }
                    )
                }

                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { input ->
                        if (input.endsWith(",") || input.endsWith("\n")) {
                            val clean = input.trim().removeSuffix(",").removePrefix("#")
                            if (clean.isNotBlank() && !tags.contains(clean)) {
                                tags.add(clean)
                            }
                            newTagInput = ""
                        } else {
                            newTagInput = input
                        }
                    },
                    placeholder = { Text("Add tag...", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 11.sp),
                    modifier = Modifier.width(110.dp)
                )
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
        color = if (selected) IrisRoyalViolet.copy(alpha = 0.15f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, IrisRoyalViolet) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) IrisRoyalViolet else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
