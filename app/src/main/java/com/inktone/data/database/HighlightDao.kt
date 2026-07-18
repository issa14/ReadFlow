package com.inktone.data.database

import androidx.room.*
import com.inktone.data.database.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapterIndex ASC, sentenceIndex ASC, startOffset ASC")
    fun getHighlightsForBook(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY sentenceIndex ASC, startOffset ASC")
    fun getHighlightsForChapter(bookId: String, chapterIndex: Int): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)
}
