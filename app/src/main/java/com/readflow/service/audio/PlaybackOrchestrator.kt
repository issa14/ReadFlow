package com.readflow.service.audio

import android.util.Log
import com.readflow.domain.model.Sentence
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val ttsRepository: TtsRepository,
    private val player: GaplessAudioPlayer
) {
    companion object {
        private const val TAG = "Orchestrator"
        private const val LOOKAHEAD = 3
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

    private var currentJob: Job? = null
    @Volatile private var playGeneration: Long = 0L  // prévient les races Stop/Play

    fun play(
        sentences: List<Sentence>,
        voice: Int = 0,
        speed: Float = 1.0f,
        startFrom: Int = 0,
        bookTitle: String = "",
        chapterTitle: String = ""
    ) {
        if (sentences.isEmpty()) return
        stop()

        currentBookTitle = bookTitle
        currentChapterTitle = chapterTitle
        currentVoice = voice
        currentSpeed = speed

        val total = sentences.size
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Playing

        val myGeneration = ++playGeneration
        currentJob = scope.launch {
            try {
                // Channel pour recevoir les résultats de synthèse
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
                            // Continue — ne pas bloquer tout le pipeline
                        }
                    }
                    buffer.close()
                }

                // Boucle de lecture — démarrer le player après le 1er enqueue
                var index = startFrom
                var started = false

                for (result in buffer) {
                    if (!isActive || _state.value != State.Playing) break

                    player.enqueue(result.samples)
                    if (!started) {
                        player.play()
                        started = true
                    }

                    index++
                    _progress.value = Progress(index, total, sentences.getOrNull(index))
                    Log.d(TAG, "Playing sentence $index/${total}")
                }

                // Attendre que le player ait fini TOUS les segments
                val totalSentences = total - startFrom
                while (player.completedCount < totalSentences && isActive && _state.value == State.Playing) {
                    delay(200)
                }
                delay(300)
                if (playGeneration == myGeneration) _state.value = State.Idle
                player.stop()
                fillJob.cancel()

            } catch (e: CancellationException) {
                player.stop()
                if (playGeneration == myGeneration) _state.value = State.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                if (playGeneration == myGeneration) _state.value = State.Error(e.message ?: "Erreur")
                player.stop()
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
        _state.value = State.Idle
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
    }
}
