package com.iris.notes.data

import com.iris.notes.model.Note
import com.iris.notes.util.LocalLogger
import kotlinx.coroutines.flow.Flow

class IrisRepository(private val noteDao: NoteDao) {

    val activeNotes: Flow<List<Note>> = noteDao.getActiveNotes()
    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes()

    suspend fun saveNote(note: Note) {
        val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
        noteDao.insertOrUpdate(updatedNote)
        LocalLogger.security("NOTE", "Local note saved: \"${updatedNote.title}\" (ID: ${updatedNote.id})")
    }

    suspend fun togglePin(note: Note) {
        val updated = note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        noteDao.update(updated)
        LocalLogger.security("NOTE", "Note pin toggled: \"${updated.title}\" (isPinned: ${updated.isPinned})")
    }

    suspend fun toggleChecklistItem(noteId: String, itemId: String) {
        val note = noteDao.getNoteById(noteId) ?: return
        val updatedList = note.checklist.map { item ->
            if (item.id == itemId) item.copy(completed = !item.completed) else item
        }
        val updatedNote = note.copy(checklist = updatedList, updatedAt = System.currentTimeMillis())
        noteDao.update(updatedNote)
    }

    suspend fun moveToTrash(id: String) {
        noteDao.moveToTrash(id, System.currentTimeMillis())
        LocalLogger.security("TRASH", "Moved note to trash: $id")
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
}
