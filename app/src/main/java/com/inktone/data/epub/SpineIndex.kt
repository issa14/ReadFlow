package com.inktone.data.epub

import org.readium.r2.shared.publication.Link

/**
 * Index de résolution entre les entrées du TOC et les positions du spine.
 *
 * Le spine ([publication.readingOrder]) est une liste plate de tous les fichiers
 * XHTML dans l'ordre de lecture. La table des matières ([publication.tableOfContents])
 * est une structure arborescente définie par l'auteur.
 *
 * [SpineIndex] permet de mapper une entrée du TOC vers la plage d'indices
 * correspondante dans le spine. Cela résout le problème des chapitres logiques
 * répartis sur plusieurs fichiers spine (ex: "ch2_part1.xhtml" + "ch2_part2.xhtml").
 *
 * @param spine Le readingOrder de la publication Readium.
 */
class SpineIndex(private val spine: List<Link>) {

    /**
     * Map interne : href normalisé (sans ancre #, sans chemin) → index dans le spine.
     *
     * Construit une seule fois à l'initialisation. Le nettoyage des ancres
     * est fait symétriquement ici (construction) et dans [resolveRange] (recherche).
     */
    private val index: Map<String, Int> = buildMap {
        spine.forEachIndexed { i, link ->
            val key = normalizeHref(link.href.toString())
            if (key.isNotBlank()) {
                put(key, i)
            }
        }
    }

    /** Nombre d'entrées dans le spine. */
    val size: Int get() = spine.size

    /**
     * Résout la plage d'indices spine correspondant à une entrée du TOC.
     *
     * Algorithme :
     * 1. Normalise le href du [tocLink] (supprime l'ancre # et le chemin)
     * 2. Cherche l'index exact dans le map interne
     * 3. Si non trouvé, tente une correspondance partielle (suffixe)
     * 4. Si [nextTocLink] est fourni, la plage s'étend de l'index trouvé
     *    jusqu'à (nextTocStart - 1)
     * 5. Si aucun nextTocLink (dernière entrée du TOC), la plage va jusqu'à
     *    la fin du spine
     *
     * @param tocLink L'entrée du TOC à résoudre.
     * @param nextTocLink L'entrée TOC suivante dans l'ordre de parcours,
     *        ou null si c'est la dernière.
     * @return La plage d'indices dans le spine, ou [IntRange.EMPTY] si
     *         le href n'est pas trouvé.
     */
    fun resolveRange(tocLink: Link, nextTocLink: Link? = null): IntRange {
        val href = normalizeHref(tocLink.href.toString())
        val start = lookupIndex(href) ?: return IntRange.EMPTY

        val endExclusive = if (nextTocLink != null) {
            val nextHref = normalizeHref(nextTocLink.href.toString())
            lookupIndex(nextHref) ?: spine.size
        } else {
            spine.size
        }

        return start until endExclusive
    }

    /**
     * Cherche un index dans le map interne, avec fallback sur correspondance partielle.
     */
    private fun lookupIndex(href: String): Int? {
        return index[href]
            ?: index.entries.find { (key, _) ->
                key.endsWith(href) || href.endsWith(key)
            }?.value
    }

    companion object {
        /**
         * Normalise un href pour le lookup dans l'index.
         *
         * Supprime :
         * - Tout ce qui suit un `#` (ancre/fragment)
         * - Le préfixe de chemin (ne garde que le nom du fichier)
         */
        fun normalizeHref(href: String): String {
            return href
                .substringBefore("#")
                .substringAfterLast("/")
                .trim()
        }
    }
}
