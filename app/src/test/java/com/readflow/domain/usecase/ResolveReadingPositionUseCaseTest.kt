package com.readflow.domain.usecase

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResolveReadingPositionUseCaseTest {

    private lateinit var useCase: ResolveReadingPositionUseCase

    @BeforeEach
    fun setUp() {
        useCase = ResolveReadingPositionUseCase()
    }

    @Test
    fun `DB progress prioritaire sur SavedState`() {
        val result = useCase(
            dbChapterIndex = 2,
            dbSentenceIndex = 5,
            savedChapterIndex = 0,
            savedSentenceIndex = 0,
            totalChapters = 10
        )
        assertEquals(2, result.chapterIndex)
        assertEquals(5, result.sentenceIndex)
    }

    @Test
    fun `fallback sur SavedState si DB absente`() {
        val result = useCase(
            dbChapterIndex = null,
            dbSentenceIndex = null,
            savedChapterIndex = 1,
            savedSentenceIndex = 3,
            totalChapters = 10
        )
        assertEquals(1, result.chapterIndex)
        assertEquals(3, result.sentenceIndex)
    }

    @Test
    fun `chapterIndex clampé dans les bornes`() {
        val result = useCase(
            dbChapterIndex = 15, // > totalChapters
            dbSentenceIndex = 0,
            savedChapterIndex = 0,
            savedSentenceIndex = 0,
            totalChapters = 10
        )
        assertEquals(9, result.chapterIndex) // clampé à totalChapters-1
    }

    @Test
    fun `chapterIndex négatif clampé à 0`() {
        val result = useCase(
            dbChapterIndex = -5,
            dbSentenceIndex = 0,
            savedChapterIndex = 0,
            savedSentenceIndex = 0,
            totalChapters = 5
        )
        assertEquals(0, result.chapterIndex)
    }

    @Test
    fun `dbSentenceIndex null par défaut à 0`() {
        val result = useCase(
            dbChapterIndex = 3,
            dbSentenceIndex = null,
            savedChapterIndex = 0,
            savedSentenceIndex = 10,
            totalChapters = 10
        )
        assertEquals(3, result.chapterIndex)
        assertEquals(0, result.sentenceIndex)
    }
}
