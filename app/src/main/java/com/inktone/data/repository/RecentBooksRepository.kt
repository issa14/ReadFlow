package com.inktone.data.repository

import com.inktone.data.database.RecentBookDao
import com.inktone.data.database.entity.RecentBookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire LRU de l'historique des livres récemment ouverts.
 *
 * Garantit un maximum de 30 entrées. Quand un livre est ouvert :
 * - S'il existe déjà → mis à jour et replacé en tête (lastOpened rafraîchi).
 * - S'il n'existe pas → inséré en tête.
 * - Si la limite est dépassée → le plus ancien est supprimé.
 *
 * Thread-safety : Room gère nativement la concurrence (les DAO sont
 * suspendus et sérialisés par Room), aucun Mutex supplémentaire n'est nécessaire.
 */
@Singleton
class RecentBooksRepository @Inject constructor(
    private val dao: RecentBookDao
) {
    companion object {
        const val MAX_RECENT_BOOKS = 30
    }

    /** Flux réactif de la liste triée (plus récent en premier). */
    val recentBooks: Flow<List<RecentBookEntity>> = dao.getRecentBooks()

    /** Enregistre l'ouverture d'un livre. */
    suspend fun openBook(book: RecentBookEntity) {
        dao.upsert(book.copy(lastOpened = System.currentTimeMillis()))
        dao.trimToLimit()
    }

    /** Supprime un livre de l'historique. */
    suspend fun removeBook(bookId: String) {
        dao.deleteByBookId(bookId)
    }

    /** Vide intégralement l'historique. */
    suspend fun clearAll() {
        dao.clearAll()
    }
}
