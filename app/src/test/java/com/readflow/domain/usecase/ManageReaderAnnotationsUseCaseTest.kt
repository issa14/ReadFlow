package com.readflow.domain.usecase

import com.readflow.data.database.AnnotationDao
import com.readflow.data.database.BookmarkDao
import com.readflow.data.database.HighlightDao
import com.readflow.data.database.entity.BookmarkEntity
import com.readflow.data.database.entity.HighlightEntity
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManageReaderAnnotationsUseCaseTest {

    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val annotationDao = mockk<AnnotationDao>(relaxed = true)
    private lateinit var useCase: ManageReaderAnnotationsUseCase

    @BeforeEach
    fun setUp() {
        useCase = ManageReaderAnnotationsUseCase(bookmarkDao, highlightDao, annotationDao)
    }

    // ── Bookmark toggle ─────────────────────────────────

    @Test
    fun `toggleBookmark ajoute si absent`() = runTest {
        coEvery { bookmarkDao.findByPosition(any(), any(), any()) } returns null
        coEvery { bookmarkDao.insert(any()) } just Runs

        val result = useCase.toggleBookmark("book-1", 0, 0, "Bonjour.")

        assertTrue(result is AnnotationResult.Success)
        assertEquals("Marque-page ajouté", (result as AnnotationResult.Success).message)
        coVerify(exactly = 1) { bookmarkDao.insert(any()) }
        coVerify(exactly = 0) { bookmarkDao.delete(any()) }
    }

    @Test
    fun `toggleBookmark retire si présent`() = runTest {
        val existing = BookmarkEntity(bookId = "book-1", chapterIndex = 0, sentenceIndex = 0, text = "Bonjour.")
        coEvery { bookmarkDao.findByPosition(any(), any(), any()) } returns existing
        coEvery { bookmarkDao.delete(any()) } just Runs

        val result = useCase.toggleBookmark("book-1", 0, 0, "Bonjour.")

        assertTrue(result is AnnotationResult.Success)
        assertEquals("Marque-page retiré", (result as AnnotationResult.Success).message)
        coVerify(exactly = 1) { bookmarkDao.delete(existing) }
        coVerify(exactly = 0) { bookmarkDao.insert(any()) }
    }

    @Test
    fun `toggleBookmark tronque le texte à 120 caractères`() = runTest {
        val longText = "A".repeat(200)
        val capturedBookmark = slot<BookmarkEntity>()
        coEvery { bookmarkDao.findByPosition(any(), any(), any()) } returns null
        coEvery { bookmarkDao.insert(capture(capturedBookmark)) } just Runs

        useCase.toggleBookmark("book-1", 0, 0, longText)

        assertEquals(120, capturedBookmark.captured.text.length)
    }

    // ── Highlight ───────────────────────────────────────

    @Test
    fun `addHighlight insère un surlignage`() = runTest {
        val captured = slot<HighlightEntity>()
        coEvery { highlightDao.insertHighlight(capture(captured)) } returns 1L

        val result = useCase.addHighlight("book-1", 0, 1, "mot", 5, 8)

        assertTrue(result is AnnotationResult.Success)
        assertEquals("Surlignage ajouté", (result as AnnotationResult.Success).message)
        assertEquals("book-1", captured.captured.bookId)
        assertEquals(0, captured.captured.chapterIndex)
        assertEquals(1, captured.captured.sentenceIndex)
        assertEquals("mot", captured.captured.selectedText)
        assertEquals("#FFEB3D", captured.captured.colorHex)
    }

    // ── Annotation ──────────────────────────────────────

    @Test
    fun `addAnnotation insère une annotation`() = runTest {
        val result = useCase.addAnnotation("book-1", 0, 2, "Note importante")

        assertTrue(result is AnnotationResult.Success)
        assertEquals("Annotation ajoutée", (result as AnnotationResult.Success).message)
        coVerify(exactly = 1) { annotationDao.insertAnnotation(any()) }
    }

    // ── Reload ──────────────────────────────────────────

    @Test
    fun `reloadAnnotations retourne highlights et bookmarks`() = runTest {
        val highlights = listOf(
            HighlightEntity(bookId = "b1", chapterIndex = 0, sentenceIndex = 0,
                startOffset = 0, endOffset = 4, selectedText = "test", colorHex = "#FF0")
        )
        val bookmarks = listOf(
            BookmarkEntity(bookId = "b1", chapterIndex = 0, sentenceIndex = 0, text = "test")
        )

        every { highlightDao.getHighlightsForChapter("book-1", 0) } returns flowOf(highlights)
        every { bookmarkDao.getBookmarks("book-1") } returns flowOf(bookmarks)

        val reloaded = useCase.reloadAnnotations("book-1", 0)

        assertEquals(1, reloaded.highlights.size)
        assertEquals(1, reloaded.bookmarks.size)
    }
}
