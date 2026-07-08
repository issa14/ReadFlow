package com.readflow.ui.screen.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.domain.model.Chapter
import kotlinx.coroutines.delay

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val book = state.book
    val chapter = state.currentChapter

    // Contrôles visibles (toggle au tap)
    var showControls by remember { mutableStateOf(false) }
    var ttsSpeed by remember { mutableFloatStateOf(1.0f) }
    var ttsVoice by remember { mutableIntStateOf(0) }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Auto-hide des contrôles après 5s
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Fond sombre (style lecture Moon+) ──────────
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1A1A1A)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("❌ ${state.error}", color = Color(0xFFFF6B6B))
                    }
                }
                chapter != null -> {
                    // Texte scrollable (plein écran)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .clickable { showControls = !showControls }
                            .padding(horizontal = 16.dp, vertical = 48.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        )
                        Spacer(Modifier.height(16.dp))

                        chapter.sentences.forEachIndexed { index, sentence ->
                            val isCurrent = index == state.currentSentenceIndex && state.isPlaying
                            Text(
                                text = sentence.text + " ",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = when {
                                        isCurrent -> Color(0xFFFFB74D) // orange Moon+ highlight
                                        else -> Color.White.copy(alpha = 0.85f)
                                    },
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp
                                )
                            )
                        }

                        Spacer(Modifier.height(80.dp)) // espace pour la barre du bas
                    }
                }
            }
        }

        // ── Top bar minimaliste ────────────────────────
        if (book != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White.copy(alpha = 0.8f))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("Ch. ${state.currentChapterIndex + 1}/${book.totalChapters}",
                        color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ── Overlay contrôles (animé) ──────────────────
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2A2A2A).copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Progression chapitre ───────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Phrase ${state.currentSentenceIndex + 1}/${chapter?.sentences?.size ?: 0}",
                            color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showControls = false }) {
                            Text("✕", color = Color.White.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Vitesse TTS ───────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${"%.1f".format(ttsSpeed)}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = ttsSpeed,
                            onValueChange = { ttsSpeed = it },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFB74D),
                                activeTrackColor = Color(0xFFFFB74D)
                            )
                        )
                    }

                    // ── Voix TTS ──────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = ttsVoice == 0,
                            onClick = { ttsVoice = 0 },
                            label = { Text("♀ Jessica", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB74D).copy(alpha = 0.3f),
                                selectedLabelColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = ttsVoice == 1,
                            onClick = { ttsVoice = 1 },
                            label = { Text("♂ Pierre", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB74D).copy(alpha = 0.3f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Boutons Play/Pause/Stop ────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Précédent
                        IconButton(onClick = { /* TODO: prev sentence */ },
                            modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Précédent",
                                tint = Color.White.copy(alpha = 0.6f))
                        }

                        Spacer(Modifier.width(16.dp))

                        // Play/Pause
                        FilledIconButton(
                            onClick = {
                                if (state.isPlaying) viewModel.pause()
                                else viewModel.play(ttsVoice, ttsSpeed)
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFFFB74D)
                            )
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Lire",
                                modifier = Modifier.size(28.dp),
                                tint = Color(0xFF1A1A1A)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        // Stop
                        IconButton(onClick = { viewModel.stop(); showControls = false },
                            modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Stop, "Stop",
                                tint = Color.White.copy(alpha = 0.6f))
                        }
                    }

                    // ── Navigation chapitres ───────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = viewModel::previousChapter) {
                            Text("◀ Chap. précédent", color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = viewModel::nextChapter) {
                            Text("Chap. suivant ▶", color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

