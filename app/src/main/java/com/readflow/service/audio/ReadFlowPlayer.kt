package com.readflow.service.audio

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Adaptateur entre le [PlaybackOrchestrator] et l'API [Player] de Media3.
 *
 * Permet d'utiliser le pipeline audio ReadFlow (ONNX → AudioTrack) comme un
 * [Player] standard pour [androidx.media3.session.MediaSessionService].
 * Toutes les commandes (play/pause/stop/seek) sont déléguées au
 * [PlaybackOrchestrator].
 *
 * Architecture :
 * - Hérite de [SimpleBasePlayer] qui gère automatiquement les listeners et
 *   la boucle de notification d'état.
 * - Chaque méthode `handle*` retourne un `ListenableFuture` immédiatement
 *   résolu car toutes nos opérations sont synchrones côté orchestrateur.
 * - Les commandes `COMMAND_SEEK_TO_NEXT` et `COMMAND_SEEK_TO_PREVIOUS`
 *   sont exposées pour le support des boutons Bluetooth/écouteurs.
 *
 * Thread safety : toutes les variables mutables sont `@Volatile` et les
 * méthodes `handle*` sont appelées sur le thread du Looper fourni.
 */
class ReadFlowPlayer(
    private val orchestrator: PlaybackOrchestrator
) : SimpleBasePlayer(Looper.getMainLooper()) {

    // ── État interne ─────────────────────────────────

    @Volatile private var playWhenReady = false
    @Volatile private var playbackState = Player.STATE_IDLE
    @Volatile private var mediaItems: List<MediaItem> = emptyList()
    @Volatile private var positionMs = 0L
    @Volatile private var totalDurationMs = 0L
    @Volatile private var released = false

    // ── API publique pour AudioPlaybackService ───────

    /** Configure le contenu en cours de lecture (titre, auteur). */
    fun setContent(title: String, artist: String = "", durationMs: Long = 0L) {
        if (released) return
        totalDurationMs = durationMs
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
        mediaItems = listOf(
            MediaItem.Builder()
                .setMediaId("chapter")
                .setMediaMetadata(metadata)
                .build()
        )
        positionMs = 0L
        invalidateState()
    }

    /** Met à jour la position estimée et la durée totale. */
    fun updateProgress(position: Long, duration: Long) {
        if (released) return
        positionMs = position
        totalDurationMs = duration
        invalidateState()
    }

    /** Passe en état [Player.STATE_READY]. */
    fun setReady(ready: Boolean) {
        if (released) return
        if (ready && playbackState != Player.STATE_READY) {
            playbackState = Player.STATE_READY
            invalidateState()
        }
    }

    /** Passe en état [Player.STATE_ENDED]. */
    fun setEnded() {
        if (released) return
        playWhenReady = false
        playbackState = Player.STATE_ENDED
        invalidateState()
    }

    /** Passe en état [Player.STATE_IDLE]. */
    fun setIdle() {
        if (released) return
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        positionMs = 0L
        invalidateState()
    }

    /** Rafraîchit l'état exposé à MediaSession. */
    fun refreshState() {
        if (released) return
        invalidateState()
    }

    /**
     * Met à jour [playWhenReady] SANS déclencher [handleSetPlayWhenReady].
     *
     * Utile pour synchroniser l'état du Player avec un changement
     * déjà effectué côté [PlaybackOrchestrator] (ex: depuis la notification).
     * Évite l'appel redondant à [orchestrator.resume] ou [orchestrator.pause].
     */
    fun setPlayWhenReadySilent(pwr: Boolean) {
        if (released) return
        this.playWhenReady = pwr
        invalidateState()
    }

    /**
     * Nettoie l'état interne. Appelé avant ou après [SimpleBasePlayer.release].
     *
     * [SimpleBasePlayer.release] est `final` — on ne peut pas l'override.
     * L'appelant ([AudioPlaybackService.onDestroy]) doit appeler
     * `readFlowPlayer?.release()` (Media3) suivi de cette méthode si nécessaire.
     */
    fun cleanup() {
        released = true
        playWhenReady = false
        playbackState = Player.STATE_IDLE
    }

    // ── SimpleBasePlayer overrides ────────────────────

    override fun getState(): State {
        // Sécurité : playlist vide → forcer IDLE (évite crash SimpleBasePlayer)
        if (mediaItems.isEmpty() &&
            playbackState != Player.STATE_IDLE &&
            playbackState != Player.STATE_ENDED
        ) {
            playbackState = Player.STATE_IDLE
        }

        val playlistData = mediaItems.map { item ->
            getPlaceholderMediaItemData(item)
        }

        return State.Builder()
            .setPlaylist(playlistData)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.COMMAND_PLAY_PAUSE)
            .setContentPositionMs(positionMs)
            .setTotalBufferedDurationMs { totalDurationMs }
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM
                    )
                    .build()
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (released) return Futures.immediateFuture(Unit)

        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            if (playbackState == Player.STATE_IDLE) {
                playbackState = Player.STATE_READY
            }
            orchestrator.resume()
        } else {
            orchestrator.pause()
        }
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (released) return Futures.immediateFuture(Unit)
        playbackState = Player.STATE_READY
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleStop(): ListenableFuture<*> {
        if (released) return Futures.immediateFuture(Unit)
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        orchestrator.stop()
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        // Le TTS phrase par phrase ne supporte pas le seek précis.
        // On accepte silencieusement la position et on met à jour l'état.
        this.positionMs = positionMs
        invalidateState()
        return Futures.immediateFuture(Unit)
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        if (released) return Futures.immediateFuture(Unit)
        this.mediaItems = mediaItems.toList()
        this.positionMs = startPositionMs
        playbackState = Player.STATE_READY
        invalidateState()
        return Futures.immediateFuture(Unit)
    }
}


