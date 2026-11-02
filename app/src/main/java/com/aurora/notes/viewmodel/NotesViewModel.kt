package com.aurora.notes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.notes.data.AuroraDatabase
import com.aurora.notes.data.AuroraRepository
import com.aurora.notes.model.Note
import com.aurora.notes.util.LocalLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuroraRepository

    val allActiveNotes: StateFlow<List<Note>>
    val allTrashedNotes: StateFlow<List<Note>>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Notes, 1: Pinned, 2: Trash, 3: Settings
    val selectedTab = _selectedTab.asStateFlow()

    private val _activeEditingNote = MutableStateFlow<Note?>(null)
    val activeEditingNote = _activeEditingNote.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView = _isGridView.asStateFlow()

    init {
        val noteDao = AuroraDatabase.getDatabase(application).noteDao()
        repository = AuroraRepository(noteDao)

        allActiveNotes = repository.activeNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allTrashedNotes = repository.trashedNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        LocalLogger.security("APP", "Aurora Notes initialized with local Room SQLite storage")
    }

    val filteredActiveNotes: StateFlow<List<Note>> = combine(
        allActiveNotes,
        _searchQuery,
        _selectedCategory,
        _selectedTab
    ) { notes, query, category, tab ->
        var list = notes
        if (tab == 1) {
            list = list.filter { it.isPinned }
        }
        if (category != "All") {
            list = list.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { note ->
                note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.tags.any { it.lowercase().contains(q) } ||
                note.checklist.any { it.text.lowercase().contains(q) }
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTrashedNotes: StateFlow<List<Note>> = combine(
        allTrashedNotes,
        _searchQuery
    ) { notes, query ->
        if (query.isBlank()) {
            notes
        } else {
            val q = query.trim().lowercase()
            notes.filter { note ->
                note.title.lowercase().contains(q) ||
                note.content.lowercase().contains(q) ||
                note.tags.any { it.lowercase().contains(q) } ||
                note.checklist.any { it.text.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun openNewNote() {
        _activeEditingNote.value = Note(
            title = "",
            content = "",
            category = if (_selectedCategory.value == "All") "General" else _selectedCategory.value
        )
    }

    fun openEditNote(note: Note) {
        _activeEditingNote.value = note
    }

    fun closeEditor() {
        _activeEditingNote.value = null
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            repository.saveNote(note)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.togglePin(note)
        }
    }

    fun toggleChecklistItem(noteId: String, itemId: String) {
        viewModelScope.launch {
            repository.toggleChecklistItem(noteId, itemId)
        }
    }

    fun moveToTrash(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }

    fun restoreFromTrash(id: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
        }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch {
            repository.deletePermanently(id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun clearDiagnosticLogs() {
        LocalLogger.clear()
    }

    fun exportDiagnosticLogs(): String {
        return LocalLogger.exportToPlainText()
    }
}
