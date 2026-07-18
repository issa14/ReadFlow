package com.inktone.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.data.database.entity.ReadingProgress

/**
 * DAO pour la persistance atomique de la progression de lecture.
 *
 * Toutes les opérations sont suspendues pour s'exécuter
 * hors du thread principal via les coroutines Room.
 */
@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllSync(): List<ReadingProgress>

    /**
     * Récupère la dernière progression enregistrée pour un livre.
     * @return [ReadingProgress] ou null si aucune progression sauvegardée.
     */
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgressForBook(bookId: String): ReadingProgress?

    /**
     * Sauvegarde (insert ou remplace) la progression de lecture.
     * Stratégie REPLACE : mise à jour atomique si la clé primaire existe déjà.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgress)
}
