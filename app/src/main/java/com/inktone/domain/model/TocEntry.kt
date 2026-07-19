package com.inktone.domain.model

/** Entrée de la table des matières, avec le vrai titre défini par l'auteur. */
data class TocEntry(
    val index: Int,        // Index dans la liste plate du TOC (correspond à Chapter.index)
    val title: String,     // Titre réel ("Chapitre 7 — La Trahison")
    val level: Int = 0     // Profondeur d'imbrication (0 = racine)
)
