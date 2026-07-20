package com.inktone.data.repository

import android.text.Html
import android.content.Context
import android.util.Log
import com.inktone.CrashReporter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inktone.data.database.BookDao
import com.inktone.data.database.ReadingProgressDao
import com.inktone.data.database.RichBlockCacheDao
import com.inktone.data.database.SearchDao
import com.inktone.data.database.SentenceCacheDao
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.data.database.entity.SentenceFts
import com.inktone.data.epub.SpineIndex
import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.RichBlock
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TextSpan
import com.inktone.domain.model.TocEntry
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.usecase.ChunkTextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.INFINITE
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.WarningLogger
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Index construit une seule fois par ouverture d'archive EPUB, pour éviter de rouvrir le ZIP
 * (relecture du répertoire central) et de rescanner linéairement ses entrées à chaque extraction
 * (HTML de chapitre, image, couverture, série calibre) — potentiellement 70-100+ fois par import
 * avant ce changement. Voir PLAN_ACTION_TOP_TIER_CLAUDECODE.md §2.1.
 */
private class EpubZipIndex(private val zip: ZipFile) : Closeable {
    private val entries: List<ZipEntry> = zip.entries().toList()
    private val byName: Map<String, ZipEntry> = entries.associateBy { it.name }

    /** Entrée par chemin exact (O(1)), puis par correspondance de suffixe pour les chemins relatifs/préfixés différemment. */
    fun find(path: String): ZipEntry? = byName[path] ?: entries.firstOrNull { it.name.endsWith(path) }

    /** Parcours complet, pour les recherches heuristiques (ex. couverture par motif de nom). */
    fun entriesSequence(): Sequence<ZipEntry> = entries.asSequence()

    fun inputStream(entry: ZipEntry) = zip.getInputStream(entry)

    override fun close() = zip.close()
}

