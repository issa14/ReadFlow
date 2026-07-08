package com.readflow.service.audio

import android.util.Log
import com.readflow.domain.model.Sentence
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chef d'orchestre de la lecture audio.
 *
 * Maintient un buffer de 3 phrases d'avance (N+1, N+2, N+3).
 * Pipeline 100% asynchrone — si une phrase n'est pas prête :
 * silence court (50ms) + skip propre.
 */
@Singleton
class PlaybackOrchestrator @Inject constructor(
    private val ttsRepository: TtsRepository,
    private val player: GaplessAudioPlayer
) {
    companion object {
        private const val TAG = "Orchestrator"
        private const val LOOKAHEAD = 3
        private const val SKIP_SILENCE_MS = 50L
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

    private var currentJob: Job? = null

    /**
     * Lance la lecture d'une liste de phrases.
     * @param sentences Phrases à lire
     * @param voice Voix TTS (0 = Jessica, 1 = Pierre)
     * @param speed Vitesse (0.5–2.0)
     * @param startFrom Index de départ (reprise de lecture)
     */
    fun play(
        sentences: List<Sentence>,
        voice: Int = 0,
        speed: Float = 1.0f,
        startFrom: Int = 0
    ) {
        if (sentences.isEmpty()) return
        stop()

        val total = sentences.size
        _progress.value = Progress(startFrom, total, sentences.getOrNull(startFrom))
        _state.value = State.Playing

        currentJob = scope.launch {
            try {
                var index = startFrom
                val buffer = ArrayDeque<SynthesisResult>(LOOKAHEAD)

                // Pré-remplir le buffer
                for (i in 0 until minOf(LOOKAHEAD, total - index)) {
                    launch {
                        val result = ttsRepository.synthesize(
                            sentences[index + i].text, voice, speed
                        )
                        synchronized(buffer) { buffer.add(result) }
                    }
                }

                while (isActive && index < total && _state.value == State.Playing) {
                    // Attendre que le buffer ait au moins 1 élément
                    var result: SynthesisResult?
                    var waited = 0
                    do {
                        result = synchronized(buffer) { buffer.removeFirstOrNull() }
                        if (result == null) {
                            delay(SKIP_SILENCE_MS)
                            waited++
                        }
                    } while (result == null && waited < 10)

                    if (result == null) {
                        // Skip : phrase non prête
                        Log.w(TAG, "Skip sentence $index (timeout)")
                        index++
                        _progress.value = Progress(index, total, sentences.getOrNull(index))
                        continue
                    }

                    // Enqueue l'audio dans le player
                    player.enqueue(result.samples)
                    player.play()

                    // Lancer la synthèse de la phrase LOOKAHEAD en avance
                    val nextIdx = index + LOOKAHEAD
                    if (nextIdx < total) {
                        launch {
                            val next = ttsRepository.synthesize(
                                sentences[nextIdx].text, voice, speed
                            )
                            synchronized(buffer) { buffer.add(next) }
                        }
                    }

                    index++
                    _progress.value = Progress(index, total, sentences.getOrNull(index))
                }

                // Attendre que le player finisse
                while (player.pendingCount > 0 && isActive) {
                    delay(200)
                }
                delay(500) // laisser le dernier segment finir
                _state.value = State.Idle
                player.stop()

            } catch (e: CancellationException) {
                player.stop()
                _state.value = State.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                _state.value = State.Error(e.message ?: "Erreur inconnue")
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
