package com.readflow.service.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback pour les changements de focus audio Android.
 * Implémenté par [PlaybackOrchestrator] pour réagir
 * aux événements système (appels, notifications, débranchement casque).
 */
interface AudioFocusListener {
    /** Le focus audio est regagné : la lecture peut reprendre. */
    fun onFocusGained()

    /**
     * Le focus audio est perdu.
     * @param isPermanent true si perte définitive (appel entrant, autre app média).
     *                    false si perte temporaire (notification courte, ducking).
     */
    fun onFocusLost(isPermanent: Boolean)

    /** L'utilisateur a débranché ses écouteurs ou le Bluetooth s'est déconnecté. */
    fun onAudioBecomingNoisy()
}

/**
 * Gère le focus audio Android pour ReadFlow.
 *
 * Délègue les décisions de pause/reprise via [AudioFocusListener].
 * S'abonne au broadcast [AudioManager.ACTION_AUDIO_BECOMING_NOISY]
 * pour détecter le débranchement des écouteurs.
 *
 * Usage :
 * 1. Enregistrer un listener via [setListener]
 * 2. Appeler [requestFocus] avant de lancer la lecture
 * 3. Appeler [abandonFocus] quand la lecture est terminée
 */

@Singleton
class AudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioFocus"
    }

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var listener: AudioFocusListener? = null

    // ── AudioFocusRequest (API ≥ 26, réutilisé pour abandon) ──
    private var focusRequest: AudioFocusRequest? = null

    // ── BroadcastReceiver pour ACTION_AUDIO_BECOMING_NOISY ──
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY → pause")
                listener?.onAudioBecomingNoisy()
            }
        }
    }

    private var noisyReceiverRegistered = false

    // ── Listener interne de focus ──
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.d(TAG, "Focus change: ${focusChangeToString(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                listener?.onFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                unregisterNoisyReceiver()
                listener?.onFocusLost(isPermanent = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                listener?.onFocusLost(isPermanent = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Le ducking (baisse de volume) n'est pas adapté pour
                // du texte parlé → on traite comme une perte temporaire.
                listener?.onFocusLost(isPermanent = false)
            }
        }
    }

    // ── API publique ──────────────────────────────────

    /** Enregistre le listener qui recevra les callbacks de focus. */
    fun setListener(newListener: AudioFocusListener?) {
        listener = newListener
    }

    /**
     * Demande le focus audio permanent.
     * @return true si le focus a été accordé, false sinon.
     */
    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Focus demandé → ${if (granted) "OK" else "REFUSÉ"}")
        if (granted) {
            registerNoisyReceiver()
        }
        return granted
    }

    /** Abandonne le focus audio. */
    fun abandonFocus() {
        unregisterNoisyReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        Log.d(TAG, "Focus abandonné")
    }

    // ── Private ───────────────────────────────────────

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                noisyReceiver, filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(noisyReceiver, filter)
        }
        noisyReceiverRegistered = true
        Log.d(TAG, "NoisyReceiver enregistré")
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (_: IllegalArgumentException) {
            // déjà désenregistré
        }
        noisyReceiverRegistered = false
        Log.d(TAG, "NoisyReceiver désenregistré")
    }

    private fun focusChangeToString(change: Int): String = when (change) {
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        else -> "UNKNOWN($change)"
    }
}
