package com.inktone.data.database

import androidx.room.*
import com.inktone.data.database.entity.SentenceFts
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query("SELECT bookId, chapterIndex, sentenceIndex, text FROM sentence_fts WHERE sentence_fts MATCH :query AND bookId = :bookId")
    suspend fun search(bookId: String, query: String): List<SentenceFts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sentences: List<SentenceFts>)

    @Query("DELETE FROM sentence_fts WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
