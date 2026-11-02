package com.iris.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iris.notes.model.ChecklistItem
import com.iris.notes.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Note::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class IrisDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: IrisDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): IrisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IrisDatabase::class.java,
                    "iris_vault.db"
                )
                    .addCallback(IrisDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class IrisDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateSeedData(database.noteDao())
                    }
                }
            }

            suspend fun populateSeedData(noteDao: NoteDao) {
                val seedNotes = listOf(
                    Note(
                        id = "note-welcome-1",
                        title = "Zero-Knowledge Security Architecture",
                        content = "Iris Secure Notes operates under a strict Zero-Knowledge, Local-Only model.\n\nEvery character, checklist item, and media attachment is stored in local encrypted memory.\n\nCore Safeguards:\n- No Remote Network Calls: Completely offline.\n- Zero Telemetry: No analytics or remote logs.\n- Local Hardware Key Derivation: PBKDF2 with 100,000 iterations.",
                        checklist = listOf(
                            ChecklistItem("c1", "Local AES-256-GCM authenticated cipher", true),
                            ChecklistItem("c2", "100,000 rounds PBKDF2 key derivation", true),
                            ChecklistItem("c3", "Strict zero-telemetry local storage", true),
                            ChecklistItem("c4", "Emergency panic lock & duress disguise", true),
                            ChecklistItem("c5", "Local trash with selective & bulk purge", true)
                        ),
                        tags = listOf("Security", "Encryption", "Architecture"),
                        category = "Security",
                        isPinned = true,
                        isEncrypted = true
                    ),
                    Note(
                        id = "note-welcome-2",
                        title = "Iris Liquid Glass Design System",
                        content = "Crafted with Jetpack Compose:\n\n- Typography: Generous line heights & editorial balance.\n- Jewel Accents: Royal Violet (#5925DC) and Radiant Amethyst (#9B51E0).\n- Command Island: Bottom floating control bar.",
                        checklist = listOf(
                            ChecklistItem("c6", "Liquid glass floating toolbar", true),
                            ChecklistItem("c7", "Tactile cards with subtle whisper borders", true),
                            ChecklistItem("c8", "Android native biometric & PIN lock integration", true)
                        ),
                        tags = listOf("Design", "Iris", "Interface"),
                        category = "Ideas",
                        isPinned = true,
                        isEncrypted = true
                    ),
                    Note(
                        id = "note-welcome-3",
                        title = "Hardware & Operational Security Checklist",
                        content = "When handling sensitive thoughts, operational hygiene is just as critical as software encryption.",
                        checklist = listOf(
                            ChecklistItem("c9", "Set auto-lock interval to 5 minutes", true),
                            ChecklistItem("c10", "Enable Android native lock screen in settings", false),
                            ChecklistItem("c11", "Configure duress code for sensitive environments", false),
                            ChecklistItem("c12", "Export offline encrypted vault backup", false)
                        ),
                        tags = listOf("OpSec", "Checklist"),
                        category = "Personal",
                        isPinned = false,
                        isEncrypted = true
                    )
                )
                noteDao.insertAll(seedNotes)
            }
        }
    }
}
