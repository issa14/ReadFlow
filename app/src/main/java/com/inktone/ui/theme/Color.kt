package com.inktone.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════
//  Thème 1 — Papier d'Art (défaut, chaud & doux)
// ═══════════════════════════════════════════════════
val PapierArtColors = lightColorScheme(
    primary            = Color(0xFF8C6239),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1800),
    secondary          = Color(0xFFC15C3D),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3F0C00),
    background         = Color(0xFFFBF9F6),
    onBackground       = Color(0xFF2C2520),
    surface            = Color(0xFFF3ECE0),
    onSurface          = Color(0xFF2C2520),
    surfaceVariant     = Color(0xFFEBE0D0),
    onSurfaceVariant   = Color(0xFF6E6458),
    outline            = Color(0xFFD0C5B5),
    outlineVariant     = Color(0xFFE5DAC8),
    error              = Color(0xFFBA1A1A),
)

// ═══════════════════════════════════════════════════
//  Thème 2 — Obsidian Noir (sombre profond & moderne)
// ═══════════════════════════════════════════════════
val ObsidianColors = darkColorScheme(
    primary            = Color(0xFF90A4AE),
    onPrimary          = Color(0xFF1A2328),
    primaryContainer   = Color(0xFF2E3A42),
    onPrimaryContainer = Color(0xFFB0C4CE),
    secondary          = Color(0xFF0091EA),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFF003258),
    onSecondaryContainer = Color(0xFFCCE5FF),
    background         = Color(0xFF0F1014),
    onBackground       = Color(0xFFE1E2E7),
    surface            = Color(0xFF16181F),
    onSurface          = Color(0xFFE1E2E7),
    surfaceVariant     = Color(0xFF232733),
    onSurfaceVariant   = Color(0xFF7A7E9D),
    outline            = Color(0xFF3A3E4C),
    outlineVariant     = Color(0xFF2A2D3A),
    error              = Color(0xFFFF6B6B),
)

// ═══════════════════════════════════════════════════
//  Thème 3 — Brouillard Nordique (frais & minimaliste)
// ═══════════════════════════════════════════════════
val NordicFogColors = lightColorScheme(
    primary            = Color(0xFF4A6572),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFCEE5F0),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary          = Color(0xFF689F38),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFD7F5C0),
    onSecondaryContainer = Color(0xFF102200),
    background         = Color(0xFFF0F2F5),
    onBackground       = Color(0xFF232F34),
    surface            = Color(0xFFE4E7EB),
    onSurface          = Color(0xFF232F34),
    surfaceVariant     = Color(0xFFD1D5DB),
    onSurfaceVariant   = Color(0xFF5C6B73),
    outline            = Color(0xFFB8BEC5),
    outlineVariant     = Color(0xFFD8DCE2),
    error              = Color(0xFFBA1A1A),
)

// ═══════════════════════════════════════════════════
//  Thème 4 — Signature (bleu lecture + orange TTS)
//  Clair : fond ivoire chaud, bleu vibrant, orange énergique
// ═══════════════════════════════════════════════════
val SignatureLightColors = lightColorScheme(
    primary            = Color(0xFF0066FF),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary          = Color(0xFFC04000),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3A0A00),
    tertiary           = Color(0xFF006B5E),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFF7FF8E3),
    onTertiaryContainer = Color(0xFF00201B),
    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),
    background         = Color(0xFFFFFBF5),
    onBackground       = Color(0xFF1B1B1F),
    surface            = Color(0xFFF8F4EC),
    onSurface          = Color(0xFF1B1B1F),
    surfaceVariant     = Color(0xFFE1E2EC),
    onSurfaceVariant   = Color(0xFF44464F),
    outline            = Color(0xFF74777F),
    outlineVariant     = Color(0xFFC4C6D0),
    inverseSurface     = Color(0xFF303033),
    inverseOnSurface   = Color(0xFFF2F0F4),
    inversePrimary     = Color(0xFFAAC7FF),
    scrim              = Color.Black,
)

