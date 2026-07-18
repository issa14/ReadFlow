package com.inktone.data.database.entity

import androidx.room.Entity

/**
 * Cache de segmentation des phrases dans Room.
 *
 * **Objectif :** Chaque chapitre n'est découpé en phrases qu'une seule fois.
 * Les résultats sont persistés dans SQLite, évitant la re-segmentation
 * coûteuse (BreakIterator + filtrage) à chaque ouverture de chapitre.
 *
 * Clé composite : (bookId, chapterIndex, sentenceIndex).
 * Un index sur (bookId, chapterIndex) permet de charger toutes les phrases
 * d'un chapitre en une seule requête.
 *
 * Invalidation : les phrases sont immuables une fois générées. Si le texte
 * source change (re-import du livre), l'ensemble du cache pour ce bookId
 * doit être invalidé via [SentenceCacheDao.deleteForBook].
 */
@Entity(
    tableName = "sentence_cache",
    primaryKeys = ["bookId", "chapterIndex", "sentenceIndex"]
)
data class SentenceCacheEntity(
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val chapterTitle: String = ""
)
