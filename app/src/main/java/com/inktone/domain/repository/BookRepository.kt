package com.inktone.domain.repository

import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Progress
import java.io.InputStream

interface BookRepository {
    /** Importe un EPUB depuis un InputStream et retourne le [Book] créé. */
    suspend fun importEpub(
        inputStream: InputStream,
        fileName: String,
        sourceFolder: String? = null,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Book

    /** Récupère un chapitre complet (texte découpé en phrases). */
    suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter

    /** Liste tous les livres importés. */
    suspend fun getAllBooks(): List<Book>

    /** Sauvegarde/charge la progression de lecture. */
    suspend fun saveProgress(progress: Progress)
    suspend fun getProgress(bookId: String): Progress?

    /** Ré-extrait la couverture d'un livre depuis son EPUB source. Retourne le nouveau chemin, ou null si aucune couverture trouvée. */
    suspend fun regenerateCover(bookId: String): String?

    /** Retire les couvertures de tous les livres (retour au placeholder dégradé automatique). */
    suspend fun clearAllCovers()

    /** Bascule le statut favori d'un livre. */
    suspend fun setFavorite(bookId: String, isFavorite: Boolean)

    /** Liste dédupliquée de tous les tags (subjects EPUB) présents dans la bibliothèque. */
    suspend fun getAllTags(): List<String>
}
