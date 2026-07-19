package com.inktone.data.database.entity

import androidx.room.Entity

/**
 * Cache des blocs de contenu sémantique ([com.inktone.domain.model.RichBlock])
 * extraits d'un chapitre EPUB, pour le rendu typographique riche du Reader.
 *
 * Coexiste avec [SentenceCacheEntity] (segmentation TTS) — même source HTML,
 * deux représentations pour deux usages distincts.
 */
@Entity(
    tableName = "rich_block_cache",
    primaryKeys = ["bookId", "chapterIndex", "blockIndex"]
)
data class RichBlockCacheEntity(
    val bookId: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val type: String,        // "paragraph", "heading", "blockquote", "poem", "image", "footnote", "break"
    val level: Int = 0,      // Pour Heading uniquement
    val href: String = "",   // Pour EpubImage uniquement
    val alt: String = "",    // Pour EpubImage
    val noteId: String = "", // Pour Footnote uniquement
    val spansJson: String = ""  // List<TextSpan> sérialisée en JSON avec Gson
)
