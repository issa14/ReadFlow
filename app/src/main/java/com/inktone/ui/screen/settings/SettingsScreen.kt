package com.inktone.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.data.database.entity.PronunciationRule
import com.inktone.data.settings.AppTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pronunciationRules by viewModel.pronunciationRules.collectAsState()
    var showVoicePicker by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var showEdgeVoicePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showPathEditor by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // SAF launcher pour l'export (créer un fichier)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(it) }
    }

    // SAF launcher pour l'import (ouvrir un fichier)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackup(it) }
    }

    // Afficher le message de backup dans un snackbar
    LaunchedEffect(state.backupMessage) {
        state.backupMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBackupMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ═══════════════════════════════════════════
        //  PRÉSETS RAPIDES
        // ═══════════════════════════════════════════
        SectionGroup("Présets rapides", icon = com.inktone.ui.theme.AppIcons.Presets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                onClick = { viewModel.applyDarkModePreset() }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.DarkMode, "Mode sombre", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Mode sombre", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                onClick = { viewModel.applyAccessibilityPreset() }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Accessibility, "Accessibilité", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Accessibilité", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════
        //  LECTURE
        // ═══════════════════════════════════════════
        SectionGroup("Lecture", icon = com.inktone.ui.theme.AppIcons.Reading)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Voix
                SettingRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voix active",
                    subtitle = state.voice,
                    onClick = { showVoicePicker = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                // Vitesse
                SliderSetting(
                    icon = Icons.Default.Speed,
                    title = "Vitesse d'élocution",
                    value = state.speed,
                    valueRange = 0.5f..2.0f,
                    format = { "${"%.1f".format(it)}x" },
                    onValueChange = { viewModel.setSpeed(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                // Gain
                SliderSetting(
                    icon = Icons.Default.VolumeUp,
                    title = "Gain audio",
                    value = state.gain,
                    valueRange = 1.0f..4.0f,
                    format = { "${"%.1f".format(it)}x" },
                    onValueChange = { viewModel.setGain(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                // Moteur TTS
                val currentEngine = state.availableEngines.find { it.id == state.selectedEngine }
                SettingRow(
                    icon = Icons.Default.Memory,
                    title = "Moteur de synthèse",
                    subtitle = currentEngine?.label ?: state.selectedEngine,
                    onClick = { showEnginePicker = true }
                )

                // Si Edge sélectionné → sélecteur de voix
                if (state.selectedEngine == "edge") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    val edgeVoiceLabel = when (state.selectedEdgeVoice) {
                        "fr-FR-VivienneNeural" -> "Vivienne (FR)"
                        "fr-FR-HenriNeural"    -> "Henri (FR)"
                        else                   -> state.selectedEdgeVoice
                    }
                    SettingRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Voix Edge",
                        subtitle = edgeVoiceLabel,
                        onClick = { showEdgeVoicePicker = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════
        //  APPAREIL
        // ═══════════════════════════════════════════
        SectionGroup("Appareil", icon = com.inktone.ui.theme.AppIcons.Device)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {

                SectionHeader("Apparence", icon = com.inktone.ui.theme.AppIcons.Appearance)
                SettingRow(
                    icon = Icons.Default.Palette,
                    title = "Thème",
                    subtitle = state.theme.label,
                    onClick = { showThemePicker = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                SwitchSetting(
                    icon = Icons.Default.ColorLens,
                    title = "Couleurs dynamiques",
                    subtitle = "Palette générée depuis le fond d'écran (Android 12+)",
                    checked = state.dynamicColors,
                    onCheckedChange = { viewModel.setDynamicColors(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                SectionHeader("Accessibilité", icon = com.inktone.ui.theme.AppIcons.Accessibility)
                SwitchSetting(
                    icon = Icons.Default.Animation,
                    title = "Réduire les animations",
                    subtitle = "Désactiver les transitions animées",
                    checked = state.reduceMotion,
                    onCheckedChange = { viewModel.setReduceMotion(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                SwitchSetting(
                    icon = Icons.Default.TextFields,
                    title = "Police adaptée au système",
                    subtitle = "Utiliser la taille de police Android",
                    checked = state.respectSystemFontScale,
                    onCheckedChange = { viewModel.setRespectSystemFontScale(it) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════
        //  DONNÉES
        // ═══════════════════════════════════════════
        SectionGroup("Données", icon = com.inktone.ui.theme.AppIcons.Data)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingRow(
                    icon = Icons.Default.Folder,
                    title = "Dossier des modèles",
                    subtitle = state.modelPath.ifBlank { "Chemin par défaut" },
                    onClick = { showPathEditor = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                SettingRow(
                    icon = Icons.Default.SaveAlt,
                    title = "Exporter les données",
                    subtitle = "Sauvegarder progression, signets et réglages",
                    onClick = { exportLauncher.launch("inktone_backup_${System.currentTimeMillis()}.json") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                SettingRow(
                    icon = Icons.Default.FileOpen,
                    title = "Importer une sauvegarde",
                    subtitle = "Restaurer depuis un fichier .json",
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════
        //  PRONONCIATION
        // ═══════════════════════════════════════════
        SectionGroup("Prononciation", icon = com.inktone.ui.theme.AppIcons.Pronunciation)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                PronunciationDictionary(
                    pronunciationRules = pronunciationRules,
                    onAddPronunciationRule = { orig, rep, reg -> viewModel.addPronunciationRule(orig, rep, reg) },
                    onDeletePronunciationRule = { viewModel.deletePronunciationRule(it) },
                    onTogglePronunciationRule = { viewModel.togglePronunciationRule(it) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Dialogues ──
        if (showVoicePicker) {
            VoicePickerDialog(state.availableVoices, state.voice) { viewModel.setVoice(it); showVoicePicker = false }
        }
        if (showEnginePicker) {
            EnginePickerDialog(state.availableEngines, state.selectedEngine) { viewModel.setEngine(it); showEnginePicker = false }
        }
        if (showEdgeVoicePicker) {
            VoicePickerDialog(state.availableEdgeVoices, state.selectedEdgeVoice) { viewModel.setEdgeVoice(it); showEdgeVoicePicker = false }
        }
        if (showThemePicker) {
            ThemePickerDialog(state.theme) { viewModel.setTheme(it); showThemePicker = false }
        }
        if (showPathEditor) {
            PathEditDialog(state.modelPath, { showPathEditor = false }) { viewModel.setModelPath(it); showPathEditor = false }
        }
    }
    } // Close Column + Scaffold
}

// ─────────────────────────────────────────────────────
//  COMPOSANTS RÉUTILISABLES
// ─────────────────────────────────────────────────────

@Composable
private fun SectionGroup(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, "Ouvrir", tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SliderSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(format(value), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SwitchSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun VoicePickerDialog(voices: List<String>, selected: String, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Voix TTS", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column { voices.forEach { voice ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(voice) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = voice == selected, onClick = { onSelect(voice) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(8.dp))
                    Text(voice, color = MaterialTheme.colorScheme.onSurface)
                }
            }}
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Fermer", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
private fun EnginePickerDialog(engines: List<EngineInfo>, selected: String, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Moteur TTS", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column { engines.forEach { engine ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(engine.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = engine.id == selected, onClick = { onSelect(engine.id) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(engine.label, color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (engine.isAvailable) com.inktone.ui.theme.AppIcons.SuccessOutlined else com.inktone.ui.theme.AppIcons.WarningOutlined,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (engine.isAvailable) "Disponible" else "Indisponible",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }}
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Fermer", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
private fun ThemePickerDialog(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val options = AppTheme.entries.map { it to it.label }
    AlertDialog(
        onDismissRequest = { onSelect(selected) },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Thème", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column { options.forEach { (theme, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(theme) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = theme == selected, onClick = { onSelect(theme) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = MaterialTheme.colorScheme.onSurface)
                }
            }}
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Fermer", color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
private fun PathEditDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Dossier des modèles", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Chemin absolu") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Enregistrer", color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}

// ─────────────────────────────────────────────────────
//  DICTIONNAIRE DE PRONONCIATION
// ─────────────────────────────────────────────────────

@Composable
private fun PronunciationDictionary(
    pronunciationRules: List<PronunciationRule>,
    onAddPronunciationRule: (String, String, Boolean) -> Unit,
    onDeletePronunciationRule: (PronunciationRule) -> Unit,
    onTogglePronunciationRule: (PronunciationRule) -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }

    if (showAddRuleDialog) {
        var originalText by remember { mutableStateOf("") }
        var replacementText by remember { mutableStateOf("") }
        var isRegexChecked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Nouvelle règle de prononciation", color = MaterialTheme.colorScheme.onSurface) },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    OutlinedTextField(
                        value = originalText,
                        onValueChange = { originalText = it },
                        label = { Text("Texte d'origine") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
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
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
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
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.RecordVoiceOver, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Dictionnaire phonétique (${pronunciationRules.size})",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
        IconButton(onClick = { showAddRuleDialog = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Add, "Ajouter une règle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }

    if (pronunciationRules.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Aucune règle personnalisée. Corrigez la prononciation des mots en cliquant sur +.",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    } else {
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pronunciationRules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
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
                                fontSize = 13.sp
                            )
                            if (rule.isRegex) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "regex",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            "➔ ${rule.replacement}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.isActive,
                            onCheckedChange = { onTogglePronunciationRule(rule) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                        IconButton(
                            onClick = { onDeletePronunciationRule(rule) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete, "Supprimer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
