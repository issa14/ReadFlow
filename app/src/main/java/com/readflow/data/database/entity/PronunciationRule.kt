package com.readflow.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pronunciation_rules")
data class PronunciationRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
