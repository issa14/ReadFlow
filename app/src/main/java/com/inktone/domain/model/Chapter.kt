package com.inktone.domain.model

/** Chapitre d'un livre. */
data class Chapter(
    val index: Int,
    val title: String,
    val sentences: List<Sentence>,
    val richBlocks: List<RichBlock> = emptyList(),
    /** Dossier absolu où l'EPUB a été extrait — sert à résoudre les [RichBlock.EpubImage]. */
    val epubDir: String = ""
)
