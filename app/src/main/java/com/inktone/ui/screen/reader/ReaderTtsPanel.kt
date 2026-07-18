package com.inktone.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.data.database.entity.PronunciationRule
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
    onCancelSleepTimer: () -> Unit,
    pronunciationRules: List<PronunciationRule>,
    onAddPronunciationRule: (String, String, Boolean) -> Unit,
    onDeletePronunciationRule: (PronunciationRule) -> Unit,
    onTogglePronunciationRule: (PronunciationRule) -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var isRulesExpanded by remember { mutableStateOf(false) }

    if (showAddRuleDialog) {
        var originalText by remember { mutableStateOf("") }
        var replacementText by remember { mutableStateOf("") }
        var isRegexChecked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Nouvelle règle de prononciation", color = Color.White) },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            text = {
                Column {
                    OutlinedTextField(
                        value = originalText,
                        onValueChange = { originalText = it },
                        label = { Text("Texte d'origine") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.ttsActive,
                            focusedLabelColor = MaterialTheme.colorScheme.ttsActive
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        label = { Text("Prononciation de remplacement") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.ttsActive,
                            focusedLabelColor = MaterialTheme.colorScheme.ttsActive
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isRegexChecked,
                            onCheckedChange = { isRegexChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.ttsActive)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Expression régulière (Regex)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (originalText.isNotBlank() && replacementText.isNotBlank()) {
                            onAddPronunciationRule(originalText, replacementText, isRegexChecked)
                        }
                        showAddRuleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.ttsActive)
                ) {
                    Text("Enregistrer", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

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
                label = { Text("Miro FR", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.25f),
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        // 💎 Sleep Timer (Minuteur de mise en veille)
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        // 💎 Custom Pronunciation Dictionary
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isRulesExpanded = !isRulesExpanded }
                ) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver, "Dictionnaire",
                        tint = MaterialTheme.colorScheme.ttsActive,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Dictionnaire phonétique (${pronunciationRules.size})",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { showAddRuleDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.Add, "Ajouter",
                        tint = MaterialTheme.colorScheme.ttsActive,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isRulesExpanded || pronunciationRules.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                if (pronunciationRules.isEmpty()) {
                    Text(
                        "Aucune règle personnalisée. Corrigez la prononciation des mots en cliquant sur +.",
                        color = MaterialTheme.colorScheme.outlineVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pronunciationRules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            rule.pattern,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (rule.isRegex) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "regex",
                                                color = MaterialTheme.colorScheme.ttsActive,
                                                fontSize = 9.sp,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "➔ ${rule.replacement}",
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.isActive,
                                        onCheckedChange = { onTogglePronunciationRule(rule) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.ttsActive,
                                            checkedTrackColor = MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                                    IconButton(
                                        onClick = { onDeletePronunciationRule(rule) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete, "Supprimer",
                                            tint = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
