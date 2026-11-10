package com.aurora.notes.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.notes.data.AuroraDatabase
import com.aurora.notes.data.AuroraRepository
import com.aurora.notes.model.Note
import com.aurora.notes.util.LocalLogger
import com.aurora.notes.util.PrivateStorageManager
import com.aurora.notes.util.PrivateStorageStats
import com.aurora.notes.util.StorageCleanupReport
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "aurora_notes_preferences"
        private const val KEY_BIOMETRIC_LOCK = "key_biometric_lock_enabled"
        private const val KEY_WELCOME_SEEN = "key_welcome_tour_seen_v1"
        private const val KEY_WHATS_NEW_SEEN_VERSION = "key_whats_new_seen_version_v1"
        const val CURRENT_MAJOR_UPDATE_ID = "1.3.38"
        private const val KEY_USER_TAGS = "key_user_created_tags_v1"
        private const val KEY_THEME_MODE = "key_theme_mode_v1"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled_v1"
        private const val KEY_DB_ENCRYPTION_ENABLED = "key_room_db_encryption_enabled_v1"
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val repository: AuroraRepository

    val allActiveNotes: StateFlow<List<Note>>
    val allArchivedNotes: StateFlow<List<Note>>
    val allTrashedNotes: StateFlow<List<Note>>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Notes, 1: Archive, 2: Trash, 3: Settings
    val selectedTab = _selectedTab.asStateFlow()

    private val _pendingNavTab = MutableStateFlow(0)
    val pendingNavTab: StateFlow<Int> = _pendingNavTab.asStateFlow()

    private val _isNavTransitionPending = MutableStateFlow(false)
    val isNavTransitionPending: StateFlow<Boolean> = _isNavTransitionPending.asStateFlow()

    private var navTransitionJob: kotlinx.coroutines.Job? = null

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

    private val _isWhatsNewOpen = MutableStateFlow(checkAndConsumeWhatsNewOnce())
    val isWhatsNewOpen = _isWhatsNewOpen.asStateFlow()

    private val _userTags = MutableStateFlow<List<String>>(loadSavedTags())
    val userTags = _userTags.asStateFlow()

    private val _themeMode = MutableStateFlow<String>(prefs.getString(KEY_THEME_MODE, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_ENABLED, true))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isDbEncryptionEnabled = MutableStateFlow(true)
    val isDbEncryptionEnabled: StateFlow<Boolean> = _isDbEncryptionEnabled.asStateFlow()

    private val _storageStats = MutableStateFlow(
        PrivateStorageStats(
            directoryPath = PrivateStorageManager.getPrivateExternalNotesDir(application).absolutePath,
            syncedFileCount = 0,
            orphanedFileCount = 0,
            totalBytes = 0L,
            isEncrypted = true
        )
    )
    val storageStats: StateFlow<PrivateStorageStats> = _storageStats.asStateFlow()

    private val _lastCleanupSummary = MutableStateFlow<String?>(null)
    val lastCleanupSummary: StateFlow<String?> = _lastCleanupSummary.asStateFlow()

    init {
        prefs.edit().putBoolean(KEY_DB_ENCRYPTION_ENABLED, true).apply()
        val noteDao = AuroraDatabase.getDatabase(application).noteDao()
        repository = AuroraRepository(
            context = application.applicationContext,
            noteDao = noteDao,
            initialEncryptionEnabled = true
        )

        allActiveNotes = repository.activeNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allArchivedNotes = repository.archivedNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        allTrashedNotes = repository.trashedNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val (report, stats) = repository.syncAndCleanupOnStartup()
            _storageStats.value = stats
            val totalCleaned = report.orphanedFilesRemoved + report.tempFilesRemoved
            if (totalCleaned > 0) {
                _lastCleanupSummary.value =
                    "Auto-cleaned ${report.orphanedFilesRemoved} orphaned file(s) & ${report.tempFilesRemoved} temp file(s)"
            }
            while (true) {
                kotlinx.coroutines.delay(3 * 60 * 1000L)
                runAutomaticStorageSweepInternal()
            }
        }
    }

    fun onAppResumed() {
        viewModelScope.launch(Dispatchers.IO) {
            runAutomaticStorageSweepInternal()
        }
    }

    private suspend fun runAutomaticStorageSweepInternal() {
        val report = repository.cleanupOrphanedStorageFiles()
        _storageStats.value = repository.getStorageStats()
        val totalCleaned = report.orphanedFilesRemoved + report.tempFilesRemoved
        if (totalCleaned > 0) {
            _lastCleanupSummary.value =
                "Auto-cleaned ${report.orphanedFilesRemoved} orphaned file(s) & ${report.tempFilesRemoved} temp file(s)"
        }
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
        val current = _selectedTab.value
        if (tab == current && _pendingNavTab.value == tab) return
        navTransitionJob?.cancel()
        _isNavTransitionPending.value = true
        _pendingNavTab.value = tab

        val distance = kotlin.math.abs(tab - current)
        // Slightly reduced button wait time so far menus (Notes <-> Settings, Archive <-> Settings)
        // remain responsive while still loading and animating cleanly without lag
        val leadInDelayMs = if (distance >= 2) 30L else 0L
        val totalWaitMs = if (distance >= 2) 260L else 180L

        if (leadInDelayMs == 0L) {
            _selectedTab.value = tab
        }

        navTransitionJob = viewModelScope.launch {
            if (leadInDelayMs > 0L) {
                kotlinx.coroutines.delay(leadInDelayMs)
                _selectedTab.value = tab
            }
            kotlinx.coroutines.delay(totalWaitMs - leadInDelayMs)
            _isNavTransitionPending.value = false
        }
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    private fun checkAndConsumeWhatsNewOnce(): Boolean {
        val lastSeenVersion = prefs.getString(KEY_WHATS_NEW_SEEN_VERSION, null)
        if (lastSeenVersion != CURRENT_MAJOR_UPDATE_ID) {
            prefs.edit().putString(KEY_WHATS_NEW_SEEN_VERSION, CURRENT_MAJOR_UPDATE_ID).apply()
            return true
        }
        return false
    }

    fun onAppUpdatedRestart() {
        if (checkAndConsumeWhatsNewOnce()) {
            _isWhatsNewOpen.value = true
        }
    }

    fun dismissWhatsNew() {
        prefs.edit().putString(KEY_WHATS_NEW_SEEN_VERSION, CURRENT_MAJOR_UPDATE_ID).apply()
        _isWhatsNewOpen.value = false
    }

    fun dismissWelcome() {
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
        _isWelcomeOpen.value = false
    }

    fun reopenWelcome() {
        _isWelcomeOpen.value = true
    }

    fun reopenWhatsNew() {
        _isWhatsNewOpen.value = true
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
        if (note.isArchived) return
        val cleanTitle = note.title.trim()
        val cleanContent = note.content
            .replace(HTML_TAG_REGEX, "")
            .replace("[ ]", "")
            .replace("[x]", "")
            .trim()
        val hasChecklistContent = note.checklist.any { it.text.trim().isNotBlank() }

        if (cleanTitle.isBlank() && cleanContent.isBlank() && !hasChecklistContent && note.images.isEmpty()) {
            LocalLogger.warn("NOTE", "Discarded blank note with empty title and body")
            return
        }

        val finalNote = note.copy(
            title = cleanTitle.ifBlank { "Untitled Note" }
        )

        // Do not overwrite updatedAt or re-save to Room if an existing note was not modified at all
        val existingNote = allActiveNotes.value.firstOrNull { it.id == finalNote.id }
        if (existingNote != null &&
            existingNote.title == finalNote.title &&
            existingNote.content == finalNote.content &&
            existingNote.styleSpansJson == finalNote.styleSpansJson &&
            existingNote.checklist == finalNote.checklist &&
            existingNote.images == finalNote.images &&
            existingNote.tags == finalNote.tags &&
            existingNote.category == finalNote.category &&
            existingNote.isPinned == finalNote.isPinned
        ) {
            return
        }

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

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveNote(finalNote)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun togglePin(note: Note) {
        if (note.isArchived) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.togglePin(note)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun reorderNotes(orderedIds: List<String>, isArchive: Boolean = false) {
        if (orderedIds.isEmpty()) return
        val sourceList = if (isArchive) allArchivedNotes.value else allActiveNotes.value
        val byId = sourceList.associateBy { it.id }

        // Pinned notes reorder among pinned notes; unpinned notes reorder among unpinned notes
        val pinnedOrderedIds = orderedIds.filter { id ->
            val note = byId[id]
            note != null && note.isPinned
        }
        val unpinnedOrderedIds = orderedIds.filter { id ->
            val note = byId[id]
            note != null && !note.isPinned
        }

        val visiblePinnedSet = pinnedOrderedIds.toSet()
        val visibleUnpinnedSet = unpinnedOrderedIds.toSet()

        val pinnedQueue = ArrayDeque(pinnedOrderedIds.mapNotNull { byId[it] })
        val unpinnedQueue = ArrayDeque(unpinnedOrderedIds.mapNotNull { byId[it] })

        val finalOrdered = sourceList.map { note ->
            if (note.isPinned && note.id in visiblePinnedSet && pinnedQueue.isNotEmpty()) {
                pinnedQueue.removeFirst()
            } else if (!note.isPinned && note.id in visibleUnpinnedSet && unpinnedQueue.isNotEmpty()) {
                unpinnedQueue.removeFirst()
            } else {
                note
            }
        }.mapIndexed { globalIdx, note ->
            note.copy(sortOrder = globalIdx + 1)
        }

        viewModelScope.launch(Dispatchers.IO) {
            repository.reorderNotes(finalOrdered)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun tagNotesBatch(noteIds: List<String>, tagsToApply: List<String>, isArchive: Boolean = false) {
        val cleanTags = tagsToApply
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
            .distinctBy { it.lowercase() }
        if (noteIds.isEmpty() || cleanTags.isEmpty()) return

        // Register all applied tags in userTags
        val currentTags = _userTags.value.toMutableList()
        var tagsChanged = false
        cleanTags.forEach { tag ->
            if (currentTags.none { it.equals(tag, ignoreCase = true) }) {
                currentTags.add(tag)
                tagsChanged = true
            }
        }
        if (tagsChanged) {
            persistUserTags(currentTags)
        }

        val sourceList = if (isArchive) allArchivedNotes.value else allActiveNotes.value
        val idSet = noteIds.toSet()
        val updatedNotes = sourceList.filter { it.id in idSet }.map { note ->
            val combinedTags = (note.tags + cleanTags)
                .map { it.trim().removePrefix("#").trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            note.copy(
                tags = combinedTags,
                category = combinedTags.firstOrNull() ?: note.category
            )
        }

        if (updatedNotes.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveNotesBatch(updatedNotes)
                _storageStats.value = repository.getStorageStats()
            }
        }
    }

    fun toggleChecklistItem(noteId: String, itemId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleChecklistItem(noteId, itemId)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun archiveNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.archiveNote(id)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun archiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.archiveNotesBatch(ids)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun unarchiveNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.unarchiveNote(id)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun unarchiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.unarchiveNotesBatch(ids)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun moveToTrash(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTrash(id)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun moveToTrashBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveToTrashBatch(ids)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun restoreFromTrash(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreFromTrash(id)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePermanently(id)
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.emptyTrash()
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun setThemeMode(mode: String) {
        val normalized = when (mode.lowercase()) {
            "light", "dark", "system" -> mode.lowercase()
            else -> "system"
        }
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
        _themeMode.value = normalized
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        _isHapticsEnabled.value = enabled
    }

    fun setDatabaseEncryptionEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        // Encryption is permanently enabled by default and cannot be disabled
        prefs.edit().putBoolean(KEY_DB_ENCRYPTION_ENABLED, true).apply()
        _isDbEncryptionEnabled.value = true
    }

    fun cleanUpOrphanedFiles(onComplete: (StorageCleanupReport) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = repository.cleanupOrphanedStorageFiles()
            val stats = repository.getStorageStats()
            _storageStats.value = stats
            _lastCleanupSummary.value =
                "Cleaned ${report.orphanedFilesRemoved} orphaned file(s) & ${report.tempFilesRemoved} temp file(s) (${report.bytesFreed} bytes freed)"
            withContext(Dispatchers.Main) {
                onComplete(report)
            }
        }
    }

    fun wipeAllData() {
        persistUserTags(emptyList())
        _selectedCategory.value = "All"
        viewModelScope.launch(Dispatchers.IO) {
            repository.wipeAllNotes()
            _storageStats.value = repository.getStorageStats()
            _lastCleanupSummary.value = "Wiped all notes and cleaned private external storage"
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
