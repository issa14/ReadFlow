package com.inktone.domain.model

/** Progression de lecture. */
data class Progress(
    val bookId: String,
    val currentChapterIndex: Int,
    val currentSentenceIndex: Int,
    val totalProgressFraction: Float
)
