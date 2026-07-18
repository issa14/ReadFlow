package com.inktone.data.opds

import com.inktone.domain.model.OpdsEntry
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.model.OpdsLink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parseur OPDS 1.2 (XML/Atom).
 *
 * Extrait les métadonnées du catalogue, les entrées (livres),
 * les liens de navigation, d'acquisition et de pagination.
 *
 * Prêt pour extension OPDS 2.0 (JSON) via une interface commune.
 */
object OpdsParser {

    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val NS_DC = "http://purl.org/dc/terms/"
    private const val NS_DCTERMS = "http://purl.org/dc/terms/"

    /**
     * Parse un flux XML OPDS 1.2 en [OpdsFeed].
     */
    fun parseAtomFeed(xml: String): OpdsFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var title = ""
        var iconUrl: String? = null
        var description: String? = null
        val entries = mutableListOf<OpdsEntry>()
        val navLinks = mutableListOf<OpdsLink>()
        var nextPageUrl: String? = null
        var searchUrl: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "title" -> {
                            if (parser.depth == 2) {
                                title = parser.nextText()
                            }
                        }
                        "icon" -> iconUrl = parser.nextText()
                        "subtitle" -> description = parser.nextText()
                        "entry" -> {
                            entries.add(parseEntry(parser))
                        }
                        "link" -> {
                            val link = parseLink(parser)
                            when {
                                link.rel.contains("next") -> nextPageUrl = link.href
                                link.rel.contains("search") -> searchUrl = link.href
                                link.type?.contains("opds") == true ||
                                link.rel.contains("subsection") ||
                                link.rel.contains("navigation") -> navLinks.add(link)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        return OpdsFeed(
            title = title,
            iconUrl = iconUrl,
            description = description,
            entries = entries,
            navigationLinks = navLinks,
            nextPageUrl = nextPageUrl,
            searchUrl = searchUrl
        )
    }

    private fun parseEntry(parser: XmlPullParser): OpdsEntry {
        var id = ""
        var title = ""
        var author = ""
        var summary: String? = null
        var coverUrl: String? = null
        var thumbnailUrl: String? = null
        var epubUrl: String? = null
        var pdfUrl: String? = null
        var publishedDate: String? = null
        var publisher: String? = null
        val links = mutableListOf<OpdsLink>()

        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "id" -> id = parser.nextText()
                    "title" -> title = parser.nextText()
                    "name" -> if (author.isEmpty()) author = parser.nextText()
                    "summary", "content" -> summary = parser.nextText()
                    "published", "issued" -> publishedDate = parser.nextText()
                    "publisher" -> publisher = parser.nextText()
                    "link" -> {
                        val link = parseLink(parser)
                        links.add(link)
                        when {
                            link.rel.contains("image") || link.rel.contains("cover") -> {
                                if (link.type?.contains("thumbnail") == true) thumbnailUrl = link.href
                                else coverUrl = link.href
                            }
                            link.type?.contains("epub") == true -> epubUrl = link.href
                            link.type?.contains("pdf") == true -> pdfUrl = link.href
                            link.rel.contains("acquisition") && link.type?.contains("epub") == true -> epubUrl = link.href
                            link.rel.contains("acquisition") && link.type?.contains("pdf") == true -> pdfUrl = link.href
                            link.rel.contains("thumbnail") -> thumbnailUrl = link.href
                            link.rel.contains("http://opds-spec.org/image") -> coverUrl = link.href
                            link.rel.contains("http://opds-spec.org/image/thumbnail") -> thumbnailUrl = link.href
                        }
                    }
                    "author" -> {
                        author = parseAuthor(parser)
                    }
                }
            }
            event = parser.next()
        }
        // Nettoyer le HTML éventuel dans le titre/summary
        title = title.replace(Regex("<[^>]*>"), "").trim()
        summary = summary?.replace(Regex("<[^>]*>"), "")?.trim()

        return OpdsEntry(
            id = id,
            title = title,
            author = author,
            summary = summary,
            coverUrl = coverUrl,
            thumbnailUrl = thumbnailUrl,
            epubDownloadUrl = epubUrl,
            pdfDownloadUrl = pdfUrl,
            links = links,
            publishedDate = publishedDate,
            publisher = publisher
        )
    }

    private fun parseAuthor(parser: XmlPullParser): String {
        val depth = parser.depth
        var event = parser.next()
        var name = ""
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (event == XmlPullParser.START_TAG && parser.name == "name") {
                name = parser.nextText()
            }
            event = parser.next()
        }
        return name
    }

    private fun parseLink(parser: XmlPullParser): OpdsLink {
        val href = parser.getAttributeValue(null, "href") ?: ""
        val rel = parser.getAttributeValue(null, "rel") ?: ""
        val title = parser.getAttributeValue(null, "title")
        val type = parser.getAttributeValue(null, "type")
        return OpdsLink(href = href, rel = rel, title = title, type = type)
    }
}
