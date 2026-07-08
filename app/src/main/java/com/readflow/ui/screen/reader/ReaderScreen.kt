package com.readflow.ui.screen.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.domain.model.Chapter
import com.readflow.service.audio.PlaybackOrchestrator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val book = state.book
    val chapter = state.currentChapter

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            book?.title ?: "Lecteur",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (chapter != null) {
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Erreur
            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text("❌ $error", modifier = Modifier.padding(12.dp))
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // Sélecteur de chapitre
            else if (book != null) {
                ChapterSelector(
                    currentIndex = state.currentChapterIndex,
                    totalChapters = book.totalChapters,
                    onPrevious = { viewModel.previousChapter() },
                    onNext = { viewModel.nextChapter() }
                )

                // Contenu du chapitre
                if (chapter != null) {
                    ChapterContent(
                        chapter = chapter,
                        currentSentenceIndex = state.currentSentenceIndex,
                        isPlaying = state.isPlaying,
                        onPlay = { viewModel.play() },
                        onPause = { viewModel.pause() },
                        onStop = { viewModel.stop() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSelector(
    currentIndex: Int,
    totalChapters: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrevious) { Text("◀") }
        Text(
            "Chapitre ${currentIndex + 1} / $totalChapters",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        TextButton(onClick = onNext) { Text("▶") }
    }
}

@Composable
private fun ChapterContent(
    chapter: Chapter,
    currentSentenceIndex: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Barre de contrôle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (isPlaying) {
                Button(onClick = onPause) { Text("⏸ Pause") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onStop) { Text("⏹ Stop") }
            } else {
                Button(onClick = onPlay) { Text("▶ Lire") }
            }
        }

        // Texte du chapitre
        chapter.sentences.forEachIndexed { index, sentence ->
            val isCurrent = index == currentSentenceIndex && isPlaying
            Text(
                text = sentence.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
