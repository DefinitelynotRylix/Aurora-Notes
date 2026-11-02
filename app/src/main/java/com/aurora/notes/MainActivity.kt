package com.aurora.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aurora.notes.ui.screens.NoteEditorScreen
import com.aurora.notes.ui.screens.NotesScreen
import com.aurora.notes.ui.screens.SettingsScreen
import com.aurora.notes.ui.screens.TrashScreen
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.ui.theme.AuroraTheme
import com.aurora.notes.viewmodel.NotesViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuroraTheme {
                AuroraApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AuroraApp(viewModel: NotesViewModel) {
    val activeEditingNote by viewModel.activeEditingNote.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val filteredNotes by viewModel.filteredActiveNotes.collectAsState()
    val filteredTrashedNotes by viewModel.filteredTrashedNotes.collectAsState()

    if (activeEditingNote != null) {
        NoteEditorScreen(
            note = activeEditingNote!!,
            onSaveAndClose = { updatedNote ->
                viewModel.saveNote(updatedNote)
                viewModel.closeEditor()
            }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { viewModel.openNewNote() },
                        containerColor = AuroraRoyalViolet,
                        contentColor = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Note")
                    }
                }
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { viewModel.setSelectedTab(0) },
                            icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                            label = { Text("Notes") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AuroraRoyalViolet,
                                indicatorColor = AuroraRoyalViolet.copy(alpha = 0.18f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { viewModel.setSelectedTab(1) },
                            icon = { Icon(Icons.Default.PushPin, contentDescription = "Pinned") },
                            label = { Text("Pinned") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AuroraRoyalViolet,
                                indicatorColor = AuroraRoyalViolet.copy(alpha = 0.18f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { viewModel.setSelectedTab(2) },
                            icon = { Icon(Icons.Default.Delete, contentDescription = "Trash") },
                            label = { Text("Trash") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AuroraRoyalViolet,
                                indicatorColor = AuroraRoyalViolet.copy(alpha = 0.18f)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { viewModel.setSelectedTab(3) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AuroraRoyalViolet,
                                indicatorColor = AuroraRoyalViolet.copy(alpha = 0.18f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            when (selectedTab) {
                0, 1 -> {
                    NotesScreen(
                        notes = filteredNotes,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { viewModel.setSelectedCategory(it) },
                        isGridView = isGridView,
                        onToggleGridView = { viewModel.toggleGridView() },
                        onSelectNote = { viewModel.openEditNote(it) },
                        onTogglePin = { viewModel.togglePin(it) },
                        onDeleteNote = { viewModel.moveToTrash(it) },
                        onToggleCheckItem = { noteId, itemId -> viewModel.toggleChecklistItem(noteId, itemId) },
                        onAddNote = { viewModel.openNewNote() },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                2 -> {
                    TrashScreen(
                        trashedNotes = filteredTrashedNotes,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        isGridView = isGridView,
                        onToggleGridView = { viewModel.toggleGridView() },
                        onRestoreNote = { viewModel.restoreFromTrash(it) },
                        onDeletePermanently = { viewModel.deletePermanently(it) },
                        onEmptyTrash = { viewModel.emptyTrash() },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                3 -> {
                    SettingsScreen(
                        onClearLogs = { viewModel.clearDiagnosticLogs() },
                        onExportLogs = { viewModel.exportDiagnosticLogs() },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}
