package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String?,
    val filePath: String,
    val coverPath: String?,
    val totalChapters: Int,
    val language: String,
    val addedAt: Long,
    val tocJson: String = "[]",       // List<TocEntry> sérialisée en JSON
    val publisher: String? = null,
    val publishedDate: String? = null,
    val subjects: String = "[]",      // List<String> sérialisée en JSON
    val isbn: String? = null
)
