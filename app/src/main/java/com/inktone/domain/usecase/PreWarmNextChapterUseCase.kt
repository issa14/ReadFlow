package com.inktone.domain.usecase

import android.util.Log
import com.inktone.domain.model.Book
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.service.audio.PlaybackOrchestrator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pré-synthétise la première phrase du chapitre suivant en arrière-plan.
 *
 * Appelé après chaque [LoadChapterUseCase] pour que le passage
 * au chapitre N+1 soit instantané (gap zéro, pas d'attente
 * de synthèse WebSocket/ONNX).
 *
 * Extraite de [ReaderViewModel.preWarmNextChapter()].
 */
@Singleton
class PreWarmNextChapterUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val ttsRepository: TtsRepository,
    private val orchestrator: PlaybackOrchestrator
) {
    companion object {
        private const val TAG = "PreWarmChapter"
    }

    /**
     * Pré-synthétise la première phrase du chapitre suivant.
     *
     * @param book Le livre en cours de lecture.
     * @param currentChapterIndex L'index du chapitre actuel.
     * @param voice L'identifiant de la voix TTS.
     * @param speed La vitesse de lecture.
     */
    suspend operator fun invoke(
        book: Book,
        currentChapterIndex: Int,
        voice: Int,
        speed: Float
    ) {
        val nextIndex = currentChapterIndex + 1
        if (nextIndex >= book.totalChapters) return

        try {
            val nextChapter = bookRepository.getChapter(book.id, nextIndex)
            val firstSentence = nextChapter.sentences.firstOrNull() ?: return
            val result = ttsRepository.synthesize(firstSentence.text, voice, speed)
            orchestrator.preWarm(result)
            Log.d(TAG, "Pre-warm OK: chapitre ${nextIndex + 1}, phrase 1 (${result.engineId})")
        } catch (e: Exception) {
            Log.w(TAG, "Pre-warm échoué pour chapitre ${nextIndex + 1}: ${e.message}")
        }
    }
}
