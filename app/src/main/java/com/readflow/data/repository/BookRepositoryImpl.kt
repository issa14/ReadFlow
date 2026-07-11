package com.readflow.data.repository

import android.text.Html
import android.content.Context
import android.util.Log
import com.readflow.data.database.BookDao
import com.readflow.data.database.ProgressDao
import com.readflow.data.database.SentenceCacheDao
import com.readflow.data.mapper.toDomain
import com.readflow.data.mapper.toEntity
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Progress
import com.readflow.domain.model.Sentence
import com.readflow.domain.repository.BookRepository
import com.readflow.domain.usecase.ChunkTextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.INFINITE
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.WarningLogger
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val sentenceCacheDao: SentenceCacheDao,
    private val chunkText: ChunkTextUseCase
) : BookRepository {

    private val httpClient = DefaultHttpClient(
        userAgent = "ReadFlow/0.1.0",
        connectTimeout = INFINITE,
        readTimeout = INFINITE
    )
    private val retriever: AssetRetriever get() = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(EpubParser(), emptyList()) {}

    override suspend fun importEpub(
        inputStream: InputStream,
        fileName: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Book =
        withContext(Dispatchers.IO) {
            onProgress(0.05f, "Copie du fichier EPUB...")
            val bookId = UUID.randomUUID().toString()
            val epubDir = File(context.filesDir, "epubs/$bookId")
            epubDir.mkdirs()
            val epubFile = File(epubDir, fileName)
            epubFile.outputStream().use { inputStream.copyTo(it) }

            onProgress(0.12f, "Analyse de la structure de l'EPUB...")
            val publication = openPublication(epubFile)

            onProgress(0.20f, "Lecture des métadonnées...")
            val title = publication.metadata.localizedTitle?.string
                ?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".epub")
            val author = publication.metadata.authors.joinToString(", ") { it.name }
                .takeIf { it.isNotBlank() } ?: "Auteur inconnu"

            // Extraction de la couverture de l'EPUB de manière robuste via l'archive ZIP
            var coverPath: String? = null
            try {
                ZipFile(epubFile).use { zip ->
                    val entry = zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .find { entry ->
                            val name = entry.name.lowercase()
                            (name.contains("cover") || name.contains("couverture") || name.contains("folder")) &&
                                    (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))
                        }
                    if (entry != null) {
                        val coverFile = File(epubDir, "cover.jpg")
                        zip.getInputStream(entry).use { input ->
                            coverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        coverPath = coverFile.absolutePath
                        Log.d("BookRepo", "Couverture extraite avec succès de l'archive ZIP : $coverPath")
                    } else {
                        Log.w("BookRepo", "Aucune couverture trouvée dans le fichier ZIP.")
                    }
                }
            } catch (e: Exception) {
                Log.e("BookRepo", "Erreur lors de l'extraction de la couverture du ZIP", e)
            }

            val totalChapters = publication.readingOrder.size
            val book = Book(
                id = bookId,
                title = title,
                author = author,
                description = publication.metadata.description,
                coverPath = coverPath,
                totalChapters = totalChapters,
                language = publication.metadata.languages.firstOrNull() ?: "fr",
                addedAt = System.currentTimeMillis()
            )
            
            onProgress(0.25f, "Enregistrement du livre...")
            bookDao.insert(book.toEntity(epubFile.absolutePath, coverPath))

            // Pré-chargement et segmentation de tous les chapitres pour un rendu instantané
            val startCacheProgress = 0.25f
            val endCacheProgress = 1.00f
            val range = endCacheProgress - startCacheProgress

            for (i in 0 until totalChapters) {
                val progressFraction = startCacheProgress + range * (i.toFloat() / totalChapters)
                val displayIndex = i + 1
                onProgress(progressFraction, "Optimisation du chapitre $displayIndex sur $totalChapters...")

                try {
                    val link = publication.readingOrder[i]
                    val text = extractHtml(epubFile, link.href.toString())
                    // Segmente et stocke directement en cache Room via ChunkTextUseCase
                    chunkText(bookId, i, text)
                } catch (e: Exception) {
                    Log.e("BookRepo", "Erreur lors de la pré-segmentation du chapitre $i", e)
                }
            }

            onProgress(1.00f, "Livre optimisé et prêt !")
            book
        }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter =
        withContext(Dispatchers.IO) {
            val book = bookDao.getById(bookId)
                ?: throw IllegalStateException("Livre introuvable : $bookId")
            val epubFile = File(book.filePath)
            require(epubFile.exists()) { "Fichier EPUB introuvable" }

            // 1. Vérifier le cache Room avant de ré-extraire l'EPUB
            val cachedSentences = sentenceCacheDao.getSentences(bookId, chapterIndex)
            if (cachedSentences.isNotEmpty()) {
                Log.d("BookRepo", "Cache HIT — bookId=$bookId ch=$chapterIndex (${cachedSentences.size} phrases)")
                val publication = openPublication(epubFile)
                val link = publication.readingOrder.getOrNull(chapterIndex)
                    ?: throw IllegalStateException("Chapitre $chapterIndex introuvable")
                return@withContext Chapter(
                    index = chapterIndex,
                    title = link.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${chapterIndex + 1}",
                    sentences = cachedSentences.map { entity ->
                        Sentence(
                            index = entity.sentenceIndex,
                            text = entity.text,
                            startOffset = entity.startOffset,
                            endOffset = entity.endOffset
                        )
                    }
                )
            }

            // 2. Cache froid : extraction + segmentation classiques
            Log.d("BookRepo", "Cache MISS — segmentation pour bookId=$bookId ch=$chapterIndex")
            val publication = openPublication(epubFile)
            val link = publication.readingOrder.getOrNull(chapterIndex)
                ?: throw IllegalStateException("Chapitre $chapterIndex introuvable")

            val text = extractHtml(epubFile, link.href.toString())
            val sentences = chunkText(bookId, chapterIndex, text)

            Chapter(
                index = chapterIndex,
                title = link.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${chapterIndex + 1}",
                sentences = sentences
            )
        }

    override suspend fun getAllBooks(): List<Book> =
        bookDao.getAll().first().map { it.toDomain() }

    override suspend fun saveProgress(progress: Progress) =
        progressDao.upsert(progress.toEntity())

    override suspend fun getProgress(bookId: String): Progress? =
        progressDao.getByBookId(bookId)?.toDomain()

    // ── Helpers ──────────────────────────────────────────

    private suspend fun openPublication(epubFile: File) =
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
            throw e
        }
        ?: throw IllegalStateException("Impossible d'ouvrir l'EPUB")

    private fun extractHtml(epubFile: File, href: String): String {
        return try {
            ZipFile(epubFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .find { it.name.endsWith(href) || it.name == href }
                if (entry != null) {
                    val html = zip.getInputStream(entry).bufferedReader().readText()
                    Log.d("BookRepo", "Chapter HTML: ${html.length}B, href=$href, entry=${entry.name}")
                    val stripped = stripHtml(html)
                    Log.d("BookRepo", "Stripped: ${stripped.length} chars, preview=\"${stripped.take(100)}...\"")
                    stripped
                } else {
                    Log.w("BookRepo", "Entry not found for href=$href")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e("BookRepo", "Error extracting HTML for href=$href", e)
            ""
        }
    }

    @Suppress("DEPRECATION")
    private fun stripHtml(html: String): String {
        if (html.isEmpty()) return ""

        // 1. Ne garder que le contenu du <body> si présent pour éviter d'extraire le titre du head
        var processedHtml = html
        val bodyMatcher = java.util.regex.Pattern.compile("<body[^>]*>(.*?)</body>", java.util.regex.Pattern.DOTALL or java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html)
        if (bodyMatcher.find()) {
            processedHtml = bodyMatcher.group(1) ?: html
        } else {
            // Fallback : supprimer la balise <head> entière
            processedHtml = processedHtml.replace(Regex("<head[^>]*>.*?</head>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        }

        // Supprimer les balises <style> et <script> qui pourraient se trouver dans le body
        processedHtml = processedHtml.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        processedHtml = processedHtml.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

        // 2. Décode toutes les entités HTML (&#8217; -> ', &rsquo; -> ', etc.)
        // et extrait le texte brut en éliminant les balises structurelles
        val decodedText = Html.fromHtml(processedHtml, Html.FROM_HTML_MODE_LEGACY).toString()

        // 3. Nettoyage de la mise en forme sans détruire les sauts de ligne (\n)
        return decodedText
            .replace(Regex("[\\r\\t]"), "")        // Supprime les retours chariot et tabulations parasites
            .replace(Regex(" {2,}"), " ")          // Fusionne les espaces doubles ou plus en un seul espace
            .replace(Regex("\\n{3,}"), "\n\n")     // Limite les sauts de ligne consécutifs à 2 maximum
            .trim()
    }
}
