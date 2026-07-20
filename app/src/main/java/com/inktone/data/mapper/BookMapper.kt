package com.inktone.data.mapper

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inktone.data.database.entity.BookEntity
import com.inktone.data.database.entity.ProgressEntity
import com.inktone.domain.model.Book
import com.inktone.domain.model.Progress
import com.inktone.domain.model.TocEntry

private val gson = Gson()
private val tocEntryListType = object : TypeToken<List<TocEntry>>() {}.type
private val stringListType = object : TypeToken<List<String>>() {}.type

private fun List<TocEntry>.toJson(): String = gson.toJson(this)
private fun String.toTocEntryList(): List<TocEntry> =
    if (isBlank()) emptyList() else gson.fromJson(this, tocEntryListType) ?: emptyList()

private fun List<String>.toJsonList(): String = gson.toJson(this)
private fun String.toStringList(): List<String> =
    if (isBlank()) emptyList() else gson.fromJson(this, stringListType) ?: emptyList()

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    coverPath = coverPath,
    totalChapters = totalChapters,
    language = language,
    addedAt = addedAt,
    tocEntries = tocJson.toTocEntryList(),
    publisher = publisher,
    publishedDate = publishedDate,
    subjects = subjects.toStringList(),
    isbn = isbn,
    isFavorite = isFavorite,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    sourceFolder = sourceFolder
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
    addedAt = addedAt,
    tocJson = tocEntries.toJson(),
    publisher = publisher,
    publishedDate = publishedDate,
    subjects = subjects.toJsonList(),
    isbn = isbn,
    isFavorite = isFavorite,
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    sourceFolder = sourceFolder
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
