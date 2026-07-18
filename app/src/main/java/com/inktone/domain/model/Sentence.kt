package com.inktone.domain.model

/** Phrase d'un chapitre avec sa position. */
data class Sentence(
    val index: Int,
    val text: String,
    val startOffset: Int,  // offset caractère dans le chapitre
    val endOffset: Int
)
