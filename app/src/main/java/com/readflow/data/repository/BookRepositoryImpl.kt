package com.readflow.data.repository

import android.content.Context
import com.readflow.data.database.BookDao
import com.readflow.data.database.ProgressDao
import com.readflow.data.mapper.toDomain
import com.readflow.data.mapper.toEntity
import com.readflow.data.source.EpubParser
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Progress
import com.readflow.domain.repository.BookRepository
import com.readflow.domain.usecase.ChunkTextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    override suspend fun importEpub(inputStream: InputStream, fileName: String): Book {
        val bookId = UUID.randomUUID().toString()
        val epubDir = File(context.filesDir, "epubs/$bookId")
        epubDir.mkdirs()

        val epubFile = File(epubDir, fileName)
        epubFile.outputStream().use { inputStream.copyTo(it) }

        val meta = EpubParser.parseMetadata(epubFile)

        val book = Book(
            id = bookId,
            title = meta.title,
            author = meta.author,
            description = meta.description,
            totalChapters = meta.chapters.size.coerceAtLeast(1),
            language = meta.language ?: "fr",
            addedAt = System.currentTimeMillis()
        )

        bookDao.insert(book.toEntity(epubFile.absolutePath))
        return book
    }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter {
        val book = bookDao.getById(bookId)
            ?: throw IllegalStateException("Livre introuvable : $bookId")

        val epubFile = File(book.filePath)
        require(epubFile.exists()) { "EPUB introuvable" }

        val meta = EpubParser.parseMetadata(epubFile)
        val chapterRef = meta.chapters.getOrNull(chapterIndex)

        val opfPath = findOpfPath(epubFile)
        val text = if (chapterRef != null) {
            EpubParser.extractChapterText(epubFile, chapterRef.href, opfPath)
        } else ""

        val sentences = chunkText(text)

        return Chapter(
            index = chapterIndex,
            title = chapterRef?.title ?: "Chapitre ${chapterIndex + 1}",
            sentences = sentences
        )
    }

    override suspend fun getAllBooks(): List<Book> =
        bookDao.getAll().first().map { it.toDomain() }

    override suspend fun saveProgress(progress: Progress) =
        progressDao.upsert(progress.toEntity())

    override suspend fun getProgress(bookId: String): Progress? =
        progressDao.getByBookId(bookId)?.toDomain()

    private fun findOpfPath(epubFile: File): String {
        ZipFile(epubFile).use { zip ->
            val entry = zip.getEntry("META-INF/container.xml") ?: return ""
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            return Regex("""full-path="([^"]+)"""").find(xml)?.groupValues?.get(1) ?: ""
        }
    }
}
