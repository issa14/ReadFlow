package com.inktone.data.database

import androidx.room.*
import com.inktone.data.database.entity.RecentBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentBookDao {

    /** Retourne la liste triée du plus récent au plus ancien (max 30). */
    @Query("SELECT * FROM recent_books ORDER BY lastOpened DESC LIMIT 30")
    fun getRecentBooks(): Flow<List<RecentBookEntity>>

    /** Compte le nombre d'entrées actuelles. */
    @Query("SELECT COUNT(*) FROM recent_books")
    suspend fun count(): Int

    /** Insère ou remplace un livre récent (l'upsert met à jour lastOpened). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: RecentBookEntity)

    /** Supprime les entrées les plus anciennes au-delà de la limite. */
    @Query("""
        DELETE FROM recent_books WHERE bookId IN (
            SELECT bookId FROM recent_books ORDER BY lastOpened ASC LIMIT MAX(0, (SELECT COUNT(*) FROM recent_books) - 30)
        )
    """)
    suspend fun trimToLimit()

    /** Supprime un livre spécifique de l'historique. */
    @Query("DELETE FROM recent_books WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)

    /** Vide tout l'historique. */
    @Query("DELETE FROM recent_books")
    suspend fun clearAll()
}
