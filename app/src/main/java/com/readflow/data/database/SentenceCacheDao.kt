package com.readflow.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.readflow.data.database.entity.SentenceCacheEntity

/**
 * DAO pour le cache de segmentation des phrases.
 *
 * Toutes les opérations sont suspendues pour s'exécuter
 * hors du thread principal via les coroutines Room.
 */
@Dao
interface SentenceCacheDao {

    /**
     * Récupère toutes les phrases déjà segmentées pour un chapitre.
     * Ordonnées par sentenceIndex croissant.
     *
     * @return Liste vide si le chapitre n'a jamais été segmenté.
     */
    @Query("""
        SELECT * FROM sentence_cache
        WHERE bookId = :bookId AND chapterIndex = :chapterIndex
        ORDER BY sentenceIndex ASC
    """)
    suspend fun getSentences(bookId: String, chapterIndex: Int): List<SentenceCacheEntity>

    /**
     * Insère toutes les phrases d'un chapitre en une transaction atomique.
     *
     * Stratégie REPLACE : si la clé composite existe déjà, remplace.
     * Cela permet de ré-importer un livre sans supprimer manuellement
     * le cache précédent.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sentences: List<SentenceCacheEntity>)

    /**
     * Supprime le cache de segmentation pour un livre entier.
     *
     * Appelé lors de la suppression d'un livre de la bibliothèque
     * ou de la ré-importation d'un EPUB.
     */
    @Query("DELETE FROM sentence_cache WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    /**
     * Vérifie si un chapitre a déjà été segmenté.
     *
     * Utilisé pour décider si on doit lancer la segmentation
     * ou simplement charger depuis le cache.
     */
    @Query("SELECT COUNT(*) FROM sentence_cache WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun hasSentences(bookId: String, chapterIndex: Int): Int

    /**
     * Met à jour le titre de chapitre pour toutes les phrases d'un chapitre.
     * Appelé après l'import pour éviter de rouvrir l'EPUB plus tard.
     */
    @Query("UPDATE sentence_cache SET chapterTitle = :title WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun updateChapterTitle(bookId: String, chapterIndex: Int, title: String)
}
