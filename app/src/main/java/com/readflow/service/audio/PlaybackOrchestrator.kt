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

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    BUFFERING
}

data class PlaybackState(
    val activeSentenceIndex: Int = 0,
    val activeSentenceText: String = "",
    val totalSentences: Int = 0,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val bookTitle: String = "",
    val chapterTitle: String = ""
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
    @Volatile private var currentSentenceIdx: Int = 0
    @Volatile private var currentTotalSentences: Int = 0
    @Volatile private var currentSentences: List<Sentence> = emptyList()

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
        val next = (currentSentenceIdx + 1).coerceAtMost(sentences.lastIndex)
        if (next != currentSentenceIdx) {
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
        val prev = (currentSentenceIdx - 1).coerceAtLeast(0)
        if (prev != currentSentenceIdx) {
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
        currentSentenceIdx = startFrom
        currentTotalSentences = sentences.size
        currentSentences = sentences

        val total = sentences.size
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Loading
        wasPausedByFocusLoss = false

        updatePlaybackState(
            index = startFrom,
            text = sentences.getOrNull(startFrom)?.text ?: "",
            total = total,
            status = PlaybackStatus.BUFFERING
        )

        val myGeneration = ++playGeneration
        currentJob = scope.launch {
            try {
                val buffer = Channel<SynthesisResult>(LOOKAHEAD)

                val fillJob = launch {
                    for (i in 0 until sentences.size) {
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
                var currentReadIdx = startFrom

                for (result in buffer) {
                    if (!isActive || (_state.value != State.Playing && _state.value != State.Loading)) break

                    player.enqueue(result.samples)
                    val silenceLen = (result.sampleRate * INTER_SENTENCE_SILENCE_MS / 1000)
                    player.enqueue(FloatArray(silenceLen) { 0f })

                    if (!started) {
                        _state.value = State.Playing
                        player.play()
                        started = true
                        launch {
                            var lastCompleted = 0
                            while (isActive && _state.value == State.Playing &&
                                   player.completedCount < (total - startFrom) * 2) {
                                val c = player.completedCount
                                if (c != lastCompleted && c % 2 == 0) {
                                    val sentenceIdx = startFrom + c / 2
                                    val currentSentence = sentences.getOrNull(sentenceIdx - 1)
                                    currentReadIdx = sentenceIdx
                                    currentSentenceIdx = sentenceIdx
                                    _progress.value = Progress(
                                        sentenceIdx, total, currentSentence)

                                    updatePlaybackState(
                                        index = sentenceIdx,
                                        text = currentSentence?.text ?: "",
                                        total = total,
                                        status = PlaybackStatus.PLAYING
                                    )

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
                player.stop()
                audioFocusManager.abandonFocus()
                fillJob.cancel()

            } catch (e: CancellationException) {
                player.stop()
                audioFocusManager.abandonFocus()
                if (playGeneration == myGeneration) {
                    _state.value = State.Idle
                    updatePlaybackState(
                        index = currentSentenceIdx,
                        text = "",
                        total = currentTotalSentences,
                        status = PlaybackStatus.IDLE
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                if (playGeneration == myGeneration) _state.value = State.Error(e.message ?: "Erreur")
                updatePlaybackState(
                    index = currentSentenceIdx,
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
            index = currentSentenceIdx,
            text = currentSentences.getOrNull(currentSentenceIdx)?.text ?: "",
            total = currentTotalSentences,
            status = PlaybackStatus.PAUSED
        )
    }

    fun resume() {
        _state.value = State.Playing
        player.resume()
        updatePlaybackState(
            index = currentSentenceIdx,
            text = currentSentences.getOrNull(currentSentenceIdx)?.text ?: "",
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

    private fun updatePlaybackState(
        index: Int,
        text: String,
        total: Int,
        status: PlaybackStatus
    ) {
        _playbackState.value = PlaybackState(
            activeSentenceIndex = index,
            activeSentenceText = text,
            totalSentences = total,
            status = status,
            bookTitle = currentBookTitle,
            chapterTitle = currentChapterTitle
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
