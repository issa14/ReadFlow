package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val date: String,
    val durationSeconds: Long,
    val wordsRead: Int,
    val timestamp: Long = System.currentTimeMillis()
)
