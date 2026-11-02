package com.aurora.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aurora.notes.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: String, timestamp: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1")
    suspend fun getTrashedCount(): Int
}
