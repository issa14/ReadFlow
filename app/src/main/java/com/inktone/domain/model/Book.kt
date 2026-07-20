package com.inktone.domain.model

/** Livre importé dans la bibliothèque. */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val description: String?,
    val coverPath: String? = null,
    val totalChapters: Int,
    val language: String,
    val addedAt: Long,
    val tocEntries: List<TocEntry> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val subjects: List<String> = emptyList(),
    val isbn: String? = null,
    val isFavorite: Boolean = false,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val sourceFolder: String? = null
)
