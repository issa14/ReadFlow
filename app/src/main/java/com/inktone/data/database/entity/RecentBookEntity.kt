package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entrée dans l'historique des livres récemment ouverts.
 *
 * Gérée avec une stratégie LRU : maximum 30 entrées, la plus récente
 * en premier, la plus ancienne supprimée automatiquement.
 */
@Entity(tableName = "recent_books")
data class RecentBookEntity(
    @PrimaryKey val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String = "",
    val lastOpened: Long = System.currentTimeMillis(),
    val progress: Int = 0,
    val readingTimeSpent: Long = 0
)
