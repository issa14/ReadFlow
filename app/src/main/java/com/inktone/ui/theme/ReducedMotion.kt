package com.inktone.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Retourne 0 si l'utilisateur a désactivé les animations dans les paramètres
 * système Android (Échelle d'animation = 0), sinon retourne [defaultMs].
 *
 * Usage :
 * ```
 * val duration = reducedMotionDuration(200)
 * AnimatedVisibility(visible = ..., enter = fadeIn(tween(duration)))
 * ```
 */
@Composable
fun reducedMotionDuration(defaultMs: Int): Int {
    val context = LocalContext.current
    val isReduced = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0.0f
        } catch (_: SecurityException) {
            false // Pas la permission, assume animations normales
        }
    }
    return if (isReduced) 0 else defaultMs
}
