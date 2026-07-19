package com.inktone.domain.model

/**
 * Bloc de contenu sémantique extrait d'un fichier XHTML EPUB.
 *
 * Préserve la structure de l'auteur (headings, emphasis, blockquotes, images)
 * que [Sentence] ne peut pas représenter (texte plat uniquement).
 *
 * Coexistence avec [Sentence] :
 * - [Sentence] = unité TTS (segmentation phrases pour la synthèse vocale)
 * - [RichBlock] = unité d'affichage (rendu visuel avec structure typographique)
 * La même source HTML génère DEUX représentations pour DEUX usages distincts.
 */
sealed class RichBlock {

    /** Index du bloc dans le chapitre (0-based). */
    abstract val index: Int

    /** Paragraphe standard (texte courant). */
    data class Paragraph(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Titre de section (h1…h4 EPUB). */
    data class Heading(
        override val index: Int,
        val level: Int,          // 1 = h1, 2 = h2, …
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Citation (blockquote). */
    data class BlockQuote(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Ligne de poème / vers (class="verse", "stanza", "poem"). */
    data class PoemLine(
        override val index: Int,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Image EPUB (href → path relatif dans le ZIP). */
    data class EpubImage(
        override val index: Int,
        val href: String,        // chemin relatif dans l'EPUB
        val alt: String = ""
    ) : RichBlock()

    /** Note de bas de page (epub:type="footnote", "endnote"). */
    data class Footnote(
        override val index: Int,
        val noteId: String,
        val spans: List<TextSpan>
    ) : RichBlock()

    /** Séparateur de section (hr, class="separator"). */
    data class SectionBreak(override val index: Int) : RichBlock()
}

/**
 * Span de texte avec style inline.
 *
 * Évite de matérialiser toutes les combinaisons de styles comme des
 * sous-classes hermétiques — un TextSpan peut être bold ET italic.
 */
data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val superscript: Boolean = false,  // exposants (notes de bas de page)
    val noteRef: String? = null        // ID de la note référencée (si lien footnote)
)
