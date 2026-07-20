package com.inktone.data.mapper

import com.inktone.domain.model.Book
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BookMapperTest {

    private val book = Book(
        id = "book-1",
        title = "Les Misérables",
        author = "Victor Hugo",
        description = null,
        coverPath = null,
        totalChapters = 3,
        language = "fr",
        addedAt = 1234567890L,
        isFavorite = true,
        seriesName = "Les Classiques",
        seriesIndex = 2.5f
    )

    @Test
    fun `round-trip favori, serie et index de serie`() {
        val roundTripped = book.toEntity(filePath = "/tmp/book.epub").toDomain()

        assertEquals(book.isFavorite, roundTripped.isFavorite)
        assertEquals(book.seriesName, roundTripped.seriesName)
        assertEquals(book.seriesIndex, roundTripped.seriesIndex)
    }

    @Test
    fun `valeurs par defaut quand favori et serie absents`() {
        val plain = book.copy(isFavorite = false, seriesName = null, seriesIndex = null)
        val roundTripped = plain.toEntity(filePath = "/tmp/book.epub").toDomain()

        assertEquals(false, roundTripped.isFavorite)
        assertEquals(null, roundTripped.seriesName)
        assertEquals(null, roundTripped.seriesIndex)
    }
}
