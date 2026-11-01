package com.example.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Addendum §1 / §11: demo samples are never seeded into the Library, legacy auto-seeded
 * rows are migrated to `isDemo = 1` (hidden) instead of being deleted, and genuine user
 * documents always survive migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DemoLibraryMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun uniqueDbName() = "demo_migration_${UUID.randomUUID()}.db"

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS documents (",
        "id TEXT NOT NULL PRIMARY KEY,",
        "title TEXT NOT NULL,",
        "author TEXT NOT NULL,",
        "format TEXT NOT NULL,",
        "rawContent TEXT NOT NULL,",
        "totalPages INTEGER NOT NULL,",
        "lastReadPage INTEGER NOT NULL,",
        "bookmarkPage INTEGER,",
        "hasProgress INTEGER NOT NULL,",
        "createdAt INTEGER NOT NULL,",
        "lastOpenedAt INTEGER NOT NULL",
        ")"
    ).joinToString(" ")

    @Test
    fun `migration 1 to 2 tags legacy demo rows and preserves user documents`() {
        val dbName = uniqueDbName()
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()

        // Build a genuine v1 database with one legacy auto-seeded demo row and one user document
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL(v1Schema)
        raw.execSQL(
            "INSERT INTO documents (id, title, author, format, rawContent, totalPages, lastReadPage, " +
                "bookmarkPage, hasProgress, createdAt, lastOpenedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(
                "sample_pdf_10_pages", "The Quantum Manuscript (10 Pages)", "Dr. Arthur Vance",
                "PDF", "seeded demo", 10, 1, 3, 1, 1L, 1L
            )
        )
        raw.execSQL(
            "INSERT INTO documents (id, title, author, format, rawContent, totalPages, lastReadPage, " +
                "bookmarkPage, hasProgress, createdAt, lastOpenedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "user_doc_123", "My Own Novel", "Me", "TXT", "real user text", 4, 2, null, 1, 2L, 2L
            )
        )
        raw.version = 1
        raw.close()

        // Open through Room: MIGRATION_1_2 runs and Room validates the v2 schema
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val docs = runBlocking { room.documentDao().getAllDocuments().first() }
            assertEquals(2, docs.size)

            val legacy = docs.first { it.id == "sample_pdf_10_pages" }
            assertTrue("legacy seeded demo must be tagged isDemo", legacy.isDemo)
            assertEquals("The Quantum Manuscript (10 Pages)", legacy.title)

            val user = docs.first { it.id == "user_doc_123" }
            assertFalse("genuine user document must never be tagged demo", user.isDemo)
            assertEquals("real user text", user.rawContent)
        } finally {
            room.close()
        }
    }

    @Test
    fun `fresh library database starts empty and never re-seeds demo documents`() {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, uniqueDbName())
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = room.documentDao()
            assertTrue(
                "a brand-new install must have no documents until the user adds one",
                runBlocking { dao.getAllDocuments().first() }.isEmpty()
            )

            // Demo samples built from the catalog are session-only and must not appear in the
            // Library unless the user explicitly added them.
            val sample = DocumentParserService.getSampleDocuments().first()
            assertTrue(sample.isDemo)
            assertTrue(runBlocking { dao.getAllDocuments().first() }.none { it.id == sample.id })

            // A user document inserted by the app persists normally
            runBlocking {
                dao.insertDocument(
                    DocumentEntity(
                        id = "user_upload_1",
                        title = "Uploaded Book",
                        format = DocumentFormat.PDF,
                        rawContent = "pages",
                        totalPages = 2
                    )
                )
            }
            val docs = runBlocking { dao.getAllDocuments().first() }
            assertEquals(1, docs.size)
            assertFalse(docs.single().isDemo)
            assertNotNull(docs.single().id)
        } finally {
            room.close()
        }
    }
}
