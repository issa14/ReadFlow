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
    val addedAt: Long
)