private fun openZipIndex(epubFile: File): EpubZipIndex = EpubZipIndex(ZipFile(epubFile))

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val sentenceCacheDao: SentenceCacheDao,
    private val richBlockCacheDao: RichBlockCacheDao,
    private val searchDao: SearchDao,
    private val chunkText: ChunkTextUseCase
) : BookRepository {

    companion object {
        // Patterns HTML compilés une seule fois
        private val BODY_PATTERN = java.util.regex.Pattern.compile(
            "<body[^>]*>(.*?)</body>",
            java.util.regex.Pattern.DOTALL or java.util.regex.Pattern.CASE_INSENSITIVE
        )
        private val STYLE_PATTERN = Regex("<style[^>]*>.*?</style>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val SCRIPT_PATTERN = Regex("<script[^>]*>.*?</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val HEAD_PATTERN = Regex("<head[^>]*>.*?</head>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val CR_TAB_PATTERN = Regex("[\\r\\t]")
        private val DOUBLE_SPACES_PATTERN = Regex(" {2,}")
        private val TRIPLE_NEWLINE_PATTERN = Regex("\\n{3,}")
    }

    private val httpClient = DefaultHttpClient(
        userAgent = "InkTone/0.1.0",
        connectTimeout = INFINITE,
        readTimeout = INFINITE
    )
    private val retriever: AssetRetriever get() = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(EpubParser(), emptyList()) {}
    private val gson = Gson()

    override suspend fun importEpub(
        inputStream: InputStream,
        fileName: String,
        sourceFolder: String?,
        onProgress: (progress: Float, status: String) -> Unit
    ): Book =
        withContext(Dispatchers.IO) {
            onProgress(0.05f, "Copie du fichier EPUB...")
            val bookId = UUID.randomUUID().toString()
            val epubDir = File(context.filesDir, "epubs/$bookId")
            epubDir.mkdirs()
            val imagesDir = File(epubDir, "images").apply { mkdirs() }
            val epubFile = File(epubDir, fileName)
            epubFile.outputStream().use { inputStream.copyTo(it) }

            onProgress(0.12f, "Analyse de la structure de l'EPUB...")
            val publication = try {
                openPublication(epubFile)
            } catch (e: Exception) {
                // Nettoyer les fichiers partiellement copiés
                epubDir.deleteRecursively()
                throw IllegalStateException(
                    "Fichier EPUB corrompu ou illisible : ${e.message?.take(100) ?: "erreur inconnue"}"
                )
            }

            onProgress(0.20f, "Lecture des métadonnées...")
            val title = publication.metadata.localizedTitle?.string
                ?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".epub")
            val author = publication.metadata.authors.joinToString(", ") { it.name }
                .takeIf { it.isNotBlank() } ?: "Auteur inconnu"

            val publisher = try {
                publication.metadata.publishers.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w("BookRepo", "Lecture publisher échouée : ${e.message}")
                null
            }
            val publishedDate = try {
                publication.metadata.published?.let {
                    java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()).toString().take(10)
                }
            } catch (e: Exception) {
                Log.w("BookRepo", "Lecture date de publication échouée : ${e.message}")
                null
            }
            val subjects = try {
                publication.metadata.subjects.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
            } catch (e: Exception) {
                Log.w("BookRepo", "Lecture subjects échouée : ${e.message}")
                emptyList()
            }
            val isbn = try {
                publication.metadata.identifier
                    ?.takeIf { it.startsWith("urn:isbn:") }
                    ?.removePrefix("urn:isbn:")
            } catch (e: Exception) {
                Log.w("BookRepo", "Lecture ISBN échouée : ${e.message}")
                null
            }
            val zipIndex = openZipIndex(epubFile)
            try {
                val seriesInfo = try {
                    publication.metadata.belongsToSeries.firstOrNull()
                        ?.let { it.name.takeIf { n -> n.isNotBlank() } to it.position?.toFloat() }
                        ?.takeIf { it.first != null }
                } catch (e: Exception) {
                    Log.w("BookRepo", "Lecture série (Readium) échouée : ${e.message}")
                    null
                } ?: try {
                    extractCalibreSeriesFallback(zipIndex)
                } catch (e: Exception) {
                    Log.w("BookRepo", "Lecture série (calibre:series) échouée : ${e.message}")
                    null
                }

                // Extraction de la couverture — Readium metadata d'abord (EPUB3), fallback heuristique ZIP (EPUB2)
                var coverPath: String? = extractCoverViaReadium(publication, epubDir)
                if (coverPath == null) {
                    coverPath = extractCoverHeuristic(zipIndex, epubDir)
                }

                // ── Construire l'index spine ──
                val spineIndex = SpineIndex(publication.readingOrder)

                // ── Aplatir le TOC en liste ordonnée avec profondeur (skip les entrées sans href) ──
                val flatToc = mutableListOf<Pair<Link, Int>>()
                fun flatten(links: List<Link>, level: Int) {
                    for (link in links) {
                        if (link.href.toString().isNotBlank()) flatToc.add(link to level)
                        flatten(link.children, level + 1)
                    }
                }
                flatten(publication.tableOfContents, 0)

                val totalChapters = flatToc.size
                Log.d("BookRepo", "TOC: ${publication.tableOfContents.size} racines → $totalChapters entrées, spineIndex.size=${spineIndex.size}")

                val tocEntries = mutableListOf<TocEntry>()

                val book = Book(
                    id = bookId,
                    title = title,
                    author = author,
                    description = publication.metadata.description,
                    coverPath = coverPath,
                    totalChapters = totalChapters,
                    language = publication.metadata.languages.firstOrNull() ?: "fr",
                    addedAt = System.currentTimeMillis(),
                    publisher = publisher,
                    publishedDate = publishedDate,
                    subjects = subjects,
                    isbn = isbn,
                    seriesName = seriesInfo?.first,
                    seriesIndex = seriesInfo?.second,
                    sourceFolder = sourceFolder
                )

                onProgress(0.25f, "Enregistrement du livre...")
                bookDao.insert(book.toEntity(epubFile.absolutePath, coverPath))

                // ── Import basé TOC (les chapitres = entrées du TOC, pas du spine) ──
                val startCacheProgress = 0.25f
                val endCacheProgress = 1.00f
                val progressRange = endCacheProgress - startCacheProgress

                for (i in 0 until totalChapters) {
                    val progressFraction = startCacheProgress + progressRange * (i.toFloat() / totalChapters)
                    val (tocLink, tocLevel) = flatToc[i]
                    val chapterTitle = tocLink.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${i + 1}"
                    tocEntries.add(TocEntry(index = i, title = chapterTitle, level = tocLevel))
                    onProgress(progressFraction, "Optimisation : $chapterTitle...")

                    try {
                        val nextTocLink = flatToc.getOrNull(i + 1)?.first
                        val anchoredRange = spineIndex.resolveAnchoredRange(tocLink, nextTocLink)
                        val spineRange = anchoredRange.spineRange

                        if (spineRange.isEmpty()) {
                            Log.w("BookRepo", "TOC '$chapterTitle' (${tocLink.href}) non trouvé dans le spine — ignoré")
                            sentenceCacheDao.updateChapterTitle(bookId, i, "$chapterTitle (non trouvé)")
                            continue
                        }

                        // Concaténer le texte + les RichBlocks de tous les fichiers spine de la plage
                        val richBlocks = mutableListOf<RichBlock>()
                        val combinedHtml = buildString {
                            for (spineIdx in spineRange) {
                                val link = publication.readingOrder[spineIdx]
                                val spineHref = link.href.toString()
                                var raw = extractRawHtml(zipIndex, spineHref)
                                if (raw.isBlank()) continue

                                val isFirst = spineIdx == spineRange.first
                                val isLast = spineIdx == spineRange.last
                                val startAnchor = if (isFirst) anchoredRange.startAnchor else null
                                val endAnchor = if (isLast) anchoredRange.endAnchor else null
                                if (startAnchor != null || endAnchor != null) {
                                    raw = sliceByAnchors(raw, startAnchor, endAnchor)
                                }

                                append(stripHtml(raw))
                                append("\n\n")
                                richBlocks.addAll(
                                    extractRichBlocks(raw, zipIndex, spineHref, imagesDir, richBlocks.size)
                                )
                            }
                        }

                        val sentences = chunkText(bookId, i, combinedHtml)
                        sentenceCacheDao.updateChapterTitle(bookId, i, chapterTitle)

                        // Longueur du chapitre en caractères, pour la pondération de la progression
                        // (voir architecture.md §11.3) — calculée gratuitement, combinedHtml est déjà
                        // en mémoire à cet endroit, aucun reparsing.
                        tocEntries[i] = tocEntries[i].copy(charCount = combinedHtml.length)

                        val richEntities = richBlocks.map { it.toEntity(bookId, i) }
                        if (richEntities.isNotEmpty()) richBlockCacheDao.insertAll(richEntities)

                        if (sentences.isNotEmpty()) {
                            val ftsEntries = sentences.map { sentence ->
                                SentenceFts(
                                    bookId = bookId,
                                    chapterIndex = i,
                                    sentenceIndex = sentence.index,
                                    text = sentence.text
                                )
                            }
                            searchDao.insertAll(ftsEntries)
                        }

                        Log.d("BookRepo", "Chapitre TOC $i '$chapterTitle' : spine ${spineRange.first}..${spineRange.last} (${spineRange.count()} fichiers, ${richBlocks.size} blocs riches)")
                    } catch (e: Exception) {
                        Log.e("BookRepo", "Erreur import chapitre TOC $i '$chapterTitle' : ${e.message}", e)
                        CrashReporter.recordException(e)
                    }
                }

                // Persister les vrais titres TOC maintenant qu'on les a tous collectés
                if (tocEntries.isNotEmpty()) {
                    bookDao.insert(book.copy(tocEntries = tocEntries).toEntity(epubFile.absolutePath, coverPath))
                }

                onProgress(1.00f, "Livre optimisé et prêt !")
                book.copy(tocEntries = tocEntries)
            } finally {
                zipIndex.close()
            }
        }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter =
        withContext(Dispatchers.IO) {
            val book = bookDao.getById(bookId)
                ?: throw IllegalStateException("Livre introuvable : $bookId")
            val epubFile = File(book.filePath)
            require(epubFile.exists()) { "Fichier EPUB introuvable" }
            val epubDir = epubFile.parentFile ?: epubFile

            // 1. Vérifier le cache Room avant de ré-extraire l'EPUB
            val cachedSentences = sentenceCacheDao.getSentences(bookId, chapterIndex)
            if (cachedSentences.isNotEmpty()) {
                Log.d("BookRepo", "Cache HIT — bookId=$bookId ch=$chapterIndex (${cachedSentences.size} phrases)")
                // Titre du chapitre stocké dans le cache — pas besoin de rouvrir l'EPUB
                val chapterTitle = cachedSentences.first().chapterTitle
                    .takeIf { it.isNotBlank() } ?: "Chapitre ${chapterIndex + 1}"
                val cachedRichBlocks = richBlockCacheDao.getBlocks(bookId, chapterIndex).map { it.toDomain() }
                return@withContext Chapter(
                    index = chapterIndex,
                    title = chapterTitle,
                    sentences = cachedSentences.map { entity ->
                        Sentence(
                            index = entity.sentenceIndex,
                            text = entity.text,
                            startOffset = entity.startOffset,
                            endOffset = entity.endOffset
                        )
                    },
                    richBlocks = cachedRichBlocks,
                    epubDir = epubDir.absolutePath
                )
            }

            // 2. Cache froid : extraction + segmentation classiques
            Log.d("BookRepo", "Cache MISS — segmentation pour bookId=$bookId ch=$chapterIndex")
            val publication = try {
                openPublication(epubFile)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Impossible d'ouvrir le chapitre ${chapterIndex + 1} : fichier EPUB endommagé"
                )
            }
            val link = publication.readingOrder.getOrNull(chapterIndex)
                ?: throw IllegalStateException("Chapitre $chapterIndex introuvable")

            val spineHref = link.href.toString()
            val (raw, richBlocks) = openZipIndex(epubFile).use { zipIndex ->
                val rawHtml = extractRawHtml(zipIndex, spineHref)
                val imagesDir = File(epubDir, "images").apply { mkdirs() }
                rawHtml to extractRichBlocks(rawHtml, zipIndex, spineHref, imagesDir, 0)
            }
            val text = stripHtml(raw)
            val sentences = chunkText(bookId, chapterIndex, text)
            val richEntities = richBlocks.map { it.toEntity(bookId, chapterIndex) }
            if (richEntities.isNotEmpty()) richBlockCacheDao.insertAll(richEntities)

            Chapter(
                index = chapterIndex,
                title = link.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${chapterIndex + 1}",
                sentences = sentences,
                richBlocks = richBlocks,
                epubDir = epubDir.absolutePath
            )
        }

    override suspend fun getAllBooks(): List<Book> =
        bookDao.getAll().first().map { it.toDomain() }

    override suspend fun saveProgress(progress: ReadingProgress) =
        readingProgressDao.saveProgress(progress)

    override suspend fun getProgress(bookId: String): ReadingProgress? =
        readingProgressDao.getProgressForBook(bookId)

    override suspend fun regenerateCover(bookId: String): String? =
        withContext(Dispatchers.IO) {
            val entity = bookDao.getById(bookId) ?: return@withContext null
            val epubFile = File(entity.filePath)
            if (!epubFile.exists()) return@withContext null
            val epubDir = epubFile.parentFile ?: return@withContext null

            entity.coverPath?.let { File(it).delete() }

            val freshFileName = "cover_${System.currentTimeMillis()}.jpg"
            val newPath = try {
                val publication = openPublication(epubFile)
                extractCoverViaReadium(publication, epubDir, freshFileName)
            } catch (e: Exception) {
                Log.w("BookRepo", "regenerateCover: ouverture Readium échouée pour $bookId : ${e.message}")
                null
            } ?: openZipIndex(epubFile).use { zipIndex -> extractCoverHeuristic(zipIndex, epubDir, freshFileName) }

            bookDao.updateCoverPath(bookId, newPath)
            newPath
        }

    override suspend fun clearAllCovers(): Unit =
        withContext(Dispatchers.IO) {
            bookDao.getAll().first().forEach { entity ->
                entity.coverPath?.let { File(it).delete() }
            }
            bookDao.clearAllCoverPaths()
        }

    override suspend fun setFavorite(bookId: String, isFavorite: Boolean) =
        bookDao.setFavorite(bookId, isFavorite)

    override suspend fun getAllTags(): List<String> =
        withContext(Dispatchers.IO) {
            val listType = object : TypeToken<List<String>>() {}.type
            bookDao.getAllSubjectsRaw()
                .flatMap { raw ->
                    try {
                        gson.fromJson<List<String>>(raw, listType) ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() }
        }

    // ── Helpers — ouverture & extraction EPUB ──────────────

    private suspend fun openPublication(epubFile: File): Publication =
        try {
            Log.d("BookRepo", "Opening EPUB: ${epubFile.absolutePath} (exists=${epubFile.exists()}, size=${epubFile.length()})")
            val asset = retriever
                .retrieve(epubFile, FormatHints())
                .getOrNull()
            Log.d("BookRepo", "Asset retrieved: ${asset != null}")
            if (asset != null) {
                val pub = opener.open(
                    asset,
                    "",
                    false,
                    {},
                    object : WarningLogger {
                        override fun log(warning: org.readium.r2.shared.util.logging.Warning) {
                            Log.w("BookRepo", "Readium: $warning")
                        }
                    }
                ).getOrNull()
                Log.d("BookRepo", "Publication opened: ${pub != null}")
                pub
            } else null
        } catch (e: Exception) {
            Log.e("BookRepo", "Error opening EPUB", e)
            CrashReporter.recordException(e)
            throw e
        }
        ?: throw IllegalStateException("Impossible d'ouvrir l'EPUB")

    /** Lit le contenu XHTML brut (non nettoyé) d'une entrée du ZIP EPUB. */
    private fun extractRawHtml(zipIndex: EpubZipIndex, href: String): String {
        return try {
            val entry = zipIndex.find(href)
            if (entry != null) {
                val html = zipIndex.inputStream(entry).bufferedReader().readText()
                Log.d("BookRepo", "Chapter HTML: ${html.length}B, href=$href, entry=${entry.name}")
                html
            } else {
                Log.w("BookRepo", "Entry not found for href=$href")
                ""
            }
        } catch (e: Exception) {
            Log.e("BookRepo", "Error extracting HTML for href=$href", e)
            CrashReporter.recordException(e)
            ""
        }
    }

    @Suppress("DEPRECATION")
    private fun stripHtml(html: String): String {
        if (html.isEmpty()) return ""

        var processedHtml = html
        val bodyMatcher = BODY_PATTERN.matcher(html)
        if (bodyMatcher.find()) {
            processedHtml = bodyMatcher.group(1) ?: html
        } else {
            processedHtml = processedHtml.replace(HEAD_PATTERN, "")
        }

        processedHtml = processedHtml.replace(STYLE_PATTERN, "")
        processedHtml = processedHtml.replace(SCRIPT_PATTERN, "")

        val decodedText = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_LEGACY).toString()

        val stripped = decodedText
            .replace(CR_TAB_PATTERN, "")
            .replace(DOUBLE_SPACES_PATTERN, " ")
            .replace(TRIPLE_NEWLINE_PATTERN, "\n\n")
            .trim()
        Log.d("BookRepo", "Stripped: ${stripped.length} chars, preview=\"${stripped.take(100)}...\"")
        return stripped
    }

    // ── Helpers — couverture ────────────────────────────────

    /** EPUB3 : couverture via l'API Readium `Publication.cover()` (résout le lien cover + décode le bitmap). */
    private suspend fun extractCoverViaReadium(publication: Publication, epubDir: File, fileName: String = "cover.jpg"): String? {
        val bitmap = try {
            publication.cover()
        } catch (e: Exception) {
            Log.w("BookRepo", "Lecture cover() Readium échouée : ${e.message}")
            null
        } ?: return null

        val coverFile = File(epubDir, fileName)
        return try {
            coverFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d("BookRepo", "Couverture extraite via Readium cover() : ${coverFile.absolutePath}")
            coverFile.absolutePath
        } catch (e: Exception) {
            Log.w("BookRepo", "Écriture cover() Readium échouée : ${e.message}")
            null
        }
    }

    /** EPUB2 : fallback heuristique sur les noms de fichiers de l'archive ZIP. */
    private fun extractCoverHeuristic(zipIndex: EpubZipIndex, epubDir: File, fileName: String = "cover.jpg"): String? {
        return try {
            val entry = zipIndex.entriesSequence()
                .filter { !it.isDirectory }
                .find { entry ->
                    val name = entry.name.lowercase()
                    (name.contains("cover") || name.contains("couverture") || name.contains("folder")) &&
                            (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                }
            if (entry != null) {
                val coverFile = File(epubDir, fileName)
                zipIndex.inputStream(entry).use { input ->
                    coverFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d("BookRepo", "Couverture extraite avec succès de l'archive ZIP (heuristique) : ${coverFile.absolutePath}")
                coverFile.absolutePath
            } else {
                Log.w("BookRepo", "Aucune couverture trouvée dans le fichier ZIP.")
                null
            }
        } catch (e: Exception) {
            Log.e("BookRepo", "Erreur lors de l'extraction de la couverture du ZIP", e)
            CrashReporter.recordException(e)
            null
        }
    }

    /**
     * EPUB2 : fallback sur `<meta name="calibre:series" content="..."/>` dans le fichier OPF,
     * pour les livres qui n'exposent pas de collection EPUB3 (`belongsTo`) reconnue par Readium.
     */
    private fun extractCalibreSeriesFallback(zipIndex: EpubZipIndex): Pair<String, Float?>? {
        return try {
            val containerEntry = zipIndex.find("META-INF/container.xml") ?: return null
            val containerXml = zipIndex.inputStream(containerEntry).bufferedReader().readText()
            val containerDoc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
            val opfPath = containerDoc.select("rootfile").firstOrNull()?.attr("full-path")
                ?.takeIf { it.isNotBlank() } ?: return null

            val opfEntry = zipIndex.find(opfPath) ?: return null
            val opfXml = zipIndex.inputStream(opfEntry).bufferedReader().readText()
            val opfDoc = Jsoup.parse(opfXml, "", org.jsoup.parser.Parser.xmlParser())

            val seriesName = opfDoc.select("meta[name=calibre:series]").firstOrNull()
                ?.attr("content")?.takeIf { it.isNotBlank() } ?: return null
            val seriesIndex = opfDoc.select("meta[name=calibre:series_index]").firstOrNull()
                ?.attr("content")?.toFloatOrNull()

            seriesName to seriesIndex
        } catch (e: Exception) {
            Log.w("BookRepo", "Erreur lecture calibre:series dans l'OPF", e)
            null
        }
    }

    // ── Helpers — ancres HTML (chapitres logiques partageant un fichier spine) ──

    /**
     * Découpe [html] pour ne garder que les enfants directs du `<body>` situés entre
     * [startAnchor] et [endAnchor] (identifiants d'ancre `#foo`). Si une ancre n'est pas
     * trouvée, ou si aucune des deux n'est fournie, retourne [html] tel quel.
     */
    private fun sliceByAnchors(html: String, startAnchor: String?, endAnchor: String?): String {
        if (startAnchor == null && endAnchor == null) return html
        val doc = try { Jsoup.parse(html) } catch (e: Exception) { return html }
        val body = doc.body() ?: return html
        val children = body.children()
        if (children.isEmpty()) return html

        fun indexOfId(id: String) = children.indexOfFirst { hasIdRecursive(it, id) }

        val startIdx = startAnchor?.let { id -> indexOfId(id).takeIf { it >= 0 } } ?: 0
        val endIdxExclusive = endAnchor?.let { id -> indexOfId(id).takeIf { it >= 0 } } ?: children.size

        if (startIdx >= endIdxExclusive) return html

        val newBody = Element("body")
        for (idx in startIdx until endIdxExclusive) {
            newBody.appendChild(children[idx].clone())
        }
        return newBody.outerHtml()
    }

    private fun hasIdRecursive(el: Element, id: String): Boolean {
        if (el.id() == id) return true
        return el.select("[id=$id]").isNotEmpty()
    }

    // ── Helpers — RichBlock (structure sémantique HTML) ─────

    /**
     * Extrait les [RichBlock] d'un fragment HTML de chapitre, en préservant la structure
     * sémantique (titres, citations, poèmes, images) que [stripHtml] aplatit en texte brut.
     *
     * @param startIndex Index de départ pour la numérotation des blocs — permet de
     *        concaténer les blocs de plusieurs fichiers spine dans un seul chapitre logique.
     */
    private fun extractRichBlocks(
        html: String,
        zipIndex: EpubZipIndex,
        spineHref: String,
        imagesDir: File,
        startIndex: Int
    ): List<RichBlock> {
        if (html.isBlank()) return emptyList()
        val doc = try { Jsoup.parse(html) } catch (e: Exception) { return emptyList() }
        val body = doc.body() ?: return emptyList()

        val blocks = mutableListOf<RichBlock>()
        var blockIndex = startIndex

        for (child in body.children()) {
            val block = processElement(child, blockIndex, zipIndex, spineHref, imagesDir) ?: continue
            blocks.add(block)
            blockIndex++
        }

        return blocks
    }

    private fun processElement(
        el: Element,
        index: Int,
        zipIndex: EpubZipIndex,
        spineHref: String,
        imagesDir: File
    ): RichBlock? {
        val tagName = el.tagName().lowercase()
        val epubType = el.attr("epub:type").lowercase()
        val cssClass = el.className().lowercase()

        // Ignorer les éléments de navigation
        if (epubType.contains("toc") || epubType.contains("landmarks")) return null

        // Images
        if (tagName == "img" || tagName == "image") {
            val src = el.attr("src").ifBlank { el.attr("xlink:href") }
            return resolveImageBlock(src, el.attr("alt"), index, zipIndex, spineHref, imagesDir)
        }

        // Figure avec image
        if (tagName == "figure") {
            val img = el.selectFirst("img") ?: el.selectFirst("image")
            if (img != null) {
                val src = img.attr("src").ifBlank { img.attr("xlink:href") }
                return resolveImageBlock(src, img.attr("alt"), index, zipIndex, spineHref, imagesDir)
            }
        }

        // Séparateurs de section
        if (tagName == "hr" || cssClass.contains("separator") || cssClass.contains("ornament")) {
            return RichBlock.SectionBreak(index)
        }

        // Titres
        if (tagName.length == 2 && tagName[0] == 'h' && tagName[1] in '1'..'4') {
            val level = tagName[1].digitToInt()
            val spans = extractSpans(el)
            return if (spans.isNotEmpty()) RichBlock.Heading(index, level, spans) else null
        }

        // Blockquote
        if (tagName == "blockquote") {
            val spans = extractSpans(el)
            return if (spans.isNotEmpty()) RichBlock.BlockQuote(index, spans) else null
        }

        // Notes de bas de page / fin — déjà référencées inline via TextSpan.noteRef, pas dupliquées ici
        if (epubType.contains("footnote") || epubType.contains("endnote") ||
            cssClass.contains("footnote") || cssClass.contains("endnote")) {
            return null
        }

        // Poèmes / vers
        if (cssClass.contains("verse") || cssClass.contains("stanza") ||
            cssClass.contains("poem") || cssClass.contains("poeme") ||
            epubType.contains("z3998:poem") || epubType.contains("z3998:verse")) {
            val spans = extractSpans(el)
            return if (spans.isNotEmpty()) RichBlock.PoemLine(index, spans) else null
        }

        // Tout le reste — paragraphe
        val spans = extractSpans(el)
        return if (spans.isNotEmpty()) RichBlock.Paragraph(index, spans) else null
    }

    private fun resolveImageBlock(
        src: String,
        alt: String,
        index: Int,
        zipIndex: EpubZipIndex,
        spineHref: String,
        imagesDir: File
    ): RichBlock.EpubImage? {
        if (src.isBlank() || src.startsWith("data:")) return null
        val resolved = extractAndSaveImage(zipIndex, spineHref, src, imagesDir) ?: return null
        return RichBlock.EpubImage(index, resolved, alt)
    }

    private fun extractSpans(el: Element): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        extractSpansRecursive(el, bold = false, italic = false, superscript = false, spans)
        return spans
    }

    private fun extractSpansRecursive(
        node: Node,
        bold: Boolean,
        italic: Boolean,
        superscript: Boolean,
        result: MutableList<TextSpan>
    ) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    result.add(TextSpan(text, bold, italic, superscript))
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                val cls = node.className().lowercase()
                val newBold = bold || tag == "strong" || tag == "b" || cls.contains("bold")
                val newItalic = italic || tag == "em" || tag == "i" ||
                    cls.contains("italic") || cls.contains("italique")
                val newSup = superscript || tag == "sup"

                // Détecter les références footnote (a href="#noteX")
                if (tag == "a") {
                    val href = node.attr("href")
                    if (href.startsWith("#")) {
                        val noteRef = href.removePrefix("#")
                        val text = node.text()
                        if (text.isNotBlank()) {
                            result.add(TextSpan(text, newBold, newItalic, newSup, noteRef))
                            return
                        }
                    }
                }

                for (child in node.childNodes()) {
                    extractSpansRecursive(child, newBold, newItalic, newSup, result)
                }
            }
        }
    }

    /**
     * Résout un href d'image relatif au fichier spine courant, extrait l'entrée
     * correspondante de l'archive ZIP EPUB, et la sauvegarde sur disque dans [imagesDir]
     * (les images vivent dans le ZIP, pas sur le système de fichiers — Coil a besoin
     * d'un chemin de fichier réel).
     */
    private fun extractAndSaveImage(
        zipIndex: EpubZipIndex,
        spineHref: String,
        imgHref: String,
        imagesDir: File
    ): String? {
        return try {
            val resolvedPath = resolveRelativeHref(spineHref, imgHref)
            val entry = zipIndex.find(resolvedPath)
                ?: zipIndex.find(imgHref.substringAfterLast("/"))
            if (entry == null) {
                Log.w("BookRepo", "Image introuvable : $imgHref (résolu : $resolvedPath)")
                return null
            }
            val extension = entry.name.substringAfterLast(".", "img").lowercase().take(4)
            val fileName = "img_${entry.name.hashCode().toString(16).removePrefix("-")}.$extension"
            val outFile = File(imagesDir, fileName)
            if (!outFile.exists()) {
                zipIndex.inputStream(entry).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e("BookRepo", "Erreur extraction image $imgHref", e)
            CrashReporter.recordException(e)
            null
        }
    }

    /** Résout [relativeHref] par rapport au dossier de [basePath] dans l'archive EPUB, en normalisant `.` et `..`. */
    private fun resolveRelativeHref(basePath: String, relativeHref: String): String {
        val cleanRelative = relativeHref.substringBefore("#")
        if (cleanRelative.startsWith("/")) return cleanRelative.removePrefix("/")

        val baseDir = basePath.substringBeforeLast("/", "")
        val combined = if (baseDir.isBlank()) cleanRelative else "$baseDir/$cleanRelative"

        val stack = mutableListOf<String>()
        for (part in combined.split("/")) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
