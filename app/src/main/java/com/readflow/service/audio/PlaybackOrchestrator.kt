package com.readflow.service.audio

import android.util.Log
import com.readflow.data.database.ReadingProgressDao
import com.readflow.data.database.entity.ReadingProgress
import com.readflow.domain.model.Sentence
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import com.readflow.service.onnx.OnnxInferenceService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchester la lecture TTS phrase par phrase.
 *
 * Responsabilités :
 * - Pipeline de synthèse + lecture gapless via [GaplessAudioPlayer]
 * - Gestion du focus audio via [AudioFocusManager]
 * - Persistance atomique de la progression via [ReadingProgressDao]
 *
 * Implémente [AudioFocusListener] pour réagir aux événements système
 * (appels, notifications, débranchement casque).
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val ttsRepository: TtsRepository,
    private val player: GaplessAudioPlayer,
    private val onnxService: OnnxInferenceService,
    private val audioFocusManager: AudioFocusManager,
    private val progressDao: ReadingProgressDao
) : AudioFocusListener {

    companion object {
        private const val TAG = "Orchestrator"
        private const val LOOKAHEAD = 3
        /** Silence inter-phrases en ms (évite le débit précipité). */
        private const val INTER_SENTENCE_SILENCE_MS = 300
    }

    sealed class State {
        data object Idle : State()
        data object Playing : State()
        data object Paused : State()
        data class Error(val message: String) : State()
    }

    data class Progress(
        val sentenceIndex: Int,
        val totalSentences: Int,
        val sentence: Sentence?
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val _progress = MutableStateFlow(Progress(0, 0, null))
    val progress: StateFlow<Progress> = _progress

    /** Métadonnées pour la notification / MediaSession. */
    @Volatile var currentBookTitle: String = ""
    @Volatile var currentChapterTitle: String = ""
    @Volatile var currentVoice: Int = 0
    @Volatile var currentSpeed: Float = 1.0f

    // Identité du livre/chapitre en cours, pour la persistance de progression
    @Volatile private var currentBookId: String? = null
    @Volatile private var currentChapterIdx: Int = 0
    @Volatile private var currentSentenceIdx: Int = 0

    private var currentJob: Job? = null
    @Volatile private var playGeneration: Long = 0L
    @Volatile private var wasPausedByFocusLoss: Boolean = false

    init {
        audioFocusManager.setListener(this)
    }

    // ── AudioFocusListener ───────────────────────────

    override fun onFocusGained() {
        Log.d(TAG, "onFocusGained — wasPausedByFocusLoss=$wasPausedByFocusLoss")
        if (wasPausedByFocusLoss && _state.value is State.Paused) {
            wasPausedByFocusLoss = false
            resume()
        }
    }

    override fun onFocusLost(isPermanent: Boolean) {
        Log.d(TAG, "onFocusLost — permanent=$isPermanent")
        if (_state.value is State.Playing) {
            wasPausedByFocusLoss = true
            pause()
            if (isPermanent) {
                audioFocusManager.abandonFocus()
            }
        }
    }

    override fun onAudioBecomingNoisy() {
        Log.d(TAG, "onAudioBecomingNoisy → pause")
        if (_state.value is State.Playing) {
            wasPausedByFocusLoss = true
            pause()
        }
    }

    // ── API publique ─────────────────────────────────

    /**
     * Charge la dernière progression sauvegardée pour un livre.
     * @return [ReadingProgress] ou null si aucune progression.
     */
    suspend fun loadProgress(bookId: String): ReadingProgress? {
        return withContext(Dispatchers.IO) {
            progressDao.getProgressForBook(bookId)
        }
    }

    fun play(
        sentences: List<Sentence>,
        voice: Int = 0,
        speed: Float = 1.0f,
        startFrom: Int = 0,
        bookTitle: String = "",
        chapterTitle: String = "",
        bookId: String = "",
        chapterIndex: Int = 0
    ) {
        if (sentences.isEmpty()) return
        stop()

        // Demander le focus audio — ne pas lancer sans accord
        val focusGranted = audioFocusManager.requestFocus()
        if (!focusGranted) {
            Log.w(TAG, "Focus audio refusé, lecture annulée")
            _state.value = State.Error("Focus audio non disponible")
            return
        }

        // Synchroniser le sample rate du player avec le modèle Piper (22050 Hz)
        player.sampleRate = onnxService.getSampleRate()

        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        currentVoice = voice
        currentSpeed = speed
        currentBookId = bookId
        currentChapterIdx = chapterIndex
        currentSentenceIdx = startFrom

        val total = sentences.size
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Playing
        wasPausedByFocusLoss = false

        val myGeneration = ++playGeneration
        currentJob = scope.launch {
            try {
                val buffer = Channel<SynthesisResult>(Channel.UNLIMITED)

                // Synthétiser la première phrase en priorité (bloquant)
                val first = ttsRepository.synthesize(sentences[startFrom].text, voice, speed)
                buffer.send(first)

                // Lancer le pré-remplissage asynchrone (N+1, N+2, ...)
                val fillJob = launch {
                    for (i in 1 until sentences.size) {
                        if (!isActive) break
                        val idx = startFrom + i
                        if (idx >= total) break
                        try {
                            val result = ttsRepository.synthesize(sentences[idx].text, voice, speed)
                            buffer.send(result)
                        } catch (e: CancellationException) { break }
                        catch (e: Exception) {
                            Log.e(TAG, "Synthesis error sentence $idx: ${e.message}", e)
                        }
                    }
                    buffer.close()
                }

                var started = false

                for (result in buffer) {
                    if (!isActive || _state.value != State.Playing) break

                    player.enqueue(result.samples)
                    val silenceLen = (result.sampleRate * INTER_SENTENCE_SILENCE_MS / 1000)
                    player.enqueue(FloatArray(silenceLen) { 0f })

                    if (!started) {
                        player.play()
                        started = true
                        // Surveiller completedCount pour le surlignage + persistance
                        launch {
                            var lastCompleted = 0
                            while (isActive && _state.value == State.Playing &&
                                   player.completedCount < (total - startFrom) * 2) {
                                val c = player.completedCount
                                if (c != lastCompleted && c % 2 == 0) {
                                    val sentenceIdx = startFrom + c / 2
                                    val currentSentence = sentences.getOrNull(sentenceIdx - 1)
                                    currentSentenceIdx = sentenceIdx
                                    _progress.value = Progress(
                                        sentenceIdx, total, currentSentence)

                                    // ── Persistance atomique à chaque transition de phrase ──
                                    saveProgressAsync(
                                        bookId = bookId,
                                        chapterIdx = chapterIndex,
                                        sentenceIdx = sentenceIdx,
                                        charOffset = currentSentence?.startOffset ?: 0
                                    )
                                }
                                lastCompleted = c
                                delay(100)
                            }
                        }
                    }
                }

                val expectedSegments = (total - startFrom) * 2
                while (player.completedCount < expectedSegments && isActive && _state.value == State.Playing) {
                    delay(200)
                }
                delay(300)
                if (playGeneration == myGeneration) {
                    _state.value = State.Idle
                    // Sauvegarder la progression finale (fin de chapitre)
                    saveProgressAsync(
                        bookId = bookId,
                        chapterIdx = chapterIndex,
                        sentenceIdx = total,
                        charOffset = 0
                    )
                }
                player.stop()
                audioFocusManager.abandonFocus()
                fillJob.cancel()

            } catch (e: CancellationException) {
                player.stop()
                audioFocusManager.abandonFocus()
                if (playGeneration == myGeneration) _state.value = State.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                if (playGeneration == myGeneration) _state.value = State.Error(e.message ?: "Erreur")
                player.stop()
                audioFocusManager.abandonFocus()
            }
        }
    }

    fun pause() {
        _state.value = State.Paused
        player.pause()
    }

    fun resume() {
        _state.value = State.Playing
        player.resume()
    }

    fun stop() {
        currentJob?.cancel()
        player.stop()
        audioFocusManager.abandonFocus()
        _state.value = State.Idle
        wasPausedByFocusLoss = false
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
        audioFocusManager.setListener(null)
    }

    // ── Private ───────────────────────────────────────

    /**
     * Sauvegarde la progression de manière atomique dans un scope
     * lié au cycle de vie du service, sur [Dispatchers.IO].
     */
    private fun saveProgressAsync(
        bookId: String,
        chapterIdx: Int,
        sentenceIdx: Int,
        charOffset: Int
    ) {
        if (bookId.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                progressDao.saveProgress(
                    ReadingProgress(
                        bookId = bookId,
                        chapterIndex = chapterIdx,
                        sentenceIndex = sentenceIdx,
                        characterOffset = charOffset,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Progression sauvegardée: book=$bookId ch=$chapterIdx sent=$sentenceIdx")
            } catch (e: Exception) {
                Log.e(TAG, "Échec sauvegarde progression: ${e.message}", e)
            }
        }
    }
}
