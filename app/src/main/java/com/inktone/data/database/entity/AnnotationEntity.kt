package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val selectedText: String,
    val colorHex: String,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
