package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val currentChapterIndex: Int,
    val currentSentenceIndex: Int,
    val totalProgressFraction: Float,
    val updatedAt: Long
)
