package com.inktone.domain.model

/** Chapitre d'un livre. */
data class Chapter(
    val index: Int,
    val title: String,
    val sentences: List<Sentence>
)
