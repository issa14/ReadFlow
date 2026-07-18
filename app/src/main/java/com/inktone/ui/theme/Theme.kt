package com.inktone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.inktone.data.settings.AppTheme

@Composable
fun InkToneTheme(
    theme: AppTheme = AppTheme.PAPIER_ART,
    content: @Composable () -> Unit
) {
    val (scheme, isLightBars) = when (theme) {
        AppTheme.PAPIER_ART -> PapierArtColors   to true
        AppTheme.OBSIDIAN   -> ObsidianColors    to false
        AppTheme.NORDIC_FOG -> NordicFogColors   to true
        AppTheme.SYSTEM     -> {
            if (isSystemInDarkTheme())
                ObsidianColors to false
            else
                PapierArtColors to true
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = isLightBars
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
