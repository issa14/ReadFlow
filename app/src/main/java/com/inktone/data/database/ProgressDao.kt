package com.inktone.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.data.database.entity.ProgressEntity

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): ProgressEntity?

    @Query("SELECT * FROM progress")
    suspend fun getAllSync(): List<ProgressEntity>
}
