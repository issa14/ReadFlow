package com.readflow.domain.usecase

import com.readflow.domain.model.Book
import com.readflow.domain.repository.BookRepository
import java.io.InputStream
import javax.inject.Inject

/** Importe un fichier EPUB et le parse en [Book] avec progression. */
class ParseEpubUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(
        inputStream: InputStream,
        fileName: String,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Book {
        require(fileName.endsWith(".epub", ignoreCase = true)) {
            "Format non supporté : $fileName"
        }
        return bookRepository.importEpub(inputStream, fileName, onProgress)
    }
}
