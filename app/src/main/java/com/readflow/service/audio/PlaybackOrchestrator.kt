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
 * Énumération du statut de lecture exposé à l'UI.
 */
enum class PlaybackStatus {
    /** Aucune lecture en cours. */
    IDLE,
    /** Lecture active. */
    PLAYING,
    /** Lecture mise en pause par l'utilisateur ou le système. */
    PAUSED,
    /** Synthèse en cours, attente avant lecture audio. */
    BUFFERING
}

/**
 * État de lecture complet exposé à l'UI via [StateFlow].
 *
 * Contient toutes les informations nécessaires au surlignage
 * de la phrase active et à l'autoscroll dans l'interface de lecture.
 */
data class PlaybackState(
    /** Index de la phrase en cours de lecture. */
    val activeSentenceIndex: Int = 0,
    /** Texte de la phrase en cours de lecture. */
    val activeSentenceText: String = "",
    /** Nombre total de phrases dans le chapitre courant. */
    val totalSentences: Int = 0,
    /** Statut actuel du moteur de lecture. */
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    /** Titre du livre en cours (pour notification). */
    val bookTitle: String = "",
    /** Titre du chapitre en cours. */
    val chapterTitle: String = "",
    /** Durée de la phrase active en millisecondes. */
    val sentenceDurationMs: Long = 0L,
    /** Timestamp de démarrage de la phrase active. */
    val sentenceStartTimestamp: Long = 0L
)

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
        private const val INTER_SENTENCE_SILENCE_MS = 300
    }

    sealed class State {
        data object Idle : State()
        data object Loading : State()
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

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining: StateFlow<Int?> = _sleepTimerRemaining

    private var sleepTimerJob: Job? = null

    @Volatile var currentBookTitle: String = ""
    @Volatile var currentChapterTitle: String = ""
    @Volatile var currentVoice: Int = 0
    @Volatile var currentSpeed: Float = 1.0f

    @Volatile private var currentBookId: String? = null
    @Volatile private var currentChapterIdx: Int = 0
    // AtomicInteger garantit la visibilité ET l'atomicité des lectures/écritures
    // entre coroutines, contrairement à @Volatile qui ne garantit pas l'atomicité
    // des opérations read-modify-write.
    private val currentSentenceIdx = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var currentTotalSentences: Int = 0
    @Volatile private var currentSentences: List<Sentence> = emptyList()

    private lateinit var sentenceDurations: LongArray // alloué dans play()

    private var currentJob: Job? = null
    private val playGeneration = java.util.concurrent.atomic.AtomicLong(0L)
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
        if (_state.value is State.Playing || _state.value is State.Loading) {
            wasPausedByFocusLoss = true
            pause()
            if (isPermanent) {
                audioFocusManager.abandonFocus()
            }
        }
    }

    override fun onAudioBecomingNoisy() {
        Log.d(TAG, "onAudioBecomingNoisy → pause")
        if (_state.value is State.Playing || _state.value is State.Loading) {
            wasPausedByFocusLoss = true
            pause()
        }
    }

    override fun onDuck() {
        Log.d(TAG, "onDuck → baisse du volume")
        player.setVolume(0.2f)
    }

    override fun onUnduck() {
        Log.d(TAG, "onUnduck → restauration du volume")
        if (_sleepTimerRemaining.value == null) {
            player.setVolume(1.0f)
        }
    }

    // ── API publique ─────────────────────────────────

    suspend fun loadProgress(bookId: String): ReadingProgress? {
        return withContext(Dispatchers.IO) {
            progressDao.getProgressForBook(bookId)
        }
    }

    fun seekToNext() {
        val sentences = currentSentences
        if (sentences.isEmpty()) return
        val next = (currentSentenceIdx.get() + 1).coerceAtMost(sentences.lastIndex)
        if (next != currentSentenceIdx.get()) {
            val bookId = currentBookId ?: return
            stop()
            play(
                sentences = sentences,
                voice = currentVoice,
                speed = currentSpeed,
                startFrom = next,
                bookTitle = currentBookTitle,
                chapterTitle = currentChapterTitle,
                bookId = bookId,
                chapterIndex = currentChapterIdx
            )
        }
    }

    fun seekToPrevious() {
        val sentences = currentSentences
        if (sentences.isEmpty()) return
        val prev = (currentSentenceIdx.get() - 1).coerceAtLeast(0)
        if (prev != currentSentenceIdx.get()) {
            val bookId = currentBookId ?: return
            stop()
            play(
                sentences = sentences,
                voice = currentVoice,
                speed = currentSpeed,
                startFrom = prev,
                bookTitle = currentBookTitle,
                chapterTitle = currentChapterTitle,
                bookId = bookId,
                chapterIndex = currentChapterIdx
            )
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

        player.sampleRate = onnxService.getSampleRate()

        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        currentVoice = voice
        currentSpeed = speed
        currentBookId = bookId
        currentChapterIdx = chapterIndex
        currentSentenceIdx.set(startFrom)
        currentTotalSentences = sentences.size
        currentSentences = sentences

        val total = sentences.size
        sentenceDurations = LongArray(total)
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Loading
        wasPausedByFocusLoss = false

        updatePlaybackState(
            index = startFrom,
            text = sentences.getOrNull(startFrom)?.text ?: "",
            total = total,
            status = PlaybackStatus.BUFFERING
        )

        val myGeneration = playGeneration.incrementAndGet()
        currentJob = scope.launch {
            try {
                val buffer = Channel<SynthesisResult>(LOOKAHEAD)

                val fillJob = launch {
                    try {
                        for (i in 0 until sentences.size) {
                            if (!isActive) break
                            val idx = startFrom + i
                            if (idx >= total) break
                            try {
                                val result = ttsRepository.synthesize(sentences[idx].text, voice, speed)
                                sentenceDurations[idx] = result.audioDurationMs
                                buffer.send(result)
                            } catch (e: CancellationException) { break }
                            catch (e: Exception) {
                                Log.e(TAG, "Synthesis error sentence $idx: ${e.message}", e)
                            }
                        }
                    } finally {
                        buffer.close()
                    }
                }

                var started = false
                var currentReadIdx = startFrom

                try {
                    for (result in buffer) {
                        if (!isActive || (_state.value != State.Playing && _state.value != State.Loading)) break

                        player.enqueue(result.samples)
                        val silenceLen = (result.sampleRate * INTER_SENTENCE_SILENCE_MS / 1000)
                            .coerceAtMost(GaplessAudioPlayer.SILENCE_BUFFER.size)
                        player.enqueue(GaplessAudioPlayer.SILENCE_BUFFER.copyOf(silenceLen))

                        if (!started) {
                            _state.value = State.Playing
                            player.play()
                            started = true

                            val firstDuration = sentenceDurations.getOrElse(startFrom) { 0L }
                            val firstStartTime = System.currentTimeMillis()
                            updatePlaybackState(
                                index = startFrom,
                                text = sentences.getOrNull(startFrom)?.text ?: "",
                                total = total,
                                status = PlaybackStatus.PLAYING,
                                durationMs = firstDuration,
                                startTimestamp = firstStartTime
                            )

                            launch {
                                var lastSentenceIdx = startFrom
                                while (isActive && _state.value == State.Playing &&
                                       player.completedCount < (total - startFrom) * 2) {
                                    val c = player.completedCount
                                    val sentenceIdx = startFrom + c / 2
                                    if (sentenceIdx != lastSentenceIdx) {
                                        val currentSentence = sentences.getOrNull(sentenceIdx)
                                        currentReadIdx = sentenceIdx
                                        currentSentenceIdx.set(sentenceIdx)
                                        _progress.value = Progress(
                                            sentenceIdx, total, currentSentence)

                                        val duration = sentenceDurations.getOrElse(sentenceIdx) { 0L }
                                        val startTime = System.currentTimeMillis()

                                        updatePlaybackState(
                                            index = sentenceIdx,
                                            text = currentSentence?.text ?: "",
                                            total = total,
                                            status = PlaybackStatus.PLAYING,
                                            durationMs = duration,
                                            startTimestamp = startTime
                                        )

                                        saveProgressAsync(
                                            bookId = bookId,
                                            chapterIdx = chapterIndex,
                                            sentenceIdx = sentenceIdx,
                                            charOffset = currentSentence?.startOffset ?: 0
                                        )
                                        lastSentenceIdx = sentenceIdx
                                    }
                                    delay(50)
                                }
                            }
                        }
                    }

                    val expectedSegments = (total - startFrom) * 2
                    while (player.completedCount < expectedSegments && isActive && _state.value == State.Playing) {
                        delay(200)
                    }
                    delay(300)
                    if (playGeneration.get() == myGeneration) {
                        _state.value = State.Idle
                        updatePlaybackState(
                            index = currentReadIdx,
                            text = "",
                            total = total,
                            status = PlaybackStatus.IDLE
                        )
                        saveProgressAsync(
                            bookId = bookId,
                            chapterIdx = chapterIndex,
                            sentenceIdx = total,
                            charOffset = 0
                        )
                    }
                } finally {
                    fillJob.cancel()
                    player.stop()
                    audioFocusManager.abandonFocus()
                }
            } catch (e: CancellationException) {
                player.stop()
                audioFocusManager.abandonFocus()
                if (playGeneration.get() == myGeneration) {
                    _state.value = State.Idle
                    updatePlaybackState(
                        index = currentSentenceIdx.get(),
                        text = "",
                        total = currentTotalSentences,
                        status = PlaybackStatus.IDLE
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                if (playGeneration.get() == myGeneration) _state.value = State.Error(e.message ?: "Erreur")
                updatePlaybackState(
                    index = currentSentenceIdx.get(),
                    text = "",
                    total = currentTotalSentences,
                    status = PlaybackStatus.IDLE
                )
                player.stop()
                audioFocusManager.abandonFocus()
            }
        }
    }

    fun pause() {
        _state.value = State.Paused
        player.pause()
        updatePlaybackState(
            index = currentSentenceIdx.get(),
            text = currentSentences.getOrNull(currentSentenceIdx.get())?.text ?: "",
            total = currentTotalSentences,
            status = PlaybackStatus.PAUSED
        )
    }

    fun resume() {
        _state.value = State.Playing
        player.resume()
        updatePlaybackState(
            index = currentSentenceIdx.get(),
            text = currentSentences.getOrNull(currentSentenceIdx.get())?.text ?: "",
            total = currentTotalSentences,
            status = PlaybackStatus.PLAYING
        )
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val totalSeconds = minutes * 60
        _sleepTimerRemaining.value = totalSeconds

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimerRemaining.value = remaining

                if (_state.value == State.Playing) {
                    if (remaining in 1..15) {
                        val volume = remaining / 15f
                        player.setVolume(volume)
                    } else if (remaining > 15) {
                        player.setVolume(1.0f)
                    }
                }
            }

            Log.d(TAG, "Sleep timer reached 0 → Pausing playback smoothly")
            pause()

            delay(500)
            player.setVolume(1.0f)
            _sleepTimerRemaining.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
        player.setVolume(1.0f)
    }

    fun stop() {
        currentJob?.cancel()
        player.stop()
        audioFocusManager.abandonFocus()
        _state.value = State.Idle
        wasPausedByFocusLoss = false
        updatePlaybackState(
            index = 0,
            text = "",
            total = currentTotalSentences,
            status = PlaybackStatus.IDLE
        )
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
        audioFocusManager.setListener(null)
    }

    // ── Private ───────────────────────────────────────

    /**
     * Met à jour atomiquement le [PlaybackState] exposé à l'UI.
     */
    private fun updatePlaybackState(
        index: Int,
        text: String,
        total: Int,
        status: PlaybackStatus,
        durationMs: Long = 0L,
        startTimestamp: Long = 0L
    ) {
        _playbackState.value = PlaybackState(
            activeSentenceIndex = index,
            activeSentenceText = text,
            totalSentences = total,
            status = status,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle,
            sentenceDurationMs = durationMs,
            sentenceStartTimestamp = startTimestamp
        )
    }

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
