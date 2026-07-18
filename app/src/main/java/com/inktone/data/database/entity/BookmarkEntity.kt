package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String,       // extrait du texte pour l'aperçu
    val createdAt: Long = System.currentTimeMillis()
)
