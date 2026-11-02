package com.iris.notes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iris.notes.data.IrisDatabase
import com.iris.notes.data.IrisRepository
import com.iris.notes.model.Note
import com.iris.notes.security.CryptoManager
import com.iris.notes.util.LocalLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: IrisRepository

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

    private val _inspectingCipherNote = MutableStateFlow<Note?>(null)
    val inspectingCipherNote = _inspectingCipherNote.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView = _isGridView.asStateFlow()

    init {
        val noteDao = IrisDatabase.getDatabase(application, viewModelScope).noteDao()
        repository = IrisRepository(noteDao)

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

        LocalLogger.security("APP", "Iris Vault initialized with local Room SQLite storage")
    }

    // Filtered active notes based on Search and Category
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

    // Filtered trashed notes based on Search in Trash
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

    fun inspectCipher(note: Note) {
        _inspectingCipherNote.value = note
    }

    fun closeCipherInspector() {
        _inspectingCipherNote.value = null
    }

    fun clearDiagnosticLogs() {
        LocalLogger.clear()
    }

    fun exportDiagnosticLogs(): String {
        return LocalLogger.exportToPlainText()
    }
}
