package com.inktone.ui.screen.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnifiedControlPanel(
    isPlaying: Boolean,
    accentColor: Color,
    panelBg: Color,
    useOpenDyslexic: Boolean = false,
    onTtsClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    onThemeCycle: () -> Unit,
    onFontToggle: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelBg.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── Rangée 1 : Outils rapides ────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause TTS
                IconButton(onClick = onTtsClick) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "TTS", tint = accentColor, modifier = Modifier.size(26.dp)
                    )
                }
                // Options TTS (Headphones)
                IconButton(onClick = onTtsSettingsClick) {
                    Icon(
                        Icons.Outlined.Headphones,
                        "Options TTS", tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                // Thème
                IconButton(onClick = onThemeCycle) {
                    Icon(Icons.Default.Palette, "Thème",
                        tint = Color.White.copy(alpha = 0.6f))
                }
                // OpenDyslexic Quick Toggle
                IconButton(
                    onClick = onFontToggle,
                    modifier = Modifier.semantics { contentDescription = "Police OpenDyslexic" }
                ) {
                    Text("D",
                        color = if (useOpenDyslexic) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                // Options d'affichage avancées
                IconButton(onClick = onDisplaySettingsClick) {
                    Icon(
                        Icons.Default.FormatSize,
                        "Options d'affichage", tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Rangée 2 : Navigation chapitres ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrevChapter) {
                    Text("◀ Chapitre précédent", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                Spacer(Modifier.width(24.dp))
                TextButton(onClick = onNextChapter) {
                    Text("Chapitre suivant ▶", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }
        }
    }
}
