package com.inktone.domain.usecase

import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.AnnotationEntity
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat d'une opération d'annotation (bookmark, highlight, annotation).
 */
sealed class AnnotationResult {
    data class Success(val message: String) : AnnotationResult()
    data class Error(val message: String) : AnnotationResult()
}

/**
 * Résultat du rechargement des annotations.
 */
data class ReloadedAnnotations(
    val highlights: List<HighlightEntity>,
    val bookmarks: List<BookmarkEntity>
)

/**
 * Gère les opérations CRUD sur les annotations de lecture :
 * marque-pages, surlignages et annotations textuelles.
 *
 * Extraites de [ReaderViewModel] pour isoler la logique d'accès
 * aux données et la rendre testable indépendamment du ViewModel.
 */
@Singleton
class ManageReaderAnnotationsUseCase @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val annotationDao: AnnotationDao
) {
    /**
     * Ajoute ou retire un marque-page (toggle).
     *
     * @return [AnnotationResult.Success] avec le message approprié.
     */
    suspend fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        sentenceIndex: Int,
        text: String
    ): AnnotationResult {
        val existing = bookmarkDao.findByPosition(bookId, chapterIndex, sentenceIndex)
        return if (existing != null) {
            bookmarkDao.delete(existing)
            AnnotationResult.Success("Marque-page retiré")
        } else {
            bookmarkDao.insert(
                BookmarkEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    sentenceIndex = sentenceIndex,
                    text = text.take(120)
                )
            )
            AnnotationResult.Success("Marque-page ajouté")
        }
    }

    /**
     * Ajoute un surlignage.
     */
    suspend fun addHighlight(
        bookId: String,
        chapterIndex: Int,
        sentenceIndex: Int,
        selectedText: String,
        startOffset: Int,
        endOffset: Int
    ): AnnotationResult {
        highlightDao.insertHighlight(
            HighlightEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                sentenceIndex = sentenceIndex,
                startOffset = startOffset,
                endOffset = endOffset,
                selectedText = selectedText,
                colorHex = "#FFEB3D"
            )
        )
        return AnnotationResult.Success("Surlignage ajouté")
    }

    /**
     * Ajoute une annotation textuelle.
     */
    suspend fun addAnnotation(
        bookId: String,
        chapterIndex: Int,
        sentenceIndex: Int,
        selectedText: String
    ): AnnotationResult {
        annotationDao.insertAnnotation(
            AnnotationEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                sentenceIndex = sentenceIndex,
                selectedText = selectedText,
                colorHex = "#FFF9C4"
            )
        )
        return AnnotationResult.Success("Annotation ajoutée")
    }

    /**
     * Recharge les highlights et bookmarks pour un chapitre donné.
     */
    suspend fun reloadAnnotations(
        bookId: String,
        chapterIndex: Int
    ): ReloadedAnnotations {
        val highlights = highlightDao.getHighlightsForChapter(bookId, chapterIndex).first()
        val bookmarks = bookmarkDao.getBookmarks(bookId).first()
        return ReloadedAnnotations(highlights = highlights, bookmarks = bookmarks)
    }
}
