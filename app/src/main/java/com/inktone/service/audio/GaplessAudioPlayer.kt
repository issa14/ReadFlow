package com.inktone.service.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
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
        /** Buffer de silence pré-alloué (1 seconde à 22050 Hz) pour éviter les allocations par phrase. */
        val SILENCE_BUFFER = FloatArray(22050)
        /** Taille des chunks d'écriture AudioTrack (= taille du buffer de conversion réutilisable). */
        const val CHUNK_SIZE = 4096
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

    /**
     * File d'attente non-bloquante pour les segments audio.
     *
     * Remplace [kotlinx.coroutines.channels.Channel] qui pouvait causer un deadlock :
     * si le consommateur ([startLoop]) s'arrêtait (pause/stop) alors que le producteur
     * ([PlaybackOrchestrator]) appelait [enqueue], le [Channel.send] suspendait
     * indéfiniment car plus personne ne consommait.
     *
     * [ConcurrentLinkedQueue] + [Semaphore] garantissent :
     * - [enqueue] n'est jamais bloquant (add + release).
     * - [startLoop] attend avec timeout via [Semaphore.tryAcquire].
     */
    private val queue = ConcurrentLinkedQueue<FloatArray>()
    private val queueSemaphore = Semaphore(0)

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Verrou protégeant le cycle de vie de l'[AudioTrack] contre le use-after-free.
     *
     * [stop()] acquiert ce verrou avant de libérer le track, et [writeBlocking()]
     * l'acquiert avant chaque écriture. Cela garantit qu'aucune écriture n'est
     * en cours pendant la libération, éliminant le crash natif SIGSEGV.
     */
    private val writeLock = ReentrantLock()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Compteur de segments joués — réinitialisé à chaque play(). */
    @Volatile var completedCount = 0
        private set

    /** Volume actuel (0.0 à 1.0). */
    @Volatile var currentVolume: Float = 1.0f
        private set

    /**
     * Flag atomique indiquant qu'un arrêt est demandé.
     *
     * [writeBlocking()] vérifie ce flag sous [writeLock] avant chaque écriture.
     * Cela garantit une sortie propre de la boucle d'écriture même si le job
     * n'a pas encore été annulé, évitant tout use-after-free sur l'[AudioTrack].
     */
    @Volatile var willStop = false
        private set

    /**
     * Buffer de conversion Float→Short réutilisable, alloué une seule fois
     * pour toute la durée de vie de l'instance.
     *
     * Avant : chaque appel à [writeBlocking] allouait un `ShortArray(n)`
     * de 330k–660k éléments (660 KB – 1.3 MB), soit ~720 allocations/heure.
     *
     * Après : conversion par chunks de [CHUNK_SIZE] dans ce buffer unique
     * (~8 KB alloué une fois). Divise les allocations par ~100x.
     */
    private val chunkBuffer = ShortArray(CHUNK_SIZE)

    /** Compteur d'allocations évitées (pour tests et benchmarking). */
    @Volatile var allocationsSaved: Long = 0
        private set

    /**
     * Ajoute un segment audio à la file de lecture.
     *
     * Non-bloquant : utilise [ConcurrentLinkedQueue.add] + [Semaphore.release].
     * Le producteur ([PlaybackOrchestrator]) ne sera jamais suspendu indéfiniment,
     * même si le consommateur est arrêté.
     */
    fun enqueue(samples: FloatArray) {
        Log.d(TAG, "TtsDebug | enqueue: ${samples.size} samples PCM (gain=${"%.1f".format(GAIN_MULTIPLIER)}x)")
        queue.add(samples)
        queueSemaphore.release()
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
        willStop = false
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
                // Attente avec timeout : si aucun segment n'arrive dans les 200ms,
                // on vérifie l'état (Playing/annulation) et on réessaie.
                // Cela évite le deadlock : si le producteur s'arrête, la boucle
                // sort proprement après le timeout.
                if (queueSemaphore.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                    val samples = queue.poll()
                    if (samples != null) {
                        writeBlocking(samples)
                        completedCount++
                        Log.d(TAG, "completed=$completedCount")
                    }
                }
                // yield() périodique pour la coopération d'annulation
                yield()
            }
        }
    }

    /** Arrête tout et vide la file. */
    fun stop() {
        // Flag atomique : signale à writeBlocking() d'arrêter d'écrire
        // avant même que le job soit annulé ou le verrou acquis.
        // Évite le use-after-free : writeBlocking() vérifie willStop
        // sous writeLock avant chaque appel à AudioTrack.write().
        willStop = true
        _state.value = State.Stopped
        job?.cancel()

        // Vider la file d'attente et réinitialiser le sémaphore
        // pour éviter qu'un producteur en attente ne reste bloqué.
        queue.clear()
        queueSemaphore.drainPermits()

        // Acquérir le verrou d'écriture avant de libérer le track :
        // garantit que writeBlocking() n'est plus en train d'écrire.
        writeLock.lock()
        try {
            track?.let { t ->
                try {
                    if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        t.pause()
                    }
                    t.flush()
                    t.stop()
                    t.release()
                } catch (_: Exception) {
                    // déjà libéré, ignorer
                }
            }
            track = null
        } finally {
            writeLock.unlock()
        }
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
     *
     * Stratégie mémoire : conversion Float→Short par chunks dans [chunkBuffer]
     * (alloué une seule fois par instance) au lieu d'allouer un ShortArray(n)
     * complet à chaque phrase. Divise les allocations par ~100x.
     *
     * Thread-safe : acquiert [writeLock] avant chaque vérification d'état
     * et chaque écriture pour éviter le use-after-free si [stop()] libère
     * le track concurremment. Vérifie également [willStop] pour une sortie
     * propre avant même l'annulation du job.
     */
    private fun writeBlocking(floatSamples: FloatArray) {
        val n = floatSamples.size
        var totalWritten = 0
        var offset = 0

        // Comptabilise l'allocation évitée : l'ancien code allouait un ShortArray(n)
        // à chaque appel. On incrémente un compteur pour les tests/benchmarks.
        allocationsSaved++

        while (offset < n) {
            val len = minOf(CHUNK_SIZE, n - offset)

            // Conversion FloatArray → ShortArray pour ce chunk uniquement
            // (dans le buffer réutilisable, pas d'allocation)
            for (i in 0 until len) {
                val pcmSample = (floatSamples[offset + i] * 32767.0f * GAIN_MULTIPLIER).toInt()
                chunkBuffer[i] = pcmSample.coerceIn(-32768, 32767).toShort()
            }

            writeLock.lock()
            try {
                // Vérifications sous verrou : willStop, état Playing, track non-null.
                // L'ordre est important : si willStop est true ou si l'état n'est plus
                // Playing, on sort immédiatement sans tenter d'écrire.
                if (willStop || _state.value != State.Playing) break
                val t = track ?: break
                val written = t.write(chunkBuffer, 0, len)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                totalWritten += written
                offset += written
            } finally {
                writeLock.unlock()
            }
        }
        Log.d(TAG, "TtsDebug | AudioTrack.write: $totalWritten shorts écrits / $n total (${"%.1f".format(totalWritten * 100f / n)}%)")
    }
}
