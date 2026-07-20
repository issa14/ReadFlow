package com.inktone.ui.theme

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * Classe de taille d'écran calculée une seule fois au niveau de `MainActivity` et propagée par
 * `CompositionLocal` — évite que chaque écran la recalcule localement, ce qui recréerait
 * l'incohérence entre bibliothèque et lecteur que cette tâche corrige (voir
 * PLAN_ACTION_TOP_TIER_CLAUDECODE.md §3.1).
 *
 * Pas de valeur par défaut significative : lire ce `CompositionLocal` sans qu'il ait été fourni
 * par `MainActivity` est une erreur de configuration à faire échouer bruyamment, pas un cas
 * silencieux à masquer par une valeur de repli arbitraire.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass non fourni — doit être défini au niveau de MainActivity via CompositionLocalProvider")
}
