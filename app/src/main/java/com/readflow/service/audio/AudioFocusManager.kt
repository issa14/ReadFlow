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

interface AudioFocusListener {
    fun onFocusGained()
    fun onFocusLost(isPermanent: Boolean)
    fun onAudioBecomingNoisy()
    fun onDuck()
    fun onUnduck()
}

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

    private var focusRequest: AudioFocusRequest? = null
    private var isDucked = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY → pause")
                listener?.onAudioBecomingNoisy()
            }
        }
    }

    private var noisyReceiverRegistered = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        Log.d(TAG, "Focus change: ${focusChangeToString(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucked) {
                    isDucked = false
                    listener?.onUnduck()
                } else {
                    listener?.onFocusGained()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                unregisterNoisyReceiver()
                listener?.onFocusLost(isPermanent = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                listener?.onFocusLost(isPermanent = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                listener?.onDuck()
            }
        }
    }

    fun setListener(newListener: AudioFocusListener?) {
        listener = newListener
    }

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

    fun abandonFocus() {
        unregisterNoisyReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        isDucked = false
        Log.d(TAG, "Focus abandonné")
    }

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
