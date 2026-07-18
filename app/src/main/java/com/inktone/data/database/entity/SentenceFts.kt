package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "sentence_fts")
data class SentenceFts(
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String
)
