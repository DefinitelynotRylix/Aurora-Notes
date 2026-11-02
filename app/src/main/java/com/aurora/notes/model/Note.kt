package com.aurora.notes.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val completed: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val styleSpansJson: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val category: String = "",
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
