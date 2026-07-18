package com.inktone.domain.usecase

import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Sentence
import com.inktone.domain.repository.BookRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoadChapterUseCaseTest {

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private lateinit var useCase: LoadChapterUseCase

    private val testChapter = Chapter(
        index = 0,
        title = "Chapitre 1",
        sentences = listOf(
            Sentence(index = 0, text = "Bonjour.", startOffset = 0, endOffset = 8),
            Sentence(index = 1, text = "Au revoir.", startOffset = 9, endOffset = 19)
        )
    )

    @BeforeEach
    fun setUp() {
        useCase = LoadChapterUseCase(bookRepository, highlightDao, bookmarkDao)
    }

    @Test
    fun `charge un chapitre avec ses annotations`() = runTest {
        val highlights = listOf(
            HighlightEntity(
                bookId = "book-1", chapterIndex = 0, sentenceIndex = 0,
                startOffset = 0, endOffset = 4, selectedText = "Bonj", colorHex = "#FF0"
            )
        )
        val bookmarks = listOf(
            BookmarkEntity(
                bookId = "book-1", chapterIndex = 0,
                sentenceIndex = 0, text = "Bonjour."
            )
        )

        coEvery { bookRepository.getChapter("book-1", 0) } returns testChapter
        every { highlightDao.getHighlightsForChapter("book-1", 0) } returns flowOf(highlights)
        every { bookmarkDao.getBookmarks("book-1") } returns flowOf(bookmarks)

        val result = useCase("book-1", 0)

        assertEquals(testChapter, result.chapter)
        assertEquals(1, result.highlights.size)
        assertEquals(1, result.bookmarks.size)
        assertEquals("Bonj", result.highlights[0].selectedText)
        assertEquals("Bonjour.", result.bookmarks[0].text)
    }

    @Test
    fun `chapitre sans annotations retourne des listes vides`() = runTest {
        coEvery { bookRepository.getChapter("book-1", 0) } returns testChapter
        every { highlightDao.getHighlightsForChapter("book-1", 0) } returns flowOf(emptyList())
        every { bookmarkDao.getBookmarks("book-1") } returns flowOf(emptyList())

        val result = useCase("book-1", 0)

        assertEquals(testChapter, result.chapter)
        assertTrue(result.highlights.isEmpty())
        assertTrue(result.bookmarks.isEmpty())
    }

    @Test
    fun `toutes les sources de données sont appelées`() = runTest {
        coEvery { bookRepository.getChapter("book-1", 2) } returns testChapter
        every { highlightDao.getHighlightsForChapter("book-1", 2) } returns flowOf(emptyList())
        every { bookmarkDao.getBookmarks("book-1") } returns flowOf(emptyList())

        useCase("book-1", 2)

        coVerify(exactly = 1) { bookRepository.getChapter("book-1", 2) }
        verify(exactly = 1) { highlightDao.getHighlightsForChapter("book-1", 2) }
        verify(exactly = 1) { bookmarkDao.getBookmarks("book-1") }
    }
}
