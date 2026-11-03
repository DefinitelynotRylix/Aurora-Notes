package com.aurora.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.notes.model.Note

@Database(entities = [Note::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AuroraDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(`notes`)")
                var hasStyleSpansColumn = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "styleSpansJson") {
                            hasStyleSpansColumn = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }
                if (!hasStyleSpansColumn) {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `styleSpansJson` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(`notes`)")
                var hasArchivedColumn = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "isArchived") {
                            hasArchivedColumn = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }
                if (!hasArchivedColumn) {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun getDatabase(context: Context): AuroraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuroraDatabase::class.java,
                    "aurora_notes.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
