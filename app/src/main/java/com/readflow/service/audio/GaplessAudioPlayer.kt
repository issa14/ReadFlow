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

    /** Ajoute un segment audio à la file de lecture. */
    fun enqueue(samples: FloatArray) {
        queue.add(samples)
    }

    /** Nombre de segments en attente. */
    val pendingCount: Int get() = queue.size

    /** Démarre la lecture de la file d'attente. */
    fun play() {
        if (_state.value == State.Playing) return
        _state.value = State.Playing

        job = scope.launch {
            ensureTrack()

            while (isActive && _state.value == State.Playing) {
                val samples = queue.poll()
                if (samples != null) {
                    writeBlocking(samples)
                } else {
                    // File vide — attendre ou arrêter
                    if (queue.isEmpty()) {
                        delay(100)
                        if (queue.isEmpty()) {
                            _state.value = State.Idle
                            break
                        }
                    }
                }
            }
        }
    }

    /** Met en pause. */
    fun pause() {
        _state.value = State.Paused
        track?.pause()
    }

    /** Reprend après pause. */
    fun resume() {
        _state.value = State.Playing
        track?.play()
        play()
    }

    /** Arrête tout et vide la file. */
    fun stop() {
        _state.value = State.Stopped
        job?.cancel()
        queue.clear()
        track?.let {
            it.pause()
            it.flush()
            it.stop()
            it.release()
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
