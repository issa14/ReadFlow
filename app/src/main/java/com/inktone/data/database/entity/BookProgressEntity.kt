package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_progress")
data class BookProgressEntity(
    @PrimaryKey val bookId: String,
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val currentSentenceIndex: Int = 0,
    val totalSentencesInChapter: Int = 0,
    val globalProgressFraction: Float = 0f,
    val lastReadTimestamp: Long = System.currentTimeMillis()
)
