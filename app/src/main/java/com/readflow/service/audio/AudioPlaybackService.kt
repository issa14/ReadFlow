package com.readflow.service.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.readflow.MainActivity
import com.readflow.service.audio.PlaybackOrchestrator.State
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Service de lecture audio Media3 pour ReadFlow.
 *
 * Responsabilités :
 * - Exposer un [MediaSession] pour le contrôle système (écran de verrouillage,
 *   notifications MediaStyle, commandes Bluetooth/écouteurs).
 * - Maintenir une notification de premier plan conforme à Android 12/13+
 *   (foregroundServiceType="mediaPlayback" + POST_NOTIFICATIONS).
 * - Observer l'état du [PlaybackOrchestrator] pour synchroniser
 *   la notification et l'état du [Player] Media3.
 * - Libérer proprement toutes les ressources dans [onDestroy].
 *
 * Architecture :
 * ```
 * Commande système (BT/Notif) → MediaSession → ReadFlowPlayer
 *                                                    ↓
 *                                          PlaybackOrchestrator
 *                                                    ↓
 *                                          GaplessAudioPlayer (AudioTrack)
 * ```
 */
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "AudioPlaybackSvc"
        private const val CHANNEL_ID = "readflow_playback"
        private const val CHANNEL_NAME = "Lecture TTS"
        private const val NOTIFICATION_ID = 1001

        /**
         * Délai avant sortie du mode foreground après mise en pause (ms).
         *
         * Après 5 minutes de pause, on retire la notification persistante
         * ([stopForeground(STOP_FOREGROUND_DETACH)]) tout en gardant le
         * service vivant. Si l'utilisateur reprend la lecture avant ce délai,
         * le foreground est immédiatement rétabli.
         *
         * Choix : 5 minutes = compromis entre économie de batterie
         * (notification persistante = wakelock implicite) et UX
         * (reprise rapide sans redémarrage complet du pipeline ONNX).
         */
        private const val PAUSE_FOREGROUND_TIMEOUT_MS = 5L * 60 * 1000

        // Actions des boutons MediaStyle de la notification
        private const val ACTION_PAUSE = "com.readflow.action.PAUSE"
        private const val ACTION_PLAY  = "com.readflow.action.PLAY"
        private const val ACTION_STOP  = "com.readflow.action.STOP"
        private const val ACTION_PREV  = "com.readflow.action.PREV"
        private const val ACTION_NEXT  = "com.readflow.action.NEXT"
    }

    @Inject lateinit var orchestrator: PlaybackOrchestrator
    @Inject lateinit var audioFocusManager: AudioFocusManager
    @Inject lateinit var onnxService: com.readflow.service.onnx.OnnxInferenceService

    private var mediaSession: MediaSession? = null
    private var readFlowPlayer: ReadFlowPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observationJob: Job? = null
    private var progressJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    /** true si le service est actuellement en mode foreground (notification active). */
    @Volatile private var isInForeground = true

    // ── Lifecycle ────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        createNotificationChannel()

        // Initialisation du Player custom qui adapte le PlaybackOrchestrator
        // à l'interface Player de Media3
        readFlowPlayer = ReadFlowPlayer(orchestrator)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, readFlowPlayer!!)
            .setId("ReadFlow")
            .setSessionActivity(openIntent)
            .build()

        // Notification initiale immédiate (contrat foreground 5s sur Android 12+)
        try {
            startForeground(NOTIFICATION_ID, buildNotification(
                title = "ReadFlow",
                subtitle = "Prêt pour la lecture",
                isPlaying = false
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Échec startForeground: ${e.message}", e)
        }

        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY  -> orchestrator.resume()
            ACTION_PAUSE -> orchestrator.pause()
            ACTION_STOP  -> orchestrator.stop()
            ACTION_PREV  -> orchestrator.seekToPrevious()
            ACTION_NEXT  -> orchestrator.seekToNext()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved")
        if (orchestrator.state.value !is State.Playing) {
            stopSelf()
        }
        // Si en lecture, on reste vivant (foreground service)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — libération des ressources")
        stopObserving()
        cancelPauseTimeout()
        serviceScope.cancel()

        // Libération ordonnée : Player → MediaSession → AudioFocus → Orchestrator → ONNX
        try {
            readFlowPlayer?.release()
            readFlowPlayer?.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération ReadFlowPlayer: ${e.message}", e)
        }
        readFlowPlayer = null

        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération MediaSession: ${e.message}", e)
        }
        mediaSession = null

        audioFocusManager.abandonFocus()

        try {
            orchestrator.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération orchestrator: ${e.message}", e)
        }

        try {
            onnxService.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération ONNX: ${e.message}", e)
        }

        super.onDestroy()
    }

    // ── Observation de l'orchestrateur ───────────────

    private fun startObserving() {
        // Observation de l'état global (Playing/Paused/Idle/Error)
        observationJob = serviceScope.launch {
            orchestrator.state.collect { state ->
                val player = readFlowPlayer ?: return@collect
                Log.d(TAG, "State → $state")

                when (state) {
                    is State.Idle -> {
                        player.setIdle()
                        cancelPauseTimeout()
                        updateNotification("ReadFlow", "Prêt", isPlaying = false)
                    }
                    is State.Loading -> {
                        cancelPauseTimeout()
                        ensureForeground()
                        val title = orchestrator.currentChapterTitle.ifEmpty {
                            orchestrator.currentBookTitle.ifEmpty { "ReadFlow" }
                        }
                        updateNotification(title, "Chargement...", isPlaying = true)
                    }
                    is State.Playing -> {
                        cancelPauseTimeout()
                        ensureForeground()

                        val title = orchestrator.currentChapterTitle.ifEmpty {
                            orchestrator.currentBookTitle.ifEmpty { "ReadFlow" }
                        }
                        player.setContent(
                            title = title,
                            artist = orchestrator.currentBookTitle
                        )
                        player.setReady(true)
                        // setPlayWhenReadySilent: synchronise l'icône play/pause
                        // dans la notification système SANS rappeler orchestrator.resume()
                        player.setPlayWhenReadySilent(true)
                        updateNotification(title, orchestrator.currentBookTitle, isPlaying = true)
                    }
                    is State.Paused -> {
                        // setPlayWhenReadySilent: synchronise l'état pour le lockscreen/Bluetooth
                        // sans rappeler orchestrator.pause()
                        player.setPlayWhenReadySilent(false)
                        updateNotification(
                            title = orchestrator.currentChapterTitle.ifEmpty { "ReadFlow" },
                            subtitle = "⏸ En pause",
                            isPlaying = false
                        )

                        // Programmer la sortie du mode foreground après le timeout
                        schedulePauseTimeout()
                    }
                    is State.Error -> {
                        player.setIdle()
                        cancelPauseTimeout()
                        updateNotification("Erreur", state.message, isPlaying = false)
                    }
                }
            }
        }

        // Observation de la progression (position dans le chapitre)
        progressJob = serviceScope.launch {
            orchestrator.playbackState.collect { pbs ->
                val player = readFlowPlayer ?: return@collect
                val posMs = pbs.activeSentenceIndex * 5000L
                val totalMs = pbs.totalSentences * 5000L
                player.updateProgress(posMs, totalMs)
            }
        }
    }

    private fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
        progressJob?.cancel()
        progressJob = null
        cancelPauseTimeout()
    }

    // ── Gestion du cycle foreground / pause prolongée ─

    /**
     * Programme la sortie du mode foreground après [PAUSE_FOREGROUND_TIMEOUT_MS].
     *
     * Objectif : ne pas maintenir indéfiniment une notification persistante
     * (et donc un wakelock implicite) quand la lecture est en pause prolongée.
     * Le service reste vivant en arrière-plan pour une reprise rapide, mais
     * la notification est retirée via [stopForeground] avec le flag
     * [STOP_FOREGROUND_DETACH] (Android 8+) pour éviter le crash
     * "android.app.ForegroundServiceDidNotStartInTimeException".
     */
    private fun schedulePauseTimeout() {
        cancelPauseTimeout()
        pauseTimeoutJob = serviceScope.launch {
            delay(PAUSE_FOREGROUND_TIMEOUT_MS)
            Log.d(TAG, "Pause prolongée → sortie du mode foreground")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                isInForeground = false
            } catch (e: Exception) {
                Log.e(TAG, "Erreur stopForeground: ${e.message}", e)
            }
        }
    }

    private fun cancelPauseTimeout() {
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
    }

    /**
     * Replace le service en mode foreground si nécessaire.
     *
     * Appelé quand la lecture reprend après une pause prolongée
     * (où [stopForeground] a été appelé). La notification est
     * recréée et le service repasse en foreground.
     */
    private fun ensureForeground() {
        if (isInForeground) return
        val title = orchestrator.currentChapterTitle.ifEmpty {
            orchestrator.currentBookTitle.ifEmpty { "ReadFlow" }
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification(
                title = title,
                subtitle = orchestrator.currentBookTitle,
                isPlaying = true
            ))
            isInForeground = true
            Log.d(TAG, "Retour en mode foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Échec retour foreground: ${e.message}", e)
        }
    }

    // ── Construction de la notification ──────────────

    private fun updateNotification(title: String, subtitle: String, isPlaying: Boolean) {
        try {
            val notif = buildNotification(title, subtitle, isPlaying)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Échec mise à jour notification: ${e.message}", e)
        }
    }

    /**
     * Construit une notification MediaStyle compatible Android 12/13+.
     *
     * Points clés :
     * - [Notification.MediaStyle] pour l'affichage compact étendu.
     * - Actions Pause/Play/Stop avec [PendingIntent] pointant vers ce service.
     * - [setOngoing] = true quand en lecture (ne peut pas être swipe-dismissed).
     * - [VISIBILITY_PUBLIC] pour l'affichage sur l'écran de verrouillage.
     */
    private fun buildNotification(
        title: String,
        subtitle: String,
        isPlaying: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setStyle(Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (isPlaying) {
            // Mode lecture : Prev | Pause | Next
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Précédent",
                    PendingIntent.getService(this, 3,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PREV), flag)
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause, "Pause",
                    PendingIntent.getService(this, 1,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PAUSE), flag)
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Suivant",
                    PendingIntent.getService(this, 4,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_NEXT), flag)
                ).build()
            )
        } else {
            // Mode pause/idle : Prev | Play | Next
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Précédent",
                    PendingIntent.getService(this, 3,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PREV), flag)
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_play, "Lire",
                    PendingIntent.getService(this, 1,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PLAY), flag)
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Suivant",
                    PendingIntent.getService(this, 4,
                        Intent(this, AudioPlaybackService::class.java).setAction(ACTION_NEXT), flag)
                ).build()
            )
        }

        return builder.build()
    }

    // ── Canal de notification ────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Contrôles de lecture TTS ReadFlow"
                setShowBadge(false)
                // Pas de son de notification (le TTS produit déjà l'audio)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}


