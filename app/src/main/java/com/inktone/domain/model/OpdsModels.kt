package com.inktone.domain.model

/**
 * Flux OPDS parsé contenant les métadonnées, entrées et liens.
 */
data class OpdsFeed(
    val title: String = "",
    val iconUrl: String? = null,
    val description: String? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val navigationLinks: List<OpdsLink> = emptyList(),
    val acquisitionLinks: List<OpdsLink> = emptyList(),
    val nextPageUrl: String? = null,
    val searchUrl: String? = null
)

/**
 * Une entrée dans un flux OPDS (livre, catégorie, sous-catalogue).
 */
data class OpdsEntry(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val summary: String? = null,
    val coverUrl: String? = null,
    val thumbnailUrl: String? = null,
    val epubDownloadUrl: String? = null,
    val pdfDownloadUrl: String? = null,
    val links: List<OpdsLink> = emptyList(),
    val publishedDate: String? = null,
    val publisher: String? = null
)

/**
 * Lien OPDS générique (navigation, acquisition, image, recherche).
 */
data class OpdsLink(
    val href: String,
    val rel: String = "",
    val title: String? = null,
    val type: String? = null
)

/**
 * Configuration d'un serveur/catalogue OPDS.
 */
data class OpdsCatalog(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val username: String? = null,
    val password: String? = null,
    val description: String? = null
)
