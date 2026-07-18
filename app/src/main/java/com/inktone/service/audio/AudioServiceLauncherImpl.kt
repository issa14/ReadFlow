package com.inktone.service.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.inktone.domain.service.AudioServiceLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation Android de [AudioServiceLauncher].
 *
 * Seule classe autorisée à manipuler [Context], [Intent] et les permissions
 * Android pour le lancement du service audio. Injectée dans le ViewModel
 * via l'interface, ce qui rend le ViewModel testable unitairement.
 */
@Singleton
class AudioServiceLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioServiceLauncher {

    override fun canStart(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun start() {
        val intent = Intent(context, AudioPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
}
