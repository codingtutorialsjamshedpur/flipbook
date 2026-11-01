package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DocumentEntity

@Database(entities = [DocumentEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        /**
         * Stable IDs used by the demo fixtures that older app versions auto-seeded into the
         * library on every launch. They are tagged as demo records by [MIGRATION_1_2] so the
         * Library can hide them, without ever deleting genuine user documents.
         */
        val LEGACY_DEMO_DOCUMENT_IDS = listOf(
            "sample_pdf_10_pages",
            "sample_hindi_panch",
            "sample_noir_script",
            "sample_alchemist",
            "sample_scifi_script"
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
                val placeholders = LEGACY_DEMO_DOCUMENT_IDS.joinToString(",") { "?" }
                db.execSQL(
                    "UPDATE documents SET isDemo = 1 WHERE id IN ($placeholders)",
                    LEGACY_DEMO_DOCUMENT_IDS.toTypedArray()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "storyflip_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
