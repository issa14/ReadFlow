package com.readflow.service.audio

import android.util.Log
import com.readflow.data.database.ReadingProgressDao
import com.readflow.data.database.entity.ReadingProgress
import com.readflow.domain.model.Sentence
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import com.readflow.service.edge.EdgeTtsClient
import com.readflow.service.onnx.OnnxInferenceService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.locks.ReentrantLock
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

        // ── Durées de silence dynamiques selon la ponctuation ──
        private const val SILENCE_COMMA_MS     = 150   // virgule, point-virgule
        private const val SILENCE_SENTENCE_MS  = 650   // point, exclamation, interrogation
        private const val SILENCE_PARAGRAPH_MS = 1000  // saut de ligne / fin de paragraphe

        /**
         * Détermine la durée du silence à injecter après un segment,
         * en fonction de son dernier caractère de ponctuation.
         */
        private fun silenceDurationFor(text: String): Int {
            val trimmed = text.trimEnd()
            if (trimmed.isEmpty()) return SILENCE_SENTENCE_MS
            return when (trimmed.last()) {
                ',', ';'  -> SILENCE_COMMA_MS
                '.', '!', '?', '\u2026' -> SILENCE_SENTENCE_MS
                '\n'      -> SILENCE_PARAGRAPH_MS
                else      -> SILENCE_SENTENCE_MS
            }
        }
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

    /**
     * Verrou de sérialisation pour pause/resume/stop.
     *
     * Garantit l'atomicité des transitions d'état appelées depuis
     * des threads différents (UI, AudioFocus, notification MediaSession).
     */
    private val stateLock = ReentrantLock()

    /**
     * Channel de préchauffage pour les phrases du chapitre suivant.
     *
     * Le ViewModel peut y pousser des résultats de synthèse pré-calculés
     * (pre-warm) qui seront consommés après l'épuisement du buffer principal.
     * Cela permet un enchaînement inter-chapitre sans gap.
     */
    private val preWarmChannel = Channel<SynthesisResult>(Channel.UNLIMITED)

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

        if (!requestAudioFocus()) return
        initPlaybackParams(sentences, voice, speed, startFrom, bookTitle, chapterTitle, bookId, chapterIndex)

        val total = sentences.size
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Loading
        wasPausedByFocusLoss = false
        updatePlaybackState(startFrom, sentences.getOrNull(startFrom)?.text ?: "", total, PlaybackStatus.BUFFERING)

        val myGeneration = playGeneration.incrementAndGet()
        currentJob = scope.launch {
            try {
                val buffer = Channel<SynthesisResult>(LOOKAHEAD)
                val fillJob = launchSynthesisPipeline(buffer, sentences, voice, speed, startFrom, total)
                try {
                    consumeAndPlay(buffer, fillJob, sentences, voice, speed, startFrom, total,
                        bookTitle, chapterTitle, bookId, chapterIndex, myGeneration)
                } finally {
                    cleanupPlayback(fillJob)
                }
            } catch (e: CancellationException) {
                handleCancellation(myGeneration)
            } catch (e: Exception) {
                handlePlaybackError(e, myGeneration)
            }
        }
    }

    fun pause() {
        stateLock.lock()
        try {
            if (_state.value !is State.Playing && _state.value !is State.Loading) return
            _state.value = State.Paused
            player.pause()
            updatePlaybackState(
                index = currentSentenceIdx.get(),
                text = currentSentences.getOrNull(currentSentenceIdx.get())?.text ?: "",
                total = currentTotalSentences,
                status = PlaybackStatus.PAUSED
            )
        } finally {
            stateLock.unlock()
        }
    }

    fun resume() {
        stateLock.lock()
        try {
            if (_state.value !is State.Paused) return
            _state.value = State.Playing
            player.resume()
            updatePlaybackState(
                index = currentSentenceIdx.get(),
                text = currentSentences.getOrNull(currentSentenceIdx.get())?.text ?: "",
                total = currentTotalSentences,
                status = PlaybackStatus.PLAYING
            )
        } finally {
            stateLock.unlock()
        }
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
        stateLock.lock()
        try {
            currentJob?.cancel()
            currentJob = null
            // Vider le channel de préchauffage
            preWarmChannel.cancel()
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
        } finally {
            stateLock.unlock()
        }
    }

    /**
     * Injecte un résultat de synthèse pré-calculé dans le pipeline.
     *
     * Appelé par le ViewModel pour précharger la première phrase
     * du chapitre suivant pendant la lecture du chapitre courant.
     * Le résultat sera consommé après épuisement du buffer principal.
     */
    fun preWarm(result: SynthesisResult) {
        preWarmChannel.trySend(result)
        Log.d(TAG, "TtsDebug | preWarm: sentence pré-synthétisée injectée (engine=${result.engineId})")
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
        audioFocusManager.setListener(null)
    }

    // ── Sous-méthodes extraites de play() (A01) ──────

    private fun updatePlaybackState(
        index: Int, text: String, total: Int, status: PlaybackStatus,
        durationMs: Long = 0L, startTimestamp: Long = 0L
    ) {
        _playbackState.value = PlaybackState(
            activeSentenceIndex = index, activeSentenceText = text,
            totalSentences = total, status = status,
            bookTitle = currentBookTitle, chapterTitle = currentChapterTitle,
            sentenceDurationMs = durationMs, sentenceStartTimestamp = startTimestamp
        )
    }

    private fun requestAudioFocus(): Boolean {
        val granted = audioFocusManager.requestFocus()
        if (!granted) {
            Log.w(TAG, "Focus audio refusé, lecture annulée")
            _state.value = State.Error("Focus audio non disponible")
        }
        return granted
    }

    private fun initPlaybackParams(
        sentences: List<Sentence>, voice: Int, speed: Float, startFrom: Int,
        bookTitle: String, chapterTitle: String, bookId: String, chapterIndex: Int
    ) {
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
        sentenceDurations = LongArray(sentences.size)
    }

    private fun CoroutineScope.launchSynthesisPipeline(
        buffer: Channel<SynthesisResult>, sentences: List<Sentence>,
        voice: Int, speed: Float, startFrom: Int, total: Int
    ) = launch {
        try {
            for (i in 0 until sentences.size) {
                if (currentJob?.isActive != true) break
                val idx = startFrom + i
                if (idx >= total) break
                try {
                    val result = ttsRepository.synthesize(sentences[idx].text, voice, speed)
                    sentenceDurations[idx] = result.audioDurationMs
                    Log.d(TAG, "TtsDebug | synthèse OK: sentence $idx, engine=${result.engineId}, " +
                        "voice=${result.voiceLabel}, samples=${result.samples.size}, " +
                        "sr=${result.sampleRate}, dur=${result.audioDurationMs}ms, rtf=${result.realTimeFactor}")
                    buffer.send(result)
                } catch (e: CancellationException) { break }
                catch (e: Exception) {
                    Log.e(TAG, "Synthesis error sentence $idx: ${e.message}", e)
                    // Erreur réseau persistante (après retry Edge + fallback Piper) → arrêter
                    if (EdgeTtsClient.isNetworkError(e)) {
                        Log.w(TAG, "Erreur réseau persistante → arrêt du pipeline de synthèse")
                        _state.value = State.Error("Réseau perdu — la lecture s'est interrompue")
                        _playbackState.update { it.copy(status = PlaybackStatus.IDLE) }
                        break
                    }
                    // Autres erreurs (ex: échec décodage) → passer à la phrase suivante
                }
            }
        } finally {
            buffer.close()
        }
    }

    private suspend fun consumeAndPlay(
        buffer: Channel<SynthesisResult>, fillJob: Job, sentences: List<Sentence>,
        voice: Int, speed: Float, startFrom: Int, total: Int,
        bookTitle: String, chapterTitle: String, bookId: String, chapterIndex: Int,
        myGeneration: Long
    ) {
        var started = false
        var currentReadIdx = startFrom

        for (result in buffer) {
            // Attendre la fin de la pause (ne pas casser la pipeline !)
            while (_state.value == State.Paused && currentJob?.isActive == true) {
                delay(100)
            }
            // Sortir si arrêt définitif ou erreur
            if (currentJob?.isActive != true || _state.value == State.Idle || _state.value is State.Error) break

            player.enqueue(result.samples)
            Log.d(TAG, "TtsDebug | player.enqueue: ${result.samples.size} samples PCM, engine=${result.engineId}")
            val silenceMs = silenceDurationFor(result.text)
            val silenceLen = (result.sampleRate * silenceMs / 1000)
                .coerceAtMost(GaplessAudioPlayer.SILENCE_BUFFER.size)
            player.enqueue(GaplessAudioPlayer.SILENCE_BUFFER.copyOf(silenceLen))

            if (!started) {
                _state.value = State.Playing
                player.play()
                started = true
                updatePlaybackState(startFrom, sentences.getOrNull(startFrom)?.text ?: "", total,
                    PlaybackStatus.PLAYING,
                    if (startFrom >= 0 && startFrom < sentenceDurations.size) sentenceDurations[startFrom] else 0L,
                    System.currentTimeMillis())
                scope.launch {
                    var lastIdx = startFrom
                    while (currentJob?.isActive == true &&
                           _state.value != State.Idle &&
                           _state.value !is State.Error &&
                           player.completedCount < (total - startFrom) * 2) {
                        if (_state.value == State.Paused) {
                            delay(200)
                            continue
                        }
                        val c = player.completedCount
                        val sIdx = startFrom + c / 2
                        if (sIdx != lastIdx) {
                            val sent = sentences.getOrNull(sIdx)
                            currentSentenceIdx.set(sIdx)
                            _progress.value = Progress(sIdx, total, sent)
                            val dur = if (sIdx >= 0 && sIdx < sentenceDurations.size) sentenceDurations[sIdx] else 0L
                            updatePlaybackState(sIdx, sent?.text ?: "", total,
                                PlaybackStatus.PLAYING, dur, System.currentTimeMillis())
                            saveProgressAsync(bookId, chapterIndex, sIdx, sent?.startOffset ?: 0)
                            lastIdx = sIdx
                        }
                        delay(50)
                    }
                }
            }
        }

        // ── Consommer les résultats préchauffés (chapitre suivant) ──
        while (true) {
            val preWarmed = preWarmChannel.tryReceive().getOrNull() ?: break
            if (currentJob?.isActive != true || _state.value == State.Idle || _state.value is State.Error) break

            player.enqueue(preWarmed.samples)
            Log.d(TAG, "TtsDebug | preWarm enqueue: ${preWarmed.samples.size} samples PCM, engine=${preWarmed.engineId}")
            val silenceMs = silenceDurationFor(preWarmed.text)
            val silenceLen = (preWarmed.sampleRate * silenceMs / 1000)
                .coerceAtMost(GaplessAudioPlayer.SILENCE_BUFFER.size)
            player.enqueue(GaplessAudioPlayer.SILENCE_BUFFER.copyOf(silenceLen))
        }

        val expectedSegments = (total - startFrom) * 2
        while (player.completedCount < expectedSegments &&
               currentJob?.isActive == true &&
               (_state.value == State.Playing || _state.value == State.Paused)) {
            if (_state.value == State.Paused) {
                delay(500)
            } else {
                delay(200)
            }
        }
        delay(300)
        // Ne pas écraser un état d'erreur (ex: perte réseau)
        if (playGeneration.get() == myGeneration && _state.value !is State.Error) {
            _state.value = State.Idle
            updatePlaybackState(currentReadIdx, "", total, PlaybackStatus.IDLE)
            saveProgressAsync(bookId, chapterIndex, total, 0)
        }
    }

    private fun cleanupPlayback(fillJob: Job) {
        fillJob.cancel()
        player.stop()
        audioFocusManager.abandonFocus()
    }

    private fun handleCancellation(myGeneration: Long) {
        player.stop()
        audioFocusManager.abandonFocus()
        if (playGeneration.get() == myGeneration) {
            _state.value = State.Idle
            updatePlaybackState(currentSentenceIdx.get(), "", currentTotalSentences, PlaybackStatus.IDLE)
        }
    }

    private fun handlePlaybackError(e: Exception, myGeneration: Long) {
        Log.e(TAG, "Playback error", e)
        if (playGeneration.get() == myGeneration) _state.value = State.Error(e.message ?: "Erreur")
        updatePlaybackState(currentSentenceIdx.get(), "", currentTotalSentences, PlaybackStatus.IDLE)
        player.stop()
        audioFocusManager.abandonFocus()
    }

    private fun saveProgressAsync(
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
