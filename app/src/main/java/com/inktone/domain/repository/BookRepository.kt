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
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Book

    /** Récupère un chapitre complet (texte découpé en phrases). */
    suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter

    /** Liste tous les livres importés. */
    suspend fun getAllBooks(): List<Book>

    /** Sauvegarde/charge la progression de lecture. */
    suspend fun saveProgress(progress: Progress)
    suspend fun getProgress(bookId: String): Progress?
}
