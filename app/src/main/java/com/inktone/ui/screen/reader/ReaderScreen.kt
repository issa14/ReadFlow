package com.inktone.ui.screen.reader

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.zIndex
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
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteDialogSentenceIdx by remember { mutableStateOf(-1) }
    var noteDialogSelectedText by remember { mutableStateOf("") }
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerSentenceIdx by remember { mutableStateOf(-1) }
    var colorPickerSelectedText by remember { mutableStateOf("") }
    var colorPickerStartOffset by remember { mutableStateOf(0) }
    var colorPickerEndOffset by remember { mutableStateOf(0) }
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

        // Barre de progression chapitre — 2dp, toujours présente, non obstruante
        LinearProgressIndicator(
            progress = { state.chapterProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter)
                .zIndex(1f),
            color = accentColor.copy(alpha = 0.6f),
            trackColor = Color.Transparent
        )

        // ── COUCHE 0 : Texte (100% espace, jamais ne bouge) ─
        Crossfade(targetState = state.readerTheme, animationSpec = tween(300)) {
        when {
            state.isLoading -> LoadingIndicator(color = textColor)
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
                currentSentenceIndex = state.currentSentenceIndex,
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
                onManualPositionChanged = { sentenceIndex -> viewModel.onManualPositionChanged(sentenceIndex) },
                highlights = state.highlights,
                bookmarks = state.bookmarks,
                annotations = state.annotations,
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

        // ── Tooltip premier lancement lecteur (pointe vers le FAB ▶, donc visible
        //    seulement quand le HUD est masqué — sinon collision avec UnifiedControlPanel) ──
        if (state.showReaderTooltip && !state.isHudVisible) {
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
                    Icon(
                        imageVector = com.inktone.ui.theme.AppIcons.Hint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Appuyez sur ▶ pour synchroniser texte et audio",
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
        // Masqué pendant la lecture (collision avec les captions TTS) et pendant
        // une sélection de texte (collision avec SelectionActionBar).
        if (state.showPlayTooltip && !state.isPlaying && selectionState == null) {
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
                    Icon(
                        imageVector = com.inktone.ui.theme.AppIcons.Hint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Le surlignage suit chaque mot lu",
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
                    colorPickerSentenceIdx = s.sentenceIndex
                    colorPickerSelectedText = s.selectedText
                    colorPickerStartOffset = sentence.startOffset
                    colorPickerEndOffset = sentence.endOffset
                    selectionState = null
                    showColorPicker = true
                },
                onNote = {
                    val s = selectionState ?: return@SelectionActionBar
                    noteDialogSentenceIdx = s.sentenceIndex
                    noteDialogSelectedText = s.selectedText
                    selectionState = null
                    showNoteDialog = true
                },
                onBookmark = {
                    val s = selectionState ?: return@SelectionActionBar
                    viewModel.addBookmark(s.sentenceIndex, s.selectedText)
                    selectionState = null
                }
            )
        }

        // ── Mini color picker pour les surlignages ───
        if (showColorPicker) {
            val colors = listOf("#FFEB3D", "#90EE90", "#ADD8E6", "#FFB6C1", "#FFA500")
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                shadowElevation = 4.dp
            ) {
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .clickable {
                                    viewModel.addHighlight(
                                        sentenceIndex = colorPickerSentenceIdx,
                                        selectedText = colorPickerSelectedText,
                                        startOffset = colorPickerStartOffset,
                                        endOffset = colorPickerEndOffset,
                                        colorHex = hex
                                    )
                                    showColorPicker = false
                                }
                        )
                    }
                }
            }
        }

        // ── Captions TTS (accessibilité) ─────────────
        // Masquées pendant une sélection de texte (collision avec SelectionActionBar).
        AnimatedVisibility(
            visible = state.isPlaying && playbackState.activeSentenceText.isNotEmpty() && selectionState == null,
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

        // ── Micro-indicateur enrichi (HUD masqué) ────
        if (!state.isHudVisible && chapter != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ch. ${state.currentChapterIndex + 1} / ${book?.totalChapters ?: 1}",
                    color = textColor.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
                state.etaMinutes?.let { eta ->
                    Text("·", color = textColor.copy(alpha = 0.25f), fontSize = 11.sp)
                    Text(
                        "~$eta min",
                        color = accentColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                    Icons.Filled.PlayArrow,
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
                onBack = onBack
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
                readingMode = readingMode,
                onTtsClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                onTtsSettingsClick = { viewModel.showTtsSheet() },
                onThemeCycle = { viewModel.cycleTheme() },
                onFontToggle = { viewModel.toggleOpenDyslexic() },
                onDisplaySettingsClick = { viewModel.showSettingsSheet() },
                onSleepTimerClick = { viewModel.showTtsSheet() },
                onPrevChapter = { viewModel.previousChapter() },
                onNextChapter = { viewModel.nextChapter() },
                onToggleMode = { readingMode = if (readingMode == ReadingMode.PAGED) ReadingMode.SCROLL else ReadingMode.PAGED },
                onSearch = { book?.title?.let { onSearchClick(it) } },
                onBookmarks = { book?.title?.let { onBookmarksClick(it) } },
                onToc = { viewModel.showTocSheet() }
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
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)) }
        ) {
            val sleepRemaining by viewModel.sleepTimerRemaining.collectAsState()

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
                onCancelSleepTimer = { viewModel.cancelSleepTimer() }
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
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)) }
        ) {
            ChapterPicker(
                tocEntries = book.tocEntries,
                currentChapter = state.currentChapterIndex,
                chapterTitles = state.chapterTitles,
                onSelect = { idx ->
                    viewModel.goToChapter(idx)
                    viewModel.hideTocSheet()
                    viewModel.hideHud()
                }
            )
        }
    }

    // ── Dialog de saisie de note ─────────────────────
    if (showNoteDialog) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Ajouter une note") },
            text = {
                Column {
                    Text(
                        noteDialogSelectedText.take(80) + if (noteDialogSelectedText.length > 80) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Votre note...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addAnnotation(noteDialogSentenceIdx, noteDialogSelectedText, noteText)
                        }
                        showNoteDialog = false
                    }
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Annuler") }
            }
        )
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
                Icon(com.inktone.ui.theme.AppIcons.Copy, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copier", color = textColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onHighlight, modifier = Modifier.weight(1f)) {
                Icon(com.inktone.ui.theme.AppIcons.Highlight, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Surligner", color = accentColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onNote, modifier = Modifier.weight(1f)) {
                Icon(com.inktone.ui.theme.AppIcons.Note, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Note", color = textColor, fontSize = 13.sp, maxLines = 1)
            }
            TextButton(onClick = onBookmark, modifier = Modifier.weight(1f)) {
                Icon(com.inktone.ui.theme.AppIcons.BookmarkAdd, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Marque-page", color = accentColor, fontSize = 13.sp, maxLines = 1,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : chargement, erreur
// ─────────────────────────────────────────────────────

@Composable
private fun LoadingIndicator(color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = color.copy(alpha = 0.5f))
    }
}

@Composable
private fun ErrorMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(24.dp)) {
            Icon(
                imageVector = com.inktone.ui.theme.AppIcons.ErrorOutlined,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}
