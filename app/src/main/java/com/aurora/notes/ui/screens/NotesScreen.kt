package com.aurora.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.model.Note
import com.aurora.notes.ui.components.NoteCard
import com.aurora.notes.ui.components.ShareNoteDialog
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    userTags: List<String> = emptyList(),
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onSelectNote: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    onDeleteNotesBatch: (List<String>) -> Unit = { ids -> ids.forEach(onDeleteNote) },
    onToggleCheckItem: (String, String) -> Unit,
    onAddNote: () -> Unit,
    onCreateTag: (String) -> Unit = {},
    onDeleteTag: ((String) -> Unit)? = null,
    isPinnedTab: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Combine persisted user-created tags + any tags present on notes
    val createdTags = remember(userTags, notes) {
        val fromNotes = notes.flatMap { it.tags }
        (userTags + fromNotes)
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }

    var showCreateTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    // Multi-selection state (Hold down on note to select multiple notes)
    val selectedNoteIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // Share dialog state
    var notesToShare by remember { mutableStateOf<List<Note>>(emptyList()) }

    // Prune any selected IDs that are no longer in the visible notes list
    LaunchedEffect(notes) {
        val currentIds = notes.map { it.id }.toSet()
        selectedNoteIds.removeAll { it !in currentIds }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedNoteIds.clear()
    }

    if (notesToShare.isNotEmpty()) {
        ShareNoteDialog(
            notes = notesToShare,
            onDismiss = { notesToShare = emptyList() }
        )
    }

    if (showBatchDeleteConfirm && selectedNoteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete ${selectedNoteIds.size} Selected ${if (selectedNoteIds.size == 1) "Note" else "Notes"}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Move ${selectedNoteIds.size} selected ${if (selectedNoteIds.size == 1) "note" else "notes"} to Trash? You can restore them later from the Trash tab.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedNoteIds.toList()
                        showBatchDeleteConfirm = false
                        selectedNoteIds.clear()
                        onDeleteNotesBatch(idsToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Text("Delete (${selectedNoteIds.size})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateTagDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateTagDialog = false
                newTagName = ""
            },
            title = {
                Text("Create New Tag", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    placeholder = { Text("Tag name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTagName.trim().removePrefix("#").trim()
                        if (clean.isNotBlank() && !clean.equals("All", ignoreCase = true)) {
                            onCreateTag(clean)
                            onSelectCategory(clean)
                        }
                        newTagName = ""
                        showCreateTagDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraRoyalViolet)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateTagDialog = false
                        newTagName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingDeleteTag != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Delete Tag #${pendingDeleteTag}?") },
            text = {
                Text(
                    "Deleting #${pendingDeleteTag} will remove the tag and return any notes under it back into All without deleting the notes.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tag = pendingDeleteTag
                        pendingDeleteTag = null
                        if (tag != null) {
                            if (selectedCategory.equals(tag, ignoreCase = true)) {
                                onSelectCategory("All")
                            }
                            onDeleteTag?.invoke(tag)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header Title
        Text(
            text = if (isPinnedTab) "Pinned Notes" else "Aurora Notes",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        )
        Text(
            text = if (isPinnedTab) "Quick access to your pinned high-priority notes." else "Local Only Privacy Focused Note Taking App.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Multi-Select Action Bar (when user holds down on notes to select multiple)
        if (isSelectionMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AuroraRoyalViolet.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, AuroraRoyalViolet),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selectedNoteIds.clear() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedNoteIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AuroraRoyalViolet
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Select All / Deselect All
                        TextButton(
                            onClick = {
                                if (selectedNoteIds.size == notes.size) {
                                    selectedNoteIds.clear()
                                } else {
                                    selectedNoteIds.clear()
                                    selectedNoteIds.addAll(notes.map { it.id })
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select All",
                                tint = AuroraRoyalViolet,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedNoteIds.size == notes.size) "None" else "All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuroraRoyalViolet
                            )
                        }

                        // Share Selected
                        FilledTonalButton(
                            onClick = {
                                val selectedNotes = notes.filter { it.id in selectedNoteIds }
                                if (selectedNotes.isNotEmpty()) {
                                    notesToShare = selectedNotes
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AuroraRoyalViolet,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Selected",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Delete Selected
                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Search Bar & Grid/List Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search notes...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onToggleGridView,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View Layout"
                )
            }

            IconButton(
                onClick = onAddNote,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Note",
                    tint = AuroraRoyalViolet
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tag Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. "All" Button
            val isAllSelected = selectedCategory.equals("All", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAllSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isAllSelected) AuroraRoyalViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier.clickable {
                    onSelectCategory("All")
                }
            ) {
                Text(
                    text = "All",
                    fontSize = 12.sp,
                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            // 2. "+ Create Tag" Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AuroraRoyalViolet.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, AuroraRoyalViolet.copy(alpha = 0.45f)),
                modifier = Modifier.clickable {
                    showCreateTagDialog = true
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Tag",
                        tint = AuroraRoyalViolet,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Create Tag",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuroraRoyalViolet
                    )
                }
            }

            // 3. User-Created Tags (long-tap to delete)
            createdTags.forEach { tag ->
                val isTagSelected = selectedCategory.equals(tag, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTagSelected) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.10f),
                    border = BorderStroke(
                        1.dp,
                        if (isTagSelected) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (isTagSelected) {
                                onSelectCategory("All")
                            } else {
                                onSelectCategory(tag)
                            }
                        },
                        onLongClick = {
                            pendingDeleteTag = tag
                        }
                    )
                ) {
                    Text(
                        text = "#$tag",
                        fontSize = 12.sp,
                        fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTagSelected) Color.White else AuroraRoyalViolet,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Notes Grid / List
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No matching notes found"
                            isPinnedTab -> "No pinned notes yet"
                            else -> "No notes yet"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Try clearing your search query"
                            isPinnedTab -> "Pin any note to keep it at the top of your workspace"
                            else -> "Tap + to create your first note"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { note ->
                    val isNoteSelected = note.id in selectedNoteIds
                    NoteCard(
                        note = note,
                        isSelectionMode = isSelectionMode,
                        isSelected = isNoteSelected,
                        onClick = {
                            if (isSelectionMode) {
                                if (isNoteSelected) {
                                    selectedNoteIds.remove(note.id)
                                } else {
                                    selectedNoteIds.add(note.id)
                                }
                            } else {
                                onSelectNote(note)
                            }
                        },
                        onLongClick = {
                            if (!isNoteSelected) {
                                selectedNoteIds.add(note.id)
                            } else {
                                selectedNoteIds.remove(note.id)
                            }
                        },
                        onTogglePin = { onTogglePin(note) },
                        onDelete = { onDeleteNote(note.id) },
                        onShare = { notesToShare = listOf(note) },
                        onToggleCheckItem = { itemId -> onToggleCheckItem(note.id, itemId) },
                        onDeleteTag = onDeleteTag,
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(durationMillis = 220),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                fadeOutSpec = tween(durationMillis = 180)
                            )
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                    )
                }
            }
        }
    }
}
