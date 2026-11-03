package com.aurora.notes.data

import android.content.Context
import com.aurora.notes.model.Note
import com.aurora.notes.util.LocalLogger
import com.aurora.notes.util.PrivateStorageManager
import com.aurora.notes.util.PrivateStorageStats
import com.aurora.notes.util.StorageCleanupReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuroraRepository(
    private val context: Context,
    private val noteDao: NoteDao,
    initialEncryptionEnabled: Boolean = true
) {

    val isDatabaseEncryptionEnabled: Boolean = true

    val activeNotes: Flow<List<Note>> = noteDao.getActiveNotes().map { list ->
        list.map { PrivateStorageManager.decryptNoteFromRoom(it) }
    }

    val archivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes().map { list ->
        list.map { PrivateStorageManager.decryptNoteFromRoom(it) }
    }

    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes().map { list ->
        list.map { PrivateStorageManager.decryptNoteFromRoom(it) }
    }

    suspend fun saveNote(note: Note) {
        if (note.isArchived) return
        val plainUpdated = PrivateStorageManager.decryptNoteFromRoom(note).copy(
            updatedAt = System.currentTimeMillis()
        )
        val roomEntity = PrivateStorageManager.encryptNoteForRoom(
            note = plainUpdated,
            encryptionEnabled = isDatabaseEncryptionEnabled
        )
        noteDao.insertOrUpdate(roomEntity)
        PrivateStorageManager.writeNoteToPrivateStorage(
            context = context,
            note = plainUpdated,
            encryptFileContent = isDatabaseEncryptionEnabled
        )
        LocalLogger.security("NOTE", "Local note saved: \"${plainUpdated.title}\" (ID: ${plainUpdated.id})")
    }

    suspend fun togglePin(note: Note) {
        if (note.isArchived) return
        val plainUpdated = PrivateStorageManager.decryptNoteFromRoom(note).copy(
            isPinned = !note.isPinned,
            updatedAt = System.currentTimeMillis()
        )
        val roomEntity = PrivateStorageManager.encryptNoteForRoom(
            note = plainUpdated,
            encryptionEnabled = isDatabaseEncryptionEnabled
        )
        noteDao.update(roomEntity)
        PrivateStorageManager.writeNoteToPrivateStorage(
            context = context,
            note = plainUpdated,
            encryptFileContent = isDatabaseEncryptionEnabled
        )
    }

    suspend fun toggleChecklistItem(noteId: String, itemId: String) {
        val rawNote = noteDao.getNoteById(noteId) ?: return
        val plainNote = PrivateStorageManager.decryptNoteFromRoom(rawNote)
        if (plainNote.isArchived) return
        val updatedList = plainNote.checklist.map { item ->
            if (item.id == itemId) item.copy(completed = !item.completed) else item
        }
        val plainUpdated = plainNote.copy(
            checklist = updatedList,
            updatedAt = System.currentTimeMillis()
        )
        val roomEntity = PrivateStorageManager.encryptNoteForRoom(
            note = plainUpdated,
            encryptionEnabled = isDatabaseEncryptionEnabled
        )
        noteDao.update(roomEntity)
        PrivateStorageManager.writeNoteToPrivateStorage(
            context = context,
            note = plainUpdated,
            encryptFileContent = isDatabaseEncryptionEnabled
        )
    }

    suspend fun archiveNote(id: String) {
        noteDao.archiveNote(id, System.currentTimeMillis())
        syncSingleNoteFile(id)
    }

    suspend fun archiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.archiveNotesBatch(ids, System.currentTimeMillis())
        ids.forEach { syncSingleNoteFile(it) }
    }

    suspend fun unarchiveNote(id: String) {
        noteDao.unarchiveNote(id, System.currentTimeMillis())
        syncSingleNoteFile(id)
    }

    suspend fun unarchiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.unarchiveNotesBatch(ids, System.currentTimeMillis())
        ids.forEach { syncSingleNoteFile(it) }
    }

    suspend fun moveToTrash(id: String) {
        noteDao.moveToTrash(id, System.currentTimeMillis())
        PrivateStorageManager.deleteNoteFromPrivateStorage(context, id)
    }

    suspend fun moveToTrashBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.moveToTrashBatch(ids, System.currentTimeMillis())
        ids.forEach { PrivateStorageManager.deleteNoteFromPrivateStorage(context, it) }
    }

    suspend fun restoreFromTrash(id: String) {
        noteDao.restoreFromTrash(id)
        syncSingleNoteFile(id)
    }

    suspend fun deletePermanently(id: String) {
        noteDao.deletePermanently(id)
        PrivateStorageManager.deleteNoteFromPrivateStorage(context, id)
        cleanupOrphanedStorageFiles()
    }

    suspend fun emptyTrash() {
        val trashed = noteDao.getTrashedNotesSnapshot()
        noteDao.emptyTrash()
        trashed.forEach { PrivateStorageManager.deleteNoteFromPrivateStorage(context, it.id) }
        cleanupOrphanedStorageFiles()
    }

    suspend fun wipeAllNotes() {
        noteDao.wipeAllNotes()
        PrivateStorageManager.cleanupOrphanedFiles(context, emptySet())
    }

    suspend fun setDatabaseEncryption(enabled: Boolean = true): PrivateStorageStats {
        val allRaw = noteDao.getAllNotesSnapshot()
        val migratedRoomEntities = allRaw.map { raw ->
            PrivateStorageManager.encryptNoteForRoom(raw, encryptionEnabled = true)
        }
        if (migratedRoomEntities.isNotEmpty()) {
            noteDao.insertAll(migratedRoomEntities)
        }
        val plainNotes = allRaw.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        PrivateStorageManager.syncAllNotesToPrivateStorage(
            context = context,
            allNotes = plainNotes,
            encryptFileContent = true
        )
        return getStorageStats()
    }

    suspend fun syncAndCleanupOnStartup(): Pair<StorageCleanupReport, PrivateStorageStats> {
        val allRaw = noteDao.getAllNotesSnapshot()
        val migratedRoomEntities = allRaw.map { raw ->
            PrivateStorageManager.encryptNoteForRoom(raw, encryptionEnabled = true)
        }
        if (migratedRoomEntities.isNotEmpty()) {
            noteDao.insertAll(migratedRoomEntities)
        }
        val plainNotes = allRaw.map { PrivateStorageManager.decryptNoteFromRoom(it) }
        val report = PrivateStorageManager.syncAllNotesToPrivateStorage(
            context = context,
            allNotes = plainNotes,
            encryptFileContent = true
        )
        val validIds = plainNotes.filter { !it.isDeleted }.map { it.id }.toSet()
        val stats = PrivateStorageManager.computeStorageStats(
            context = context,
            validNoteIds = validIds,
            isEncrypted = true
        )
        return report to stats
    }

    suspend fun cleanupOrphanedStorageFiles(): StorageCleanupReport {
        val allRaw = noteDao.getAllNotesSnapshot()
        val validIds = allRaw.filter { !it.isDeleted }.map { it.id }.toSet()
        return PrivateStorageManager.cleanupOrphanedFiles(context, validIds)
    }

    suspend fun getStorageStats(): PrivateStorageStats {
        val allRaw = noteDao.getAllNotesSnapshot()
        val validIds = allRaw.filter { !it.isDeleted }.map { it.id }.toSet()
        return PrivateStorageManager.computeStorageStats(
            context = context,
            validNoteIds = validIds,
            isEncrypted = isDatabaseEncryptionEnabled
        )
    }

    private suspend fun syncSingleNoteFile(id: String) {
        val raw = noteDao.getNoteById(id) ?: return
        val plain = PrivateStorageManager.decryptNoteFromRoom(raw)
        PrivateStorageManager.writeNoteToPrivateStorage(
            context = context,
            note = plain,
            encryptFileContent = isDatabaseEncryptionEnabled
        )
    }
}
