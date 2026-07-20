package com.inktone.domain.usecase

import com.inktone.domain.model.Book
import com.inktone.domain.repository.BookRepository
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
        return bookRepository.importEpub(inputStream, fileName, onProgress = onProgress)
    }
}
