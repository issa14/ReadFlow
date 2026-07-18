package com.inktone.data.sync

import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.PronunciationRule
import com.inktone.data.database.entity.ProgressEntity
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.data.database.entity.ReadingSessionEntity

/**
 * Schéma JSON unifié exporté/chiffré pour la synchronisation cloud.
 *
 * Regroupe toutes les données utilisateur nécessaires à une restauration
 * complète de l'état de lecture InkTone.
 */
data class BackupPayload(
    val version: Int = 1,
    val appVersion: String = "0.1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val averageWpm: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val progressEntries: List<ProgressEntity> = emptyList(),
    val readingProgressList: List<ReadingProgress> = emptyList(),
    val readingSessions: List<ReadingSessionEntity> = emptyList()
)
