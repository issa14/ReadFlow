package com.inktone.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.data.database.entity.RichBlockCacheEntity

/**
 * DAO pour le cache des blocs de contenu sémantique ([com.inktone.domain.model.RichBlock]).
 */
@Dao
interface RichBlockCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<RichBlockCacheEntity>)

    @Query("SELECT * FROM rich_block_cache WHERE bookId = :bookId AND chapterIndex = :chapterIndex ORDER BY blockIndex ASC")
    suspend fun getBlocks(bookId: String, chapterIndex: Int): List<RichBlockCacheEntity>

    @Query("DELETE FROM rich_block_cache WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
