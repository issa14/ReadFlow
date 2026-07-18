package com.inktone.data.database

import androidx.room.*
import com.inktone.data.database.entity.BookProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: BookProgressEntity)

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): BookProgressEntity?

    @Query("SELECT * FROM book_progress")
    fun getAllProgress(): Flow<List<BookProgressEntity>>
}
