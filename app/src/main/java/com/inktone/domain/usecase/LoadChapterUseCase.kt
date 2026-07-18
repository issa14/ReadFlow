package com.inktone.domain.usecase

import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.domain.model.Chapter
import com.inktone.domain.repository.BookRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat du chargement d'un chapitre avec ses annotations.
 */
data class ChapterWithAnnotations(
    val chapter: Chapter,
    val highlights: List<HighlightEntity>,
    val bookmarks: List<BookmarkEntity>
)

/**
 * Charge un chapitre et ses annotations (highlights, bookmarks)
 * en une seule opération atomique.
 *
 * Extraite de [ReaderViewModel.loadChapter()] pour isoler
 * l'orchestration multi-sources (repository + DAOs) du ViewModel.
 */
@Singleton
class LoadChapterUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao
) {
    /**
     * Charge un chapitre avec ses annotations associées.
     *
     * @param bookId Identifiant du livre.
     * @param chapterIndex Index du chapitre (0-based).
     * @return [ChapterWithAnnotations] contenant le chapitre et ses annotations.
     * @throws Exception si le chapitre est introuvable ou si le chargement échoue.
     */
    suspend operator fun invoke(
        bookId: String,
        chapterIndex: Int
    ): ChapterWithAnnotations {
        val chapter = bookRepository.getChapter(bookId, chapterIndex)
        val highlights = highlightDao.getHighlightsForChapter(bookId, chapterIndex).first()
        val bookmarks = bookmarkDao.getBookmarks(bookId).first()

        return ChapterWithAnnotations(
            chapter = chapter,
            highlights = highlights,
            bookmarks = bookmarks
        )
    }
}
