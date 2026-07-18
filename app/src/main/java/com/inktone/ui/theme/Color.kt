package com.inktone.ui.theme

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
//  Couleurs statiques (conservées pour compatibilité)
//  Utiliser MaterialTheme.colorScheme.* de préférence
// ═══════════════════════════════════════════════════

val AppBackground = Color(0xFF0D0E15)         // deprecated
val SurfaceDark    = Color(0xFF161722)         // deprecated
val SurfaceRaised  = Color(0xFF202232)         // deprecated
val TextMain       = Color(0xFFE2E4ED)         // deprecated
val TextMuted      = Color(0xFF7A7E9D)         // deprecated
val AccentBlue     = Color(0xFF0091EA)         // deprecated
val AccentTts      = Color(0xFFFF79C6)
val BorderDark     = Color(0xFF222538)         // deprecated
val BorderSoft     = Color(0xFF2E324C)         // deprecated
val ShelfOverlay   = Color(0x12FFFFFF)

// ── Couvertures (gradients prédéfinis) ──
val CoverGradients = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    listOf(Color(0xFF373B44), Color(0xFF4286F4)),
    listOf(Color(0xFF870000), Color(0xFF190019)),
    listOf(Color(0xFF134E5E), Color(0xFF71B280)),
)
