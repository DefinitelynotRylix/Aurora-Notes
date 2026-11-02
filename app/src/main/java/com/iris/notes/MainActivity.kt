package com.iris.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.iris.notes.ui.components.CipherInspectorDialog
import com.iris.notes.ui.screens.NoteEditorScreen
import com.iris.notes.ui.screens.NotesScreen
import com.iris.notes.ui.screens.SettingsScreen
import com.iris.notes.ui.screens.TrashScreen
import com.iris.notes.ui.theme.IrisRoyalViolet
import com.iris.notes.ui.theme.IrisTheme
import com.iris.notes.viewmodel.NotesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IrisTheme {
                IrisApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun IrisApp(viewModel: NotesViewModel) {
    val activeEditingNote by viewModel.activeEditingNote.collectAsState()
    val inspectingCipherNote by viewModel.inspectingCipherNote.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val filteredNotes by viewModel.filteredActiveNotes.collectAsState()
    val filteredTrashedNotes by viewModel.filteredTrashedNotes.collectAsState()

    // If an active editing note is selected, show the Maximized Full-Screen Note Editor!
    if (activeEditingNote != null) {
        NoteEditorScreen(
            note = activeEditingNote!!,
            onSaveAndClose = { updatedNote ->
                viewModel.saveNote(updatedNote)
                viewModel.closeEditor()
            },
            onInspectCipher = { noteToInspect ->
                viewModel.inspectCipher(noteToInspect)
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                        label = { Text("Notes") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IrisRoyalViolet,
                            indicatorColor = IrisRoyalViolet.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        icon = { Icon(Icons.Default.PushPin, contentDescription = "Pinned") },
                        label = { Text("Pinned") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IrisRoyalViolet,
                            indicatorColor = IrisRoyalViolet.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2) },
                        icon = { Icon(Icons.Default.Delete, contentDescription = "Trash") },
                        label = { Text("Trash") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IrisRoyalViolet,
                            indicatorColor = IrisRoyalViolet.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IrisRoyalViolet,
                            indicatorColor = IrisRoyalViolet.copy(alpha = 0.15f)
                        )
                    )
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
                        onInspectCipher = { viewModel.inspectCipher(it) },
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

    // Cipher Inspector Dialog
    if (inspectingCipherNote != null) {
        CipherInspectorDialog(
            note = inspectingCipherNote!!,
            onDismiss = { viewModel.closeCipherInspector() }
        )
    }
}
