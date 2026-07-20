package com.inktone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.inktone.data.settings.AppTheme
import com.inktone.data.settings.SettingsRepository
import com.inktone.ui.navigation.InkToneNavGraph
import com.inktone.ui.screen.onboarding.OnboardingScreen
import com.inktone.ui.theme.InkToneTheme
import com.inktone.ui.theme.LocalWindowSizeClass
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerfLogger.markFirstFrame("MainActivity")
            val appTheme by settingsRepository.theme.collectAsStateWithLifecycle(initialValue = AppTheme.PAPIER_ART)
            val dynamicColors by settingsRepository.dynamicColors.collectAsStateWithLifecycle(initialValue = false)
            val isFirstLaunch by settingsRepository.isFirstLaunch.collectAsStateWithLifecycle(initialValue = true)
            val scope = rememberCoroutineScope()
            // Calculée une seule fois ici, propagée par CompositionLocal — voir
            // PLAN_ACTION_TOP_TIER_CLAUDECODE.md §3.1. Se recalcule automatiquement à chaque
            // recomposition déclenchée par un changement de configuration (rotation, pliage).
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                InkToneTheme(theme = appTheme, dynamicColors = dynamicColors) {
                    if (isFirstLaunch) {
                        OnboardingScreen(
                            onComplete = { scope.launch { settingsRepository.setOnboardingComplete() } }
                        )
                    } else {
                        InkToneNavGraph()
                    }
                }
            }
        }
    }
}
