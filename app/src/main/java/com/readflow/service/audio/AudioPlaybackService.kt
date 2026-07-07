package com.readflow.service.audio

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Service de lecture audio en arrière-plan.
 * Sera pleinement implémenté en Phase 2 avec AudioTrack gapless.
 * Actuellement : squelette minimum pour compilation.
 */
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Phase 2 : Remplacer ExoPlayer par AudioTrack gapless
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        player.prepare()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
