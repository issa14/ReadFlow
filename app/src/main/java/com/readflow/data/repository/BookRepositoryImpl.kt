package com.readflow.data.repository

import android.content.Context
import android.util.Log
import com.readflow.data.database.BookDao
import com.readflow.data.database.ProgressDao
import com.readflow.data.mapper.toDomain
import com.readflow.data.mapper.toEntity
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Progress
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
    private val chunkText: ChunkTextUseCase
) : BookRepository {

    private val httpClient = DefaultHttpClient(
        userAgent = "ReadFlow/0.1.0",
        connectTimeout = INFINITE,
        readTimeout = INFINITE
    )
    private val retriever: AssetRetriever get() = AssetRetriever(context.contentResolver, httpClient)
    private val opener = PublicationOpener(EpubParser(), emptyList()) {}

    override suspend fun importEpub(inputStream: InputStream, fileName: String): Book =
        withContext(Dispatchers.IO) {
            val bookId = UUID.randomUUID().toString()
            val epubDir = File(context.filesDir, "epubs/$bookId")
            epubDir.mkdirs()
            val epubFile = File(epubDir, fileName)
            epubFile.outputStream().use { inputStream.copyTo(it) }

            val publication = openPublication(epubFile)

            val title = publication.metadata.localizedTitle.toString()
                .takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".epub")
            val author = publication.metadata.authors.joinToString(", ") { it.name.toString() }
                .takeIf { it.isNotBlank() } ?: "Auteur inconnu"

            val book = Book(
                id = bookId,
                title = title,
                author = author,
                description = publication.metadata.description,
                totalChapters = publication.readingOrder.size,
                language = publication.metadata.languages.firstOrNull() ?: "fr",
                addedAt = System.currentTimeMillis()
            )
            bookDao.insert(book.toEntity(epubFile.absolutePath))
            book
        }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter =
        withContext(Dispatchers.IO) {
            val book = bookDao.getById(bookId)
                ?: throw IllegalStateException("Livre introuvable : $bookId")
            val epubFile = File(book.filePath)
            require(epubFile.exists()) { "Fichier EPUB introuvable" }

            val publication = openPublication(epubFile)
            val link = publication.readingOrder.getOrNull(chapterIndex)
                ?: throw IllegalStateException("Chapitre $chapterIndex introuvable")

            val text = extractHtml(epubFile, link.href.toString())
            val sentences = chunkText(text)

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
                    stripHtml(html)
                } else ""
            }
        } catch (e: Exception) { "" }
    }

    private fun stripHtml(html: String) = html
        .replace(Regex("<head[^>]*>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&nbsp;|&amp;|&lt;|&gt;|&quot;|&#?[a-z0-9]+;"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
