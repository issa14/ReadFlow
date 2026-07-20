package com.inktone.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.ui.theme.ttsActive

@Composable
fun TtsPanel(
    chapterTitle: String,
    sentenceIndex: Int,
    totalSentences: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceChange: (Int) -> Unit,
    currentSpeed: Float,
    currentVoice: Int,
    sleepTimerRemaining: Int?,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Titre
        Text(chapterTitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Phrase ${sentenceIndex + 1} / $totalSentences",
            color = MaterialTheme.colorScheme.outlineVariant,
            style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(24.dp))

        // Contrôles lecture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, "Précédent",
                    tint = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(Modifier.width(24.dp))

            FilledIconButton(
                onClick = { if (isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.ttsActive
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lire",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.width(24.dp))

            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, "Suivant",
                    tint = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stop discret
        TextButton(onClick = onStop) {
            Text("⏹ Arrêter", color = MaterialTheme.colorScheme.outlineVariant,
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        // Vitesse
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vitesse", color = MaterialTheme.colorScheme.outlineVariant,
                style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.ttsActive,
                    activeTrackColor = MaterialTheme.colorScheme.ttsActive
                )
            )
            Text("${"%.1f".format(currentSpeed)}x",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))

        // Voix
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Voix", color = MaterialTheme.colorScheme.outlineVariant,
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = currentVoice == 0,
                onClick = { onVoiceChange(0) },
                label = { Text("Jessica", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.25f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = currentVoice == 1,
                onClick = { onVoiceChange(1) },
                label = { Text("Pierre", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.25f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        // Sleep Timer (Minuteur de mise en veille)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Timer, "Veille",
                    tint = MaterialTheme.colorScheme.ttsActive,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Minuteur de veille",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            if (sleepTimerRemaining == null) {
                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        InputChip(
                            selected = false,
                            onClick = { onStartSleepTimer(min) },
                            label = { Text("$min min", style = MaterialTheme.typography.labelSmall) },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.outlineVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else {
                // Countdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val minutes = sleepTimerRemaining / 60
                    val seconds = sleepTimerRemaining % 60
                    Text(
                        "Veille active : ${"%02d:%02d".format(minutes, seconds)}",
                        color = MaterialTheme.colorScheme.ttsActive,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = onCancelSleepTimer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close, "Annuler",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

    }
}
