package com.aurora.notes.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
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
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onSelectNote: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    onToggleCheckItem: (String, String) -> Unit,
    onAddNote: () -> Unit,
    onDeleteTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "General", "Ideas", "Personal", "Work")
    val allTags = remember(notes) {
        notes.flatMap { it.tags }.distinct().sorted()
    }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    val displayedNotes = remember(notes, selectedTagFilter) {
        if (selectedTagFilter == null) {
            notes
        } else {
            notes.filter { it.tags.contains(selectedTagFilter) }
        }
    }

    if (pendingDeleteTag != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("Delete Tag #${pendingDeleteTag}?") },
            text = {
                Text(
                    "Deleting this tag will only remove the tag itself and will NOT delete any notes. All notes under #${pendingDeleteTag} will return to a regular tag-less state.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tag = pendingDeleteTag
                        pendingDeleteTag = null
                        if (tag != null) {
                            if (selectedTagFilter == tag) {
                                selectedTagFilter = null
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

        // Header Title (No 'A' icon, clean Aurora Notes header)
        Text(
            text = "Aurora Notes",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        )
        Text(
            text = "Local Only Privacy Focused Note Taking App.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

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

        // Category & Tag Filter Pills (Long-press any #tag pill to delete it)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val isSelected = selectedTagFilter == null && selectedCategory.equals(cat, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedTagFilter = null
                        onSelectCategory(cat)
                    },
                    label = {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            allTags.forEach { tag ->
                val isTagSelected = selectedTagFilter == tag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTagSelected) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.1f),
                    border = BorderStroke(
                        1.dp,
                        if (isTagSelected) AuroraRoyalViolet else AuroraRoyalViolet.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            selectedTagFilter = if (isTagSelected) null else tag
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
        if (displayedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isBlank()) "No notes yet" else "No matching notes found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "Tap + to create your first note" else "Try clearing your search query",
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
                items(displayedNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onSelectNote(note) },
                        onTogglePin = { onTogglePin(note) },
                        onDelete = { onDeleteNote(note.id) },
                        onToggleCheckItem = { itemId -> onToggleCheckItem(note.id, itemId) },
                        onDeleteTag = onDeleteTag
                    )
                }
            }
        }
    }
}
