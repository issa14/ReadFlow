package com.readflow.domain.repository

import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Progress
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
