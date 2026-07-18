package com.inktone.domain.usecase

import com.inktone.domain.model.Book
import com.inktone.domain.model.Progress
import com.inktone.domain.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Calcule la progression de lecture sous forme de fraction [0, 1]
 * et persiste le résultat via [BookRepository.saveProgress].
 *
 * La fraction est calculée comme :
 *   (chapterIndex + sentenceIndex / totalSentences) / totalChapters
 *
 * Extraite de [ReaderViewModel] pour isoler la logique métier
 * du calcul de progression et la rendre testable unitairement.
 */
@Singleton
class CalculateReadingProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Calcule et persiste la progression de lecture.
     *
     * @param book Le livre en cours de lecture.
     * @param chapterIndex L'index du chapitre courant (0-based).
     * @param sentenceIndex L'index de la phrase courante dans le chapitre (0-based).
     * @param totalSentences Nombre total de phrases dans le chapitre.
     * @return La fraction de progression calculée [0, 1].
     */
    suspend operator fun invoke(
        book: Book,
        chapterIndex: Int,
        sentenceIndex: Int,
        totalSentences: Int
    ): Float {
        val totalSent = totalSentences.coerceAtLeast(1)
        val totalChap = book.totalChapters.coerceAtLeast(1)

        val fraction = (chapterIndex.toFloat() + sentenceIndex.toFloat() / totalSent) / totalChap
        val clampedFraction = fraction.coerceIn(0f, 1f)

        val progress = Progress(
            bookId = book.id,
            currentChapterIndex = chapterIndex,
            currentSentenceIndex = sentenceIndex,
            totalProgressFraction = clampedFraction
        )

        bookRepository.saveProgress(progress)
        return clampedFraction
    }
}
