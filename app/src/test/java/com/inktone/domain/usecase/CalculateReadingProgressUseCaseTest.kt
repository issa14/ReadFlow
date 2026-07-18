package com.inktone.domain.usecase

import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Progress
import com.inktone.domain.model.Sentence
import com.inktone.domain.repository.BookRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalculateReadingProgressUseCaseTest {

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private lateinit var useCase: CalculateReadingProgressUseCase

    private val testBook = Book(
        id = "book-1",
        title = "Test",
        author = "Auteur",
        description = null,
        totalChapters = 10,
        language = "fr",
        addedAt = 0
    )

    @BeforeEach
    fun setUp() {
        useCase = CalculateReadingProgressUseCase(bookRepository)
    }

    @Test
    fun `début du livre retourne 0`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 0, sentenceIndex = 0, totalSentences = 100)
        assertEquals(0f, fraction)
    }

    @Test
    fun `fin du livre retourne environ 1`() = runTest {
        // Dernier chapitre, dernière phrase
        val fraction = useCase(
            testBook,
            chapterIndex = 9, // dernier chapitre (0-based, 10 total)
            sentenceIndex = 99, // dernière phrase (0-based, 100 total)
            totalSentences = 100
        )
        assertTrue(fraction >= 0.99f, "La fraction en fin de livre doit être proche de 1 (trouvé: $fraction)")
        assertTrue(fraction <= 1f, "La fraction ne doit pas dépasser 1 (trouvé: $fraction)")
    }

    @Test
    fun `milieu du livre retourne environ 0_5`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 4, sentenceIndex = 50, totalSentences = 100)
        assertEquals(0.45f, fraction, 0.01f)
    }

    @Test
    fun `totalSentences à 0 ne cause pas de division par zéro`() = runTest {
        val fraction = useCase(testBook, chapterIndex = 5, sentenceIndex = 0, totalSentences = 0)
        assertTrue(fraction in 0f..1f, "La fraction doit être dans [0,1], trouvé: $fraction")
    }

    @Test
    fun `totalChapters à 0 ne cause pas de division par zéro`() = runTest {
        val emptyBook = testBook.copy(totalChapters = 0)
        val fraction = useCase(emptyBook, chapterIndex = 0, sentenceIndex = 0, totalSentences = 10)
        assertTrue(fraction in 0f..1f, "La fraction doit être dans [0,1], trouvé: $fraction")
    }

    @Test
    fun `fraction toujours clampée entre 0 et 1`() = runTest {
        // Cas extrême : chapterIndex > totalChapters
        val fraction = useCase(testBook, chapterIndex = 100, sentenceIndex = 100, totalSentences = 1)
        assertEquals(1f, fraction)
    }

    @Test
    fun `le progrès est bien persisté via le repository`() = runTest {
        val savedProgress = slot<Progress>()
        coEvery { bookRepository.saveProgress(capture(savedProgress)) } just Runs

        useCase(testBook, chapterIndex = 3, sentenceIndex = 42, totalSentences = 100)

        coVerify(exactly = 1) { bookRepository.saveProgress(any()) }
        assertEquals("book-1", savedProgress.captured.bookId)
        assertEquals(3, savedProgress.captured.currentChapterIndex)
        assertEquals(42, savedProgress.captured.currentSentenceIndex)
    }
}
