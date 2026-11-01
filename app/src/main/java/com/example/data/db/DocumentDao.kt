package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastReadPage = :page, hasProgress = 1, lastOpenedAt = :timestamp WHERE id = :documentId")
    suspend fun updateReadingProgress(documentId: String, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET bookmarkPage = :page, hasProgress = 1 WHERE id = :documentId")
    suspend fun updateBookmark(documentId: String, page: Int)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)
}
