package com.aurora.notes.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.notes.data.AuroraDatabase
import com.aurora.notes.data.AuroraRepository
import com.aurora.notes.model.Note
import com.aurora.notes.util.LocalLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "aurora_notes_preferences"
        private const val KEY_BIOMETRIC_LOCK = "key_biometric_lock_enabled"
        private const val KEY_WELCOME_SEEN = "key_welcome_tour_seen_v1"
        private const val KEY_USER_TAGS = "key_user_created_tags_v1"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

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

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_LOCK, false))
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isAppLocked = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_LOCK, false))
    val isAppLocked = _isAppLocked.asStateFlow()

    private val _isWelcomeOpen = MutableStateFlow(!prefs.getBoolean(KEY_WELCOME_SEEN, false))
    val isWelcomeOpen = _isWelcomeOpen.asStateFlow()

    private val _userTags = MutableStateFlow<List<String>>(loadSavedTags())
    val userTags = _userTags.asStateFlow()

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

    private fun loadSavedTags(): List<String> {
        val json = prefs.getString(KEY_USER_TAGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistUserTags(tags: List<String>) {
        _userTags.value = tags
        prefs.edit().putString(KEY_USER_TAGS, gson.toJson(tags)).apply()
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
            list = list.filter { note ->
                note.tags.any { it.equals(category, ignoreCase = true) } ||
                    note.category.equals(category, ignoreCase = true)
            }
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

    fun dismissWelcome() {
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
        _isWelcomeOpen.value = false
    }

    fun reopenWelcome() {
        _isWelcomeOpen.value = true
    }

    fun addUserTag(rawTag: String) {
        val clean = rawTag.trim().removePrefix("#").trim()
        if (clean.isBlank() || clean.equals("All", ignoreCase = true)) return
        val current = _userTags.value
        if (current.none { it.equals(clean, ignoreCase = true) }) {
            persistUserTags(current + clean)
            LocalLogger.security("TAGS", "Created user tag '$clean'")
        }
    }

    fun openNewNote() {
        val activeTag = _selectedCategory.value
        val initialTags = if (activeTag != "All" && activeTag.isNotBlank()) listOf(activeTag) else emptyList()
        _activeEditingNote.value = Note(
            title = "",
            content = "",
            tags = initialTags,
            category = if (activeTag != "All") activeTag else ""
        )
    }

    fun openEditNote(note: Note) {
        _activeEditingNote.value = note
    }

    fun closeEditor() {
        _activeEditingNote.value = null
    }

    fun saveNote(note: Note) {
        val cleanTitle = note.title.trim()
        val cleanContent = note.content
            .replace(Regex("<[^>]*>"), "")
            .replace("[ ]", "")
            .replace("[x]", "")
            .trim()
        val hasChecklistContent = note.checklist.any { it.text.trim().isNotBlank() }

        if (cleanTitle.isBlank() && cleanContent.isBlank() && !hasChecklistContent) {
            LocalLogger.warn("NOTE", "Discarded blank note with empty title and body")
            return
        }

        val finalNote = note.copy(
            title = cleanTitle.ifBlank { "Untitled Note" }
        )

        // Ensure any tags attached to the note are also registered in userTags
        if (finalNote.tags.isNotEmpty()) {
            val current = _userTags.value.toMutableList()
            var changed = false
            finalNote.tags.forEach { t ->
                val clean = t.trim().removePrefix("#")
                if (clean.isNotBlank() && current.none { it.equals(clean, ignoreCase = true) }) {
                    current.add(clean)
                    changed = true
                }
            }
            if (changed) {
                persistUserTags(current)
            }
        }

        viewModelScope.launch {
            repository.saveNote(finalNote)
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

    fun moveToTrashBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrashBatch(ids)
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

    fun setThemeMode(mode: String) {
        val normalized = when (mode.lowercase()) {
            "light", "dark", "system" -> mode.lowercase()
            else -> "system"
        }
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        _themeMode.value = normalized
        LocalLogger.security("THEME", "Updated app theme mode to '$normalized'")
    }

    fun wipeAllData() {
        persistUserTags(emptyList())
        _selectedCategory.value = "All"
        viewModelScope.launch {
            repository.wipeAllNotes()
            LocalLogger.security("STORAGE", "Wiped all notes and custom category pins after biometric verification")
        }
    }

    fun clearDiagnosticLogs() {
        LocalLogger.clear()
    }

    fun exportDiagnosticLogs(): String {
        return LocalLogger.exportToPlainText()
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _isBiometricEnabled.value = enabled
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (_isBiometricEnabled.value) {
            _isAppLocked.value = true
        }
    }

    fun deleteTagAndDetachFromNotes(tagToDelete: String) {
        val updatedUserTags = _userTags.value.filterNot { it.equals(tagToDelete, ignoreCase = true) }
        persistUserTags(updatedUserTags)

        if (_selectedCategory.value.equals(tagToDelete, ignoreCase = true)) {
            _selectedCategory.value = "All"
        }

        viewModelScope.launch {
            val activeNotes = allActiveNotes.value
            activeNotes.forEach { note ->
                val hasTag = note.tags.any { it.equals(tagToDelete, ignoreCase = true) }
                val hasCat = note.category.equals(tagToDelete, ignoreCase = true)
                if (hasTag || hasCat) {
                    val updatedTags = note.tags.filterNot { it.equals(tagToDelete, ignoreCase = true) }
                    val updatedCat = if (hasCat) (updatedTags.firstOrNull() ?: "") else note.category
                    repository.saveNote(
                        note.copy(
                            tags = updatedTags,
                            category = updatedCat
                        )
                    )
                }
            }
            LocalLogger.security("TAGS", "Deleted tag '$tagToDelete' and returned associated notes to All.")
        }
    }
}
