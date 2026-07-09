package com.readflow.service.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.readflow.MainActivity
import com.readflow.service.audio.PlaybackOrchestrator.State
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "AudioPlaybackSvc"
        private const val CHANNEL_ID = "readflow_playback"
        private const val CHANNEL_NAME = "Lecture TTS"
        private const val NOTIFICATION_ID = 1001

        // Actions des boutons de la notification
        private const val ACTION_PAUSE = "com.readflow.action.PAUSE"
        private const val ACTION_PLAY = "com.readflow.action.PLAY"
        private const val ACTION_STOP = "com.readflow.action.STOP"
    }

    @Inject lateinit var orchestrator: PlaybackOrchestrator
    @Inject lateinit var audioFocusManager: AudioFocusManager

    private var mediaSession: MediaSession? = null
    private var readFlowPlayer: ReadFlowPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observationJob: Job? = null

    // ── Lifecycle ────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

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

        // Notification initiale (satisfait le contrat foreground 5s)
        startForeground(NOTIFICATION_ID, buildNotification("Prêt", "Lecture TTS", isPlaying = false))

        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Traiter les commandes des boutons de la notification
        when (intent?.action) {
            ACTION_PAUSE -> { Log.d(TAG, "Bouton Pause"); orchestrator.pause() }
            ACTION_PLAY  -> { Log.d(TAG, "Bouton Play"); orchestrator.resume() }
            ACTION_STOP  -> { Log.d(TAG, "Bouton Stop"); orchestrator.stop() }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (orchestrator.state.value !is State.Playing) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopObserving()
        serviceScope.cancel()
        audioFocusManager.abandonFocus()
        mediaSession?.release()
        mediaSession = null
        readFlowPlayer = null
        super.onDestroy()
    }

    // ── Observation de l'orchestrateur ───────────────

    private fun startObserving() {
        observationJob = serviceScope.launch {
            orchestrator.state.collect { state ->
                val player = readFlowPlayer ?: return@collect
                when (state) {
                    is State.Idle -> {
                        player.setIdle()
                        updateNotification("Prêt", "Lecture TTS", isPlaying = false)
                    }
                    is State.Playing -> {
                        // Le focus audio est géré par PlaybackOrchestrator
                        val title = orchestrator.currentChapterTitle.ifEmpty {
                            orchestrator.currentBookTitle.ifEmpty { "ReadFlow" }
                        }
                        player.setContent(title, orchestrator.currentBookTitle)
                        player.setReady(true)
                        player.setPlayWhenReady(true)
                        updateNotification(title, orchestrator.currentBookTitle, isPlaying = true)
                    }
                    is State.Paused -> {
                        player.setPlayWhenReady(false)
                        player.refreshState()
                        updateNotification(
                            title = orchestrator.currentChapterTitle.ifEmpty { "ReadFlow" },
                            subtitle = "⏸ En pause",
                            isPlaying = false
                        )
                    }
                    is State.Error -> {
                        player.setIdle()
                        updateNotification("Erreur", state.message, isPlaying = false)
                    }
                }
            }
        }

        serviceScope.launch {
            orchestrator.progress.collect { progress ->
                val player = readFlowPlayer ?: return@collect
                val posMs = progress.sentenceIndex * 5000L
                val totalMs = progress.totalSentences * 5000L
                player.updateProgress(posMs, totalMs)
            }
        }
    }

    private fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
    }

    // ── Construction de la notification ──────────────

    private fun updateNotification(title: String, subtitle: String, isPlaying: Boolean) {
        val notif = buildNotification(title, subtitle, isPlaying)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notif)
    }

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
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0))
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (isPlaying) {
            // Mode lecture : bouton Pause + Stop
            builder.addAction(
                android.R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(this, 1,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PAUSE), flag)
            )
            builder.addAction(
                android.R.drawable.ic_media_play, "Stop",
                PendingIntent.getService(this, 2,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP), flag)
            )
        } else {
            // Mode pause/idle : bouton Play + Stop
            builder.addAction(
                android.R.drawable.ic_media_play, "Lire",
                PendingIntent.getService(this, 1,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PLAY), flag)
            )
            builder.addAction(
                android.R.drawable.ic_media_play, "Stop",
                PendingIntent.getService(this, 2,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP), flag)
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
                description = "Notification de lecture TTS en cours"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

