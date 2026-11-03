package com.aurora.notes.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.model.Note
import com.aurora.notes.ui.theme.AuroraDangerRed
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    trashedNotes: List<Note>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onRestoreNote: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AuroraDangerRed) },
            title = { Text("Permanently Empty Trash?") },
            text = {
                Text(
                    "This will permanently delete all ${trashedNotes.size} notes from your local device storage. This action cannot be undone.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed)
                ) {
                    Text("Yes, Empty All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmptyConfirmDialog = false }) {
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
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trash",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${trashedNotes.size} item${if (trashedNotes.size == 1) "" else "s"} in trash",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }

                    if (trashedNotes.isNotEmpty()) {
                        Button(
                            onClick = { showEmptyConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AuroraDangerRed.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AuroraDangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Empty Trash", color = AuroraDangerRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (trashedNotes.isNotEmpty() || searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search trashed notes...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Trashed Items" else "Search Results (${trashedNotes.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            )

            IconButton(onClick = onToggleGridView, modifier = Modifier.size(36.dp)) {
                AnimatedContent(
                    targetState = isGridView,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.75f, animationSpec = tween(220)) + fadeIn(tween(220)))
                            .togetherWith(scaleOut(targetScale = 0.75f, animationSpec = tween(160)) + fadeOut(tween(160)))
                    },
                    label = "trash_masonry_column_icon_anim"
                ) { gridMode ->
                    Icon(
                        imageVector = if (gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle Grid Layout",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val firstNoteHorizontalScale = remember { Animatable(1f) }
        val otherNotesAlignProgress = remember { Animatable(1f) }
        var previousGridView by remember { mutableStateOf(isGridView) }
        LaunchedEffect(isGridView) {
            if (previousGridView != isGridView) {
                previousGridView = isGridView
                kotlinx.coroutines.coroutineScope {
                    kotlinx.coroutines.launch {
                        firstNoteHorizontalScale.snapTo(if (!isGridView) 0.86f else 0.94f)
                        firstNoteHorizontalScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 190f
                            )
                        )
                    }
                    kotlinx.coroutines.launch {
                        otherNotesAlignProgress.snapTo(0.92f)
                        otherNotesAlignProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.80f,
                                stiffness = 195f
                            )
                        )
                    }
                }
            }
        }

        if (trashedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Trash is empty" else "No matching trashed notes",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = trashedNotes,
                    key = { _, note -> note.id },
                    span = { _, _ ->
                        if (isGridView) {
                            StaggeredGridItemSpan.SingleLane
                        } else {
                            StaggeredGridItemSpan.FullLine
                        }
                    }
                ) { index, note ->
                    val isFirstNote = index == 0
                    TrashedNoteCard(
                        note = note,
                        onRestore = { onRestoreNote(note.id) },
                        onDeletePermanent = { onDeletePermanently(note.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                placementSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 190f
                                ),
                                fadeOutSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            )
                            .graphicsLayer {
                                if (isFirstNote) {
                                    val sx = firstNoteHorizontalScale.value
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = sx
                                    scaleY = 0.96f + 0.04f * sx
                                    alpha = (0.45f + 0.55f * sx).coerceIn(0f, 1f)
                                } else {
                                    val s = otherNotesAlignProgress.value
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = (0.4f + 0.6f * s).coerceIn(0f, 1f)
                                }
                            }
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 190f
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashedNoteCard(
    note: Note,
    onRestore: () -> Unit,
    onDeletePermanent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    val formattedDate = dateFormat.format(Date(note.deletedAt ?: note.updatedAt))

    val plainText = note.content.replace(Regex("<[^>]*>"), "").trim()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (plainText.isNotBlank()) {
                Text(
                    text = plainText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Deleted $formattedDate",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = AuroraRoyalViolet)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", fontSize = 11.sp, color = AuroraRoyalViolet)
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onDeletePermanent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Purge", tint = AuroraDangerRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
