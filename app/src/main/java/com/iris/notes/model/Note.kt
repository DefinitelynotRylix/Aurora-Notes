package com.iris.notes.model

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
    val checklist: List<ChecklistItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val category: String = "General",
    val isPinned: Boolean = false,
    val isEncrypted: Boolean = true,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class EncryptedVaultPayload(
    val version: Int = 1,
    val salt: String,
    val iv: String,
    val verifier: String,
    val ciphertext: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val noteCount: Int
)

data class VaultConfig(
    val isInitialized: Boolean = true,
    val isUnlocked: Boolean = true,
    val autoLockMinutes: Int = 5,
    val duressCode: String? = null,
    val salt: String? = null,
    val verifier: String? = null
)
