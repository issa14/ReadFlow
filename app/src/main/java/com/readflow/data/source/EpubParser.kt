package com.readflow.data.source

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubMeta(
    val title: String,
    val author: String,
    val description: String?,
    val language: String?,
    val chapters: List<ChapterRef>
)

data class ChapterRef(
    val href: String,
    val title: String?
)

object EpubParser {

    fun parseMetadata(epubFile: File): EpubMeta {
        ZipFile(epubFile).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfXml = readEntry(zip, opfPath)
            val opfDir = File(opfPath).parent ?: ""
            val doc = parseXml(opfXml)

            val title = text(doc, "title") ?: epubFile.nameWithoutExtension
            val author = text(doc, "creator") ?: "Auteur inconnu"
            val description = text(doc, "description")
            val language = text(doc, "language")

            val chapters = mutableListOf<ChapterRef>()
            val manifest = mutableMapOf<String, String>()

            doc.getElementsByTagName("item").let { items ->
                for (i in 0 until items.length) {
                    val item = items.item(i) as? Element ?: continue
                    manifest[item.getAttribute("id")] = item.getAttribute("href")
                }
            }

            doc.getElementsByTagName("itemref").let { refs ->
                for (i in 0 until refs.length) {
                    val ref = refs.item(i) as? Element ?: continue
                    val href = manifest[ref.getAttribute("idref")] ?: continue
                    val full = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                    chapters.add(ChapterRef(full, null))
                }
            }

            return EpubMeta(title, author, description, language, chapters)
        }
    }

    fun extractChapterText(epubFile: File, href: String, opfPath: String): String {
        return try {
            ZipFile(epubFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .find { it.name.endsWith(href) || it.name == href }
                if (entry != null) {
                    stripHtml(zip.getInputStream(entry).bufferedReader().readText())
                } else ""
            }
        } catch (e: Exception) { "" }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val container = readEntry(zip, "META-INF/container.xml")
        return Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
            ?: error("OPF introuvable")
    }

    private fun readEntry(zip: ZipFile, path: String): String {
        val entry = zip.entries().asSequence()
            .find { it.name.equals(path, ignoreCase = true) }
            ?: error("Entrée introuvable : $path")
        return zip.getInputStream(entry).bufferedReader().readText()
    }

    private fun parseXml(xml: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    private fun text(doc: org.w3c.dom.Document, tag: String): String? {
        val nodes = doc.getElementsByTagNameNS("*", tag)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<head[^>]*>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;|&amp;|&lt;|&gt;|&quot;|&#?[a-z0-9]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