// ═══════════════════════════════════════════════════
//  Thème 4b — Signature Sombre (OLED-optimized)
//  Fond ultra-noir, bleu adouci, orange chaud
// ═══════════════════════════════════════════════════
val SignatureDarkColors = darkColorScheme(
    primary            = Color(0xFF3399FF),
    onPrimary          = Color(0xFF001D3A),
    primaryContainer   = Color(0xFF004A9C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary          = Color(0xFFFF8C5A),
    onSecondary        = Color(0xFF3D0D00),
    secondaryContainer = Color(0xFF5C1A00),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary           = Color(0xFF4DDBBF),
    onTertiary         = Color(0xFF00382F),
    tertiaryContainer  = Color(0xFF005045),
    onTertiaryContainer = Color(0xFF7FF8E3),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
    errorContainer     = Color(0xFF93000A),
    onErrorContainer   = Color(0xFFFFDAD6),
    background         = Color(0xFF0F1419),
    onBackground       = Color(0xFFE2E2E6),
    surface            = Color(0xFF1A202C),
    onSurface          = Color(0xFFE2E2E6),
    surfaceVariant     = Color(0xFF44464F),
    onSurfaceVariant   = Color(0xFFC4C6D0),
    outline            = Color(0xFF8E9099),
    outlineVariant     = Color(0xFF44464F),
    inverseSurface     = Color(0xFFE2E2E6),
    inverseOnSurface   = Color(0xFF1B1B1F),
    inversePrimary     = Color(0xFF0066FF),
    scrim              = Color.Black,
)

// ═══════════════════════════════════════════════════
//  Tokens sémantiques — extensions sur ColorScheme
//  Usage : MaterialTheme.colorScheme.ttsActive
// ═══════════════════════════════════════════════════

/** Couleur associée au TTS actif (bouton, badge, indicateur). */
val ColorScheme.ttsActive: Color get() = this.secondary

/** Couleur de succès / confirmation (import, sauvegarde). */
val ColorScheme.success: Color get() = this.tertiary

/** Couleur de fond pour les cartes en surélévation légère. */
val ColorScheme.cardBackground: Color get() = this.surfaceVariant

// ═══════════════════════════════════════════════════
//  Couleurs statiques (conservées pour compatibilité)
//  Utiliser MaterialTheme.colorScheme.* de préférence
// ═══════════════════════════════════════════════════

@Deprecated("Utiliser MaterialTheme.colorScheme.background")
val AppBackground = Color(0xFF0D0E15)
@Deprecated("Utiliser MaterialTheme.colorScheme.surface")
val SurfaceDark    = Color(0xFF161722)
@Deprecated("Utiliser MaterialTheme.colorScheme.surfaceVariant")
val SurfaceRaised  = Color(0xFF202232)
@Deprecated("Utiliser MaterialTheme.colorScheme.onBackground")
val TextMain       = Color(0xFFE2E4ED)
@Deprecated("Utiliser MaterialTheme.colorScheme.onSurfaceVariant")
val TextMuted      = Color(0xFF7A7E9D)
@Deprecated("Utiliser MaterialTheme.colorScheme.primary ou secondary selon le thème")
val AccentBlue     = Color(0xFF0091EA)
@Deprecated("Utiliser MaterialTheme.colorScheme.ttsActive")
val AccentTts      = Color(0xFFFF79C6)
@Deprecated("Utiliser MaterialTheme.colorScheme.outline")
val BorderDark     = Color(0xFF222538)
@Deprecated("Utiliser MaterialTheme.colorScheme.outlineVariant")
val BorderSoft     = Color(0xFF2E324C)
@Deprecated("Utiliser MaterialTheme.colorScheme.surface.copy(alpha = 0.07f)")
val ShelfOverlay   = Color(0x12FFFFFF)

// ── Couvertures (gradients prédéfinis) ──
val CoverGradients = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF373B44), Color(0xFF4286F4)),
    listOf(Color(0xFF870000), Color(0xFF190019)),
    listOf(Color(0xFF134E5E), Color(0xFF71B280)),
    listOf(Color(0xFF004A9C), Color(0xFF0066FF), Color(0xFF3399FF)),
    listOf(Color(0xFF5C1A00), Color(0xFFFF6B35), Color(0xFFFF8C5A)),
)
