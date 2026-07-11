package com.readflow.ui.screen.reader

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.ui.theme.OpenDyslexicFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onBookmarksClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()
    val chapter = state.currentChapter
    val book = state.book

    // Mode immersif
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Auto-hide du HUD
    LaunchedEffect(state.isHudVisible) {
        if (state.isHudVisible) {
            kotlinx.coroutines.delay(4000)
            viewModel.hideHud()
        }
    }

    // Couleurs selon le thème
    val (bgColor, textColor, accentColor) = when (state.readerTheme) {
        ReaderTheme.NIGHT -> Triple(Color(0xFF0D0D0D), Color.White, Color(0xFFFFB74D))
        ReaderTheme.DAY -> Triple(Color(0xFFFAFAFA), Color(0xFF1A1A1A), Color(0xFF0091EA))
        ReaderTheme.SEPIA -> Triple(Color(0xFFF4ECD8), Color(0xFF3C2F2F), Color(0xFFB65D30))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ── COUCHE 0 : Texte (100% espace, jamais ne bouge) ─
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(state.error!!)
            chapter != null -> ReaderContent(
                chapter = chapter,
                playbackState = playbackState,
                textColor = textColor,
                accentColor = accentColor,
                useOpenDyslexic = state.useOpenDyslexic,
                onPageTurned = { viewModel.hideHud() },
                onTap = { viewModel.toggleHud() }
            )
        }

        // ── Micro-indicateur (HUD masqué) ────
        if (!state.isHudVisible && chapter != null) {
            val pct = if (state.totalSentences > 0)
                (state.currentSentenceIndex * 100.0 / state.totalSentences) else 0.0
            Text(
                "${"%.1f".format(pct)}%",
                color = textColor.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 8.dp)
            )
        }

        // ── Statut TTS : chargement du modèle ONNX ────
        if (ttsStatus is TtsStatus.Initializing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.92f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Préparation de la voix...",
                        color = textColor.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Erreur TTS : carte avec bouton Réessayer ──
        val ttsErr = ttsStatus as? TtsStatus.Error
        if (ttsErr != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2A1A1A),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠️", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Moteur vocal indisponible",
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ttsErr.message,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.retryTtsInit() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = accentColor
                        )
                    ) {
                        Text("Réessayer", fontSize = 13.sp)
                    }
                }
            }
        }

        // ── TopBar (overlay) ─────────────────────────
        AnimatedVisibility(
            visible = state.isHudVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book?.title ?: "",
                subtitle = "Ch. ${state.currentChapterIndex + 1}/${book?.totalChapters ?: 0}",
                onBack = onBack,
                onToc = { viewModel.showTocSheet() },
                onBookmarks = { book?.title?.let { onBookmarksClick(it) } },
                onSearch = { book?.title?.let { onSearchClick(it) } }
            )
        }

        // ── UnifiedControlPanel (overlay) ────────────
        val panelBg = when (state.readerTheme) {
            ReaderTheme.SEPIA -> Color(0xFFE8DCC8)
            else -> Color(0xFF0A0A0A)
        }
        AnimatedVisibility(
            visible = state.isHudVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            UnifiedControlPanel(
                isPlaying = state.isPlaying,
                accentColor = accentColor,
                panelBg = panelBg,
                useOpenDyslexic = state.useOpenDyslexic,
                onTtsClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                onTtsSettingsClick = { viewModel.showTtsSheet() },
                onThemeCycle = { viewModel.cycleTheme() },
                onFontToggle = { viewModel.toggleOpenDyslexic() },
                onPrevChapter = { viewModel.previousChapter() },
                onNextChapter = { viewModel.nextChapter() }
            )
        }
    }

    // ── Panneau TTS ───────────────────────
    if (state.isTtsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTtsSheet() },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            val sleepRemaining by viewModel.sleepTimerRemaining.collectAsState()
            val rules by viewModel.pronunciationRules.collectAsState()

            TtsPanel(
                chapterTitle = chapter?.title ?: "",
                sentenceIndex = state.currentSentenceIndex,
                totalSentences = state.totalSentences,
                isPlaying = state.isPlaying,
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onStop = { viewModel.stop() },
                onPrevious = { viewModel.previousSentence() },
                onNext = { viewModel.nextSentence() },
                onSpeedChange = { viewModel.setSpeed(it) },
                onVoiceChange = { viewModel.setVoice(it) },
                currentSpeed = state.speed,
                currentVoice = state.voice,
                sleepTimerRemaining = sleepRemaining,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                pronunciationRules = rules,
                onAddPronunciationRule = { orig, rep, reg -> viewModel.addPronunciationRule(orig, rep, reg) },
                onDeletePronunciationRule = { viewModel.deletePronunciationRule(it) },
                onTogglePronunciationRule = { viewModel.togglePronunciationRule(it) }
            )
        }
    }

    // ── COUCHE 2 : Table des matières ────────────────
    if (state.isTocSheetVisible && book != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTocSheet() },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            ChapterPicker(
                totalChapters = book.totalChapters,
                currentChapter = state.currentChapterIndex,
                onSelect = { idx ->
                    viewModel.goToChapter(idx)
                    viewModel.hideTocSheet()
                    viewModel.hideHud()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : chargement, erreur
// ─────────────────────────────────────────────────────

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun ErrorMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("❌ $msg", color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp))
    }
}
