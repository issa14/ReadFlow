package com.inktone.data.mapper

import com.inktone.data.database.entity.BookEntity
import com.inktone.data.database.entity.ProgressEntity
import com.inktone.domain.model.Book
import com.inktone.domain.model.Progress

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    coverPath = coverPath,
    totalChapters = totalChapters,
    language = language,
    addedAt = addedAt
)

fun Book.toEntity(filePath: String, coverPath: String? = null) = BookEntity(
    id = id,
    title = title,
    author = author,
    description = description,
    filePath = filePath,
    coverPath = coverPath,
    totalChapters = totalChapters,
    language = language,
    addedAt = addedAt
)

fun ProgressEntity.toDomain() = Progress(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentSentenceIndex = currentSentenceIndex,
    totalProgressFraction = totalProgressFraction
)

fun Progress.toEntity() = ProgressEntity(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentSentenceIndex = currentSentenceIndex,
    totalProgressFraction = totalProgressFraction,
    updatedAt = System.currentTimeMillis()
)
