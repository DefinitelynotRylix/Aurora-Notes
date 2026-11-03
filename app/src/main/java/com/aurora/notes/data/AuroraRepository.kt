package com.aurora.notes.data

import com.aurora.notes.model.Note
import com.aurora.notes.util.LocalLogger
import kotlinx.coroutines.flow.Flow

class AuroraRepository(private val noteDao: NoteDao) {

    val activeNotes: Flow<List<Note>> = noteDao.getActiveNotes()
    val archivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes()
    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes()

    suspend fun saveNote(note: Note) {
        if (note.isArchived) return
        val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
        noteDao.insertOrUpdate(updatedNote)
        LocalLogger.security("NOTE", "Local note saved: \"${updatedNote.title}\" (ID: ${updatedNote.id})")
    }

    suspend fun togglePin(note: Note) {
        if (note.isArchived) return
        val updated = note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        noteDao.update(updated)
        LocalLogger.security("NOTE", "Note pin toggled: \"${updated.title}\" (isPinned: ${updated.isPinned})")
    }

    suspend fun toggleChecklistItem(noteId: String, itemId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        if (note.isArchived) return
        val updatedList = note.checklist.map { item ->
            if (item.id == itemId) item.copy(completed = !item.completed) else item
        }
        val updatedNote = note.copy(checklist = updatedList, updatedAt = System.currentTimeMillis())
        noteDao.update(updatedNote)
    }

    suspend fun archiveNote(id: String) {
        noteDao.archiveNote(id, System.currentTimeMillis())
        LocalLogger.security("ARCHIVE", "Archived note (read-only): $id")
    }

    suspend fun archiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.archiveNotesBatch(ids, System.currentTimeMillis())
        LocalLogger.security("ARCHIVE", "Batch archived ${ids.size} notes (read-only)")
    }

    suspend fun unarchiveNote(id: String) {
        noteDao.unarchiveNote(id, System.currentTimeMillis())
        LocalLogger.security("ARCHIVE", "Restored note from archive: $id")
    }

    suspend fun unarchiveNotesBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.unarchiveNotesBatch(ids, System.currentTimeMillis())
        LocalLogger.security("ARCHIVE", "Batch restored ${ids.size} notes from archive")
    }

    suspend fun moveToTrash(id: String) {
        noteDao.moveToTrash(id, System.currentTimeMillis())
        LocalLogger.security("TRASH", "Moved note to trash: $id")
    }

    suspend fun moveToTrashBatch(ids: List<String>) {
        if (ids.isEmpty()) return
        noteDao.moveToTrashBatch(ids, System.currentTimeMillis())
        LocalLogger.security("TRASH", "Batch moved ${ids.size} notes to trash")
    }

    suspend fun restoreFromTrash(id: String) {
        noteDao.restoreFromTrash(id)
        LocalLogger.security("TRASH", "Restored note from trash: $id")
    }

    suspend fun deletePermanently(id: String) {
        noteDao.deletePermanently(id)
        LocalLogger.security("TRASH", "Permanently deleted note: $id")
    }

    suspend fun emptyTrash() {
        val purged = noteDao.emptyTrash()
        LocalLogger.security("TRASH", "Permanently emptied trash ($purged items purged)")
    }

    suspend fun wipeAllNotes() {
        val wiped = noteDao.wipeAllNotes()
        LocalLogger.security("STORAGE", "Wiped all notes from local database ($wiped notes removed)")
    }
}
