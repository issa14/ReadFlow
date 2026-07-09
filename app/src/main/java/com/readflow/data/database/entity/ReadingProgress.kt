package com.readflow.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistance de la progression de lecture.
 *
 * Stockée de manière atomique à chaque transition de phrase
 * pour permettre une reprise exacte après arrêt, crash ou
 * passage en arrière-plan.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val characterOffset: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
