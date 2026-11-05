package com.aurora.notes.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aurora.notes.model.Note
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.NoteShareHelper
import com.aurora.notes.util.PrivateStorageManager
import com.aurora.notes.util.ShareNoteFormat

@Composable
fun ShareNoteDialog(
    notes: List<Note>,
    onDismiss: () -> Unit
) {
    if (notes.isEmpty()) return

    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ShareNoteFormat.MARKDOWN) }

    val plainNotes = remember(notes) {
        notes.map { PrivateStorageManager.decryptNoteFromRoom(it) }
    }

    val individualMdFileNames = remember(plainNotes) {
        NoteShareHelper.buildUniqueNoteFileNames(plainNotes, ShareNoteFormat.MARKDOWN)
    }

    val formattedPreview = remember(plainNotes, selectedFormat, individualMdFileNames) {
        if (plainNotes.size <= 1) {
            NoteShareHelper.formatNotes(plainNotes, selectedFormat, embedBase64Images = false)
        } else {
            plainNotes.mapIndexed { idx, note ->
                "=== File ${idx + 1}: ${individualMdFileNames[idx]} ===\n" +
                    NoteShareHelper.formatSingleNote(note, selectedFormat, embedBase64Images = false).trimEnd()
            }.joinToString("\n\n")
        }
    }

    val fullLengthNoteBitmap = remember(plainNotes, selectedFormat) {
        if (selectedFormat == ShareNoteFormat.NOTE_IMAGE) {
            runCatching {
                NoteShareHelper.renderNotesToFullLengthBitmap(plainNotes).asImageBitmap()
            }.getOrNull()
        } else {
            null
        }
    }

    val suggestedFileName = remember(plainNotes, selectedFormat) {
        NoteShareHelper.buildSuggestedFileName(plainNotes, selectedFormat)
    }

    // Single-file picker for 1 note (or combined PNG)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { targetUri ->
        if (targetUri != null) {
            val ok = NoteShareHelper.writeNoteToChosenUri(
                context = context,
                uri = targetUri,
                notes = plainNotes,
                format = selectedFormat
            )
            if (ok) {
                Toast.makeText(
                    context,
                    "Saved $suggestedFileName to your chosen folder!",
                    Toast.LENGTH_LONG
                ).show()
                onDismiss()
            } else {
                Toast.makeText(
                    context,
                    "Could not write file to selected location.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Folder picker when saving multiple selected notes as separate .md files
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val savedCount = NoteShareHelper.writeSeparateMarkdownNotesToFolderUri(
                context = context,
                treeUri = treeUri,
                notes = plainNotes
            )
            if (savedCount > 0) {
                Toast.makeText(
                    context,
                    "Saved $savedCount separate .md files to your chosen folder!",
                    Toast.LENGTH_LONG
                ).show()
                onDismiss()
            } else {
                Toast.makeText(
                    context,
                    "Could not write .md files to selected folder.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun launchAndroidFolderFilePicker() {
        try {
            if (selectedFormat == ShareNoteFormat.MARKDOWN && plainNotes.size > 1) {
                openFolderLauncher.launch(null)
            } else {
                createDocLauncher.launch(suggestedFileName)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Could not open Android File Picker: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, AuroraRoyalViolet.copy(alpha = 0.4f)),
            tonalElevation = 10.dp,
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (notes.size == 1) "Share & Export Note" else "Share ${notes.size} Selected Notes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "Text-only Markdown (.md) or full-length Gallery image",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format Selector: Markdown (.md) vs Note Image (.png)
                Text(
                    text = "CHOOSE EXPORT FORMAT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuroraRoyalViolet
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShareNoteFormat.entries.forEach { format ->
                        val isSelected = selectedFormat == format
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) AuroraRoyalViolet.copy(alpha = 0.14f) else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = format }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = format.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (format == ShareNoteFormat.MARKDOWN) {
                                            "Note text only (no images)"
                                        } else {
                                            "Full-length Gallery image"
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AuroraRoyalViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // File Name & Live Preview Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedFormat == ShareNoteFormat.NOTE_IMAGE) {
                                Icons.Default.Image
                            } else {
                                Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = AuroraRoyalViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = suggestedFileName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (selectedFormat == ShareNoteFormat.NOTE_IMAGE) {
                            "Full-length PNG"
                        } else {
                            "${formattedPreview.length} chars"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedFormat == ShareNoteFormat.NOTE_IMAGE && fullLengthNoteBitmap != null) {
                        Image(
                            bitmap = fullLengthNoteBitmap,
                            contentDescription = "Full-length note image preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Text(
                            text = formattedPreview,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                if (selectedFormat == ShareNoteFormat.NOTE_IMAGE) {
                    // Primary action for Note Image: Save directly into Android Gallery
                    Button(
                        onClick = {
                            if (NoteShareHelper.saveNoteImageToGallery(context, plainNotes)) {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Note as Image to Android Gallery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            NoteShareHelper.shareToAppsAndContacts(
                                context = context,
                                notes = plainNotes,
                                format = ShareNoteFormat.NOTE_IMAGE,
                                asFileAttachment = true
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Note Image to Apps & Contacts (PNG)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // 1. Share Markdown (.md) with embedded images
                    Button(
                        onClick = {
                            NoteShareHelper.shareToAppsAndContacts(
                                context = context,
                                notes = plainNotes,
                                format = selectedFormat,
                                asFileAttachment = true
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (plainNotes.size > 1) {
                                "Share ${plainNotes.size} Separate .MD Files"
                            } else {
                                "Share Note Markdown (.MD)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Save to Preferred Folder inside Android File Picker
                    OutlinedButton(
                        onClick = { launchAndroidFolderFilePicker() },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (plainNotes.size > 1) {
                                "Save ${plainNotes.size} .MD Files to Folder (File Picker)"
                            } else {
                                "Save Markdown to Folder (Android File Picker)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
