package com.readflow.service.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecteur audio gapless basé sur AudioTrack.
 *
 * Reçoit des segments PCM (phrases synthétisées) et les enchaîne
 * sans silence entre eux. Chaque segment est ajouté à une file d'attente
 * et lu dès que le précédent est terminé.
 *
 * Format : PCM 16-bit mono. Sample rate dynamique (22050 Hz pour Piper).
 * Sherpa-ONNX produit du PCM float → conversion FloatArray → ShortArray
 * avec gain 3x pour compenser le volume natif faible du modèle Miro.
 */
@Singleton
class GaplessAudioPlayer @Inject constructor() {

    companion object {
        private const val TAG = "GaplessPlayer"
        /** Multiplicateur de gain appliqué lors de la conversion float→int16. */
        private const val GAIN_MULTIPLIER = 3.0f
    }

    /**
     * Fréquence d'échantillonnage — doit correspondre au modèle TTS.
     * Piper VITS standard = 22050 Hz. Surchargé par PlaybackOrchestrator.
     */
    @Volatile var sampleRate: Int = 22050

    sealed class State {
        data object Idle : State()
        data object Playing : State()
        data object Paused : State()
        data object Stopped : State()
    }

    private var track: AudioTrack? = null
    // Utilisation d'un Channel avec une capacité de 2 pour pré-charger N+1 pendant la lecture de N.
    private var queue = Channel<FloatArray>(2)
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

    /** Ajoute un segment audio à la file de lecture (bloquant si la file est pleine). */
    suspend fun enqueue(samples: FloatArray) {
        queue.send(samples)
    }

    /** Nombre de segments en attente. */
    val pendingCount: Int get() = 0 // Pas supporté tel quel par Channel de manière précise

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
                // receive() va suspendre jusqu'à ce qu'un chunk soit disponible
                val samples = queue.receiveCatching().getOrNull()
                if (samples != null) {
                    writeBlocking(samples)
                    completedCount++
                    Log.d(TAG, "completed=$completedCount")
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
        queue.cancel()
        queue = Channel(2)
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
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096 * 8)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track?.play()
    }

    /**
     * Convertit le FloatArray PCM (Sherpa-ONNX) en ShortArray PCM 16-bit
     * avec gain 3x, puis écrit dans l'AudioTrack par chunks.
     */
    private fun writeBlocking(floatSamples: FloatArray) {
        val t = track ?: return

        // Conversion FloatArray → ShortArray avec gain
        val n = floatSamples.size
        val shortSamples = ShortArray(n)
        for (i in 0 until n) {
            val pcmSample = (floatSamples[i] * 32767.0f * GAIN_MULTIPLIER).toInt()
            shortSamples[i] = pcmSample.coerceIn(-32768, 32767).toShort()
        }

        val chunkSize = 4096
        var offset = 0
        while (offset < n && _state.value == State.Playing) {
            val len = minOf(chunkSize, n - offset)
            val written = t.write(shortSamples, offset, len)
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error: $written")
                break
            }
            offset += written
        }
    }
}
