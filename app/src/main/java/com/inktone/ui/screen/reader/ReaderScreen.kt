package com.inktone.ui.screen.reader

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.ui.theme.reducedMotionDuration

private data class SelectionInfo(
    val sentenceIndex: Int,
    val selectedText: String
)

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
        ReaderTheme.DAY -> Triple(Color(0xFFFAFAFA), Color(0xFF1A1A1A), MaterialTheme.colorScheme.primary)
        ReaderTheme.SEPIA -> Triple(Color(0xFFF4ECD8), Color(0xFF3C2F2F), Color(0xFFB65D30))
    }

    // Taille de l'écran pour le tiers central
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var readingMode by remember { mutableStateOf(ReadingMode.PAGED) }

    // État de la sélection de texte
    var selectionState by remember { mutableStateOf<SelectionInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Feedback Snackbar après chaque action
    LaunchedEffect(state.lastAction) {
        state.lastAction?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearAction()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { screenSize = it }
    ) {
        // Mesure mémoire après chargement chapitre
        LaunchedEffect(chapter) {
            if (chapter != null) {
                com.inktone.PerfLogger.logMemorySnapshot("Reader open")
            }
        }

        // ── COUCHE 0 : Texte (100% espace, jamais ne bouge) ─
        Crossfade(targetState = state.readerTheme, animationSpec = tween(300)) {
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(state.error!!)
            chapter != null -> ReaderContent(
                chapter = chapter,
                playbackState = playbackState,
                textColor = textColor,
                accentColor = accentColor,
                readerFont = state.readerFont,
                fontSizeSp = state.fontSizeSp,
                lineHeightEm = state.lineHeightEm,
                horizontalMarginDp = state.horizontalMarginDp,
                readingMode = readingMode,
                currentChapterIndex = state.currentChapterIndex,
                totalChapters = book?.totalChapters ?: 1,
                isLoadingChapter = state.isLoadingChapter,
                onToggleMode = { readingMode = if (readingMode == ReadingMode.PAGED) ReadingMode.SCROLL else ReadingMode.PAGED },
                onPageTurned = { viewModel.hideHud() },
                onNextChapter = { viewModel.nextChapter() },
                onTextSelected = { idx, text ->
                    selectionState = SelectionInfo(idx, text)
                },
                onSelectionDismissed = {
                    selectionState = null
                },
                highlights = state.highlights,
                bookmarks = state.bookmarks,
                onTap = { offset ->
                        if (screenSize.width > 0) {
                            val left = screenSize.width / 3f
                            val right = 2f * screenSize.width / 3f
                            if (offset.x in left..right) {
                                viewModel.toggleHud()
                            }
                        }
                    },
                onDoubleTap = { viewModel.selectCurrentSentence() }
                )
            }
        } // Crossfade

        // ── Tooltip premier lancement lecteur ───────
        if (state.showReaderTooltip && state.isHudVisible) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 32.dp, end = 32.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 Appuyez sur ▶ pour synchroniser texte et audio",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.dismissReaderTooltip() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ── Tooltip 2 : après premier play ──────────
        if (state.showPlayTooltip) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 32.dp, end = 32.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 Le surlignage suit chaque mot lu",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.dismissPlayTooltip() }) {
                        Text("Compris !", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        // ── Barre d'actions de sélection ────────────
        AnimatedVisibility(
            visible = selectionState != null,
            enter = fadeIn(tween(reducedMotionDuration(200))) + slideInVertically(tween(reducedMotionDuration(200))) { it },
            exit = fadeOut(tween(reducedMotionDuration(200))) + slideOutVertically(tween(reducedMotionDuration(200))) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SelectionActionBar(
                accentColor = accentColor,
                bgColor = bgColor,
                textColor = textColor,
                onCopy = {
                    selectionState?.let {
                        clipboardManager.setText(AnnotatedString(it.selectedText))
                    }
                    selectionState = null
                },
                onHighlight = {
                    val s = selectionState ?: return@SelectionActionBar
                    val sentence = chapter?.sentences?.getOrNull(s.sentenceIndex) ?: return@SelectionActionBar
                    viewModel.addHighlight(
                        sentenceIndex = s.sentenceIndex,
                        selectedText = s.selectedText,
                        startOffset = sentence.startOffset,
                        endOffset = sentence.endOffset
                    )
                    selectionState = null
                },
                onNote = {
                    val s = selectionState ?: return@SelectionActionBar
                    viewModel.addAnnotation(s.sentenceIndex, s.selectedText)
                    selectionState = null
                },
                onBookmark = {
                    val s = selectionState ?: return@SelectionActionBar
                    viewModel.addBookmark(s.sentenceIndex, s.selectedText)
                    selectionState = null
                }
            )
        }

        // ── Captions TTS (accessibilité) ─────────────
        AnimatedVisibility(
            visible = state.isPlaying && playbackState.activeSentenceText.isNotEmpty(),
            enter = fadeIn(tween(reducedMotionDuration(300))),
            exit = fadeOut(tween(reducedMotionDuration(200))),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            Surface(
                color = bgColor.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = playbackState.activeSentenceText,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

        // ── FAB Lecture flottant (HUD masqué, pas de TTS) ──
        AnimatedVisibility(
            visible = !state.isHudVisible && !state.isPlaying && chapter != null,
            enter = fadeIn(tween(reducedMotionDuration(300))) + scaleIn(tween(reducedMotionDuration(300)), initialScale = 0.8f),
            exit = fadeOut(tween(reducedMotionDuration(200))) + scaleOut(tween(reducedMotionDuration(200)), targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
        ) {
            FloatingActionButton(
                onClick = { viewModel.play() },
                containerColor = accentColor.copy(alpha = 0.85f),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Lire",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ── TopBar (overlay) ─────────────────────────
        AnimatedVisibility(
            visible = state.isHudVisible,
            enter = fadeIn(tween(reducedMotionDuration(200))) + slideInVertically(tween(reducedMotionDuration(200))) { -it },
            exit = fadeOut(tween(reducedMotionDuration(200))) + slideOutVertically(tween(reducedMotionDuration(200))) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book?.title ?: "",
                subtitle = "Ch. ${state.currentChapterIndex + 1}/${book?.totalChapters ?: 0}",
                readingMode = readingMode,
                onToggleMode = { readingMode = if (readingMode == ReadingMode.PAGED) ReadingMode.SCROLL else ReadingMode.PAGED },
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
            enter = fadeIn(tween(reducedMotionDuration(200))) + slideInVertically(tween(reducedMotionDuration(200))) { it },
            exit = fadeOut(tween(reducedMotionDuration(200))) + slideOutVertically(tween(reducedMotionDuration(200))) { it },
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
                onDisplaySettingsClick = { viewModel.showSettingsSheet() },
                onPrevSentence = { viewModel.previousSentence() },
                onNextSentence = { viewModel.nextSentence() },
                onPrevChapter = { viewModel.previousChapter() },
                onNextChapter = { viewModel.nextChapter() }
            )
        }

        // Snackbar de feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
        )
    }

    // ── Panneau TTS ───────────────────────
    if (state.isTtsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTtsSheet() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

    // ── COUCHE 3 : Options d'affichage et typographie ─
    if (state.isSettingsSheetVisible) {
        val sheetBg = when (state.readerTheme) {
            ReaderTheme.DAY -> Color(0xFFFAFAFA)
            ReaderTheme.SEPIA -> Color(0xFFF4ECD8)
            ReaderTheme.NIGHT -> Color(0xFF121212)
        }
        val sheetTextColor = when (state.readerTheme) {
            ReaderTheme.DAY -> Color(0xFF1A1A1A)
            ReaderTheme.SEPIA -> Color(0xFF3C2F2F)
            ReaderTheme.NIGHT -> Color(0xFFFAFAFA)
        }

        ModalBottomSheet(
            onDismissRequest = { viewModel.hideSettingsSheet() },
            containerColor = sheetBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = sheetTextColor.copy(alpha = 0.3f)
                )
            }
        ) {
            ReaderSettingsPanel(
                currentTheme = state.readerTheme,
                currentFont = state.readerFont,
                fontSizeSp = state.fontSizeSp,
                lineHeightEm = state.lineHeightEm,
                horizontalMarginDp = state.horizontalMarginDp,
                onThemeChange = { viewModel.setReaderTheme(it) },
                onFontChange = { viewModel.setReaderFont(it) },
                onFontSizeChange = { viewModel.setFontSize(it) },
                onLineHeightChange = { viewModel.setLineHeight(it) },
                onHorizontalMarginChange = { viewModel.setHorizontalMargin(it) },
                accentColor = accentColor,
                panelBg = sheetBg,
                textColor = sheetTextColor
            )
        }
    }

    // ── COUCHE 2 : Table des matières ────────────────
    if (state.isTocSheetVisible && book != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTocSheet() },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
//  BARRE D'ACTIONS DE SÉLECTION
// ─────────────────────────────────────────────────────

@Composable
private fun SelectionActionBar(
    accentColor: Color,
    bgColor: Color,
    textColor: Color,
    onCopy: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onBookmark: () -> Unit
) {
    val panelBg = when {
        bgColor == Color(0xFF0D0D0D) -> Color(0xFF1A1A1A)
        bgColor == Color(0xFFF4ECD8) -> Color(0xFFE8DCC8)
        else -> Color(0xFFEEEEEE)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        shape = RoundedCornerShape(14.dp),
        color = panelBg,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text("📋 Copier", color = textColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onHighlight, modifier = Modifier.weight(1f)) {
                Text("🖍️ Surligner", color = accentColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onNote, modifier = Modifier.weight(1f)) {
                Text("📝 Note", color = textColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onBookmark, modifier = Modifier.weight(1f)) {
                Text("🔖 Marque-page", color = accentColor, fontSize = 13.sp, maxLines = 1,
                    fontWeight = FontWeight.Medium)
            }
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
        Text("❌ $msg", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp))
    }
}
