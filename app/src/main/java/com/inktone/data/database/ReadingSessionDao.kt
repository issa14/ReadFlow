package com.inktone.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.data.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT DISTINCT date FROM reading_sessions WHERE durationSeconds > 0 ORDER BY date DESC")
    fun getReadingDates(): Flow<List<String>>

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions WHERE date = :date")
    suspend fun getReadingSecondsForDate(date: String): Long?

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAllSync(): List<ReadingSessionEntity>
}
