package com.readflow.service.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.readflow.domain.model.SynthesisResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecteur audio gapless basé sur AudioTrack.
 *
 * Reçoit des segments PCM (phrases synthétisées) et les enchaîne
 * sans silence entre eux. Chaque segment est ajouté à une file d'attente
 * et lu dès que le précédent est terminé.
 *
 * Format : PCM float 22050 Hz mono.
 */
@Singleton
class GaplessAudioPlayer @Inject constructor() {

    companion object {
        private const val TAG = "GaplessPlayer"
        private const val SAMPLE_RATE = 22050
    }

    sealed class State {
        data object Idle : State()
        data object Playing : State()
        data object Paused : State()
        data object Stopped : State()
    }

    private var track: AudioTrack? = null
    private val queue = ConcurrentLinkedQueue<FloatArray>()
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Compteur de segments joués — réinitialisé à chaque play(). */
    @Volatile var completedCount = 0
        private set

    /** Volume actuel (0.0 à 1.0). */
    @Volatile var currentVolume: Float = 1.0f
        private set

    /** Ajoute un segment audio à la file de lecture. */
    fun enqueue(samples: FloatArray) {
        queue.add(samples)
    }

    /** Nombre de segments en attente. */
    val pendingCount: Int get() = queue.size

    /** Règle le volume de la piste audio (0.0 = silence, 1.0 = max). */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        track?.setVolume(currentVolume)
    }

    /** Démarre la lecture de la file d'attente. */
    fun play() {
        if (_state.value == State.Playing) return
        _state.value = State.Playing
        completedCount = 0
        startLoop()
    }

    /** Met en pause. */
    fun pause() {
        _state.value = State.Paused
        track?.pause()
    }

    /** Reprend après pause (conserve completedCount). */
    fun resume() {
        _state.value = State.Playing
        track?.play()
        startLoop()
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            ensureTrack()

            while (isActive && _state.value == State.Playing) {
                val samples = queue.poll()
                if (samples != null) {
                    writeBlocking(samples)
                    completedCount++
                    Log.d(TAG, "completed=$completedCount, pending=${queue.size}")
                } else {
                    delay(50)
                }
            }
        }
    }

    /** Arrête tout et vide la file. */
    fun stop() {
        _state.value = State.Stopped
        job?.cancel()
        queue.clear()
        track?.let { t ->
            try {
                if (t.state == AudioTrack.STATE_INITIALIZED) {
                    t.pause()
                    t.flush()
                    t.stop()
                }
                t.release()
            } catch (_: Exception) {
                // déjà libéré, ignorer
            }
        }
        track = null
    }

    /** Libère les ressources. */
    fun release() {
        stop()
        scope.cancel()
    }

    // ── Private ────────────────────────────────────────

    private fun ensureTrack() {
        if (track != null && track?.state == AudioTrack.STATE_INITIALIZED) return

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096 * 4)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track?.play()
    }

    private fun writeBlocking(samples: FloatArray) {
        val t = track ?: return
        val chunkSize = 4096
        var offset = 0
        while (offset < samples.size && _state.value == State.Playing) {
            val len = minOf(chunkSize, samples.size - offset)
            val written = t.write(samples, offset, len, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error: $written")
                break
            }
            offset += written
        }
    }
}
