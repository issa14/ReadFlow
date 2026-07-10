package com.readflow.ui.screen.reader

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.data.database.entity.PronunciationRule
import com.readflow.domain.model.Chapter
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.ui.theme.OpenDyslexicFamily

// ─────────────────────────────────────────────────────
//  READER SCREEN — Immersif, style Moon+ Reader
// ─────────────────────────────────────────────────────

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
        ReaderTheme.DAY -> Triple(Color(0xFFFAFAFA), Color(0xFF1A1A1A), Color(0xFF0091EA))
        ReaderTheme.SEPIA -> Triple(Color(0xFFF4ECD8), Color(0xFF3C2F2F), Color(0xFFB65D30))
    }

    // Taille de l'écran pour le tiers central
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { screenSize = it }
    ) {
        // ── COUCHE 0 : Texte (100% espace, jamais ne bouge) ─
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(state.error!!)
            chapter != null -> ImmersiveText(
                chapter = chapter,
                playbackState = playbackState,
                textColor = textColor,
                accentColor = accentColor,
                useOpenDyslexic = state.useOpenDyslexic,
                onTap = { offset ->
                    // Tiers central uniquement
                    if (screenSize.width > 0) {
                        val left = screenSize.width / 3f
                        val right = 2f * screenSize.width / 3f
                        if (offset.x in left..right) {
                            viewModel.toggleHud()
                        }
                    }
                }
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
                onThemeCycle = { viewModel.cycleTheme() },
                onFontToggle = { viewModel.toggleOpenDyslexic() },
                onPrevChapter = { viewModel.previousChapter() },
                onNextChapter = { viewModel.nextChapter() },
                onSettingsClick = { viewModel.showTtsSheet() }
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
                // Sleep timer
                sleepTimerRemaining = viewModel.sleepTimerRemaining.collectAsState().value,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                // Pronunciation
                pronunciationRules = viewModel.pronunciationRules.collectAsState().value,
                onAddRule = { pattern, replacement, isRegex ->
                    viewModel.addPronunciationRule(pattern, replacement, isRegex)
                },
                onDeleteRule = { viewModel.deletePronunciationRule(it) },
                onToggleRule = { viewModel.togglePronunciationRule(it) }
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
                totalChapters = book?.totalChapters ?: 0,
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
//  CHAPTER PICKER — Liste des chapitres
// ─────────────────────────────────────────────────────

@Composable
private fun ChapterPicker(
    totalChapters: Int,
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Table des matières",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 12.dp))
        for (i in 0 until totalChapters) {
            val isCurrent = i == currentChapter
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = if (isCurrent) Color(0xFFFFB74D).copy(alpha = 0.15f)
                        else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                onClick = { onSelect(i) }
            ) {
                Text(
                    "Chapitre ${i + 1}",
                    color = if (isCurrent) Color(0xFFFFB74D)
                            else Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────
//  TOP BAR — Semi-transparente, titre centré
// ─────────────────────────────────────────────────────

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onBookmarks: () -> Unit = {},
    onSearch: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0A0A).copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour",
                    tint = Color.White.copy(alpha = 0.85f))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall)
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, "Rechercher",
                    tint = Color.White.copy(alpha = 0.6f))
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onBookmarks) {
                Icon(Icons.Default.Bookmark, "Signets",
                    tint = Color.White.copy(alpha = 0.6f))
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onToc) {
                Icon(Icons.Default.List, "TOC",
                    tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  UNIFIED CONTROL PANEL — 3 rangées
// ─────────────────────────────────────────────────────

@Composable
private fun UnifiedControlPanel(
    isPlaying: Boolean,
    accentColor: Color,
    panelBg: Color,
    useOpenDyslexic: Boolean = false,
    onTtsClick: () -> Unit,
    onThemeCycle: () -> Unit,
    onFontToggle: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSettingsClick: () -> Unit = {}
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
                // Taille texte
                IconButton(onClick = { /* fontSize */ }) {
                    Text("AA", color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                // Thème
                IconButton(onClick = onThemeCycle) {
                    Icon(Icons.Default.Palette, "Thème",
                        tint = Color.White.copy(alpha = 0.6f))
                }
                // OpenDyslexic
                IconButton(onClick = onFontToggle) {
                    Text("D",
                        color = if (useOpenDyslexic) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                // Réglages TTS
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Outlined.Headphones, "Réglages vocaux",
                        tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp)
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

// ─────────────────────────────────────────────────────
//  TEXTE IMMERSIF — LazyColumn + Surlignage + Autoscroll
// ─────────────────────────────────────────────────────

/**
 * Délai de compensation Bluetooth (ms) pour la synchronisation audio/visuelle.
 *
 * Sur les appareils Bluetooth, le décalage audio peut atteindre 150-250 ms.
 * On retarde légèrement l'avancement du surlignage pour que l'affichage
 * corresponde au son perçu par l'utilisateur.
 */
private const val BLUETOOTH_LATENCY_COMPENSATION_MS = 180L

/**
 * Rendu immersif du texte d'un chapitre avec :
 * - Défilement lazy pour les grands chapitres (2000+ phrases).
 * - Surlignage de la phrase active avec fond coloré.
 * - Autoscroll fluide ([LazyListState.animateScrollToItem]) centrant
 *   la phrase active au premier tiers de l'écran.
 * - Compensation de latence Bluetooth pour synchroniser l'affichage
 *   avec le retour audio perçu.
 */
@Composable
private fun ImmersiveText(
    chapter: Chapter,
    playbackState: PlaybackState,
    textColor: Color,
    accentColor: Color,
    useOpenDyslexic: Boolean = false,
    onTap: (Offset) -> Unit
) {
    val bodyFont = if (useOpenDyslexic) OpenDyslexicFamily else FontFamily.Serif
    val listState = rememberLazyListState()
    val sentences = chapter.sentences

    val activeIdx = playbackState.activeSentenceIndex
    val isSpeaking = playbackState.status == PlaybackStatus.PLAYING

    // ── Autoscroll automatique vers la phrase active ──
    // Utilise une clé composite pour déclencher le scroll uniquement
    // quand l'index de phrase change (et pas à chaque recomposition).
    LaunchedEffect(activeIdx, isSpeaking) {
        if (isSpeaking && activeIdx in sentences.indices) {
            // Compensation Bluetooth : attendre que l'audio arrive aux oreilles
            // avant de déplacer le surlignage visuel.
            kotlinx.coroutines.delay(BLUETOOTH_LATENCY_COMPENSATION_MS)

            // +1 pour sauter la ligne de titre (index 0 = titre du chapitre)
            val targetIndex = activeIdx + 1
            if (targetIndex < listState.layoutInfo.totalItemsCount) {
                listState.animateScrollToItem(
                    index = targetIndex,
                    // Centrer au premier tiers de l'écran pour donner du contexte
                    // (l'utilisateur voit ce qui précède ET ce qui suit).
                    scrollOffset = -(listState.layoutInfo.viewportSize.height / 3)
                )
            }
        }
    }

    // ── Couleur de fond pour le surlignage ──
    // Ambre doux pour le thème nuit, bleu pour le thème jour
    val highlightBg = accentColor.copy(alpha = 0.12f)

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onTap(it) } }
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            userScrollEnabled = true
        ) {
            // ── Titre du chapitre ──
            item(key = "title") {
                Spacer(Modifier.height(24.dp))
                Text(
                    chapter.title,
                    fontFamily = bodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = textColor.copy(alpha = 0.75f),
                    lineHeight = 1.6.em
                )
                Spacer(Modifier.height(28.dp))
            }

            // ── Phrases avec surlignage ──
            itemsIndexed(
                items = sentences,
                key = { index, _ -> "sent_$index" }
            ) { index, sentence ->
                val isActive = index == activeIdx && isSpeaking

                // Surlignage : fond légèrement coloré + texte accentué
                val bgModifier = if (isActive) {
                    Modifier
                        .background(
                            color = highlightBg,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                }

                Text(
                    text = sentence.text,
                    fontFamily = bodyFont,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 17.sp,
                    lineHeight = 1.6.em,
                    textAlign = TextAlign.Justify,
                    color = when {
                        isActive -> accentColor
                        else -> textColor.copy(alpha = 0.88f)
                    },
                    modifier = bgModifier
                )
            }

            // ── Espace de respiration en bas ──
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  PANNEAU TTS — Modal Bottom Sheet
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsPanel(
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
    // ── Sleep timer ──
    sleepTimerRemaining: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    // ── Pronunciation ──
    pronunciationRules: List<PronunciationRule> = emptyList(),
    onAddRule: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteRule: (PronunciationRule) -> Unit = {},
    onToggleRule: (PronunciationRule) -> Unit = {}
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Titre
        Text(chapterTitle, color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Phrase ${sentenceIndex + 1} / $totalSentences",
            color = Color.White.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(24.dp))

        // Contrôles lecture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, "Précédent",
                    tint = Color.White.copy(alpha = 0.5f))
            }

            Spacer(Modifier.width(24.dp))

            FilledIconButton(
                onClick = { if (isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFFFB74D)
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lire",
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF0D0D0D)
                )
            }

            Spacer(Modifier.width(24.dp))

            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, "Suivant",
                    tint = Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stop discret
        TextButton(onClick = onStop) {
            Text("⏹ Arrêter", color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(20.dp))

        // Vitesse
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vitesse", color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB74D),
                    activeTrackColor = Color(0xFFFFB74D)
                )
            )
            Text("${"%.1f".format(currentSpeed)}x",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))

        // Voix
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Voix", color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = currentVoice == 0,
                onClick = { onVoiceChange(0) },
                label = { Text("Miro FR", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFB74D).copy(alpha = 0.25f),
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

        // ── SECTION : Minuteur de mise en veille ────
        SleepTimerSection(
            sleepTimerRemaining = sleepTimerRemaining,
            onStart = onStartSleepTimer,
            onCancel = onCancelSleepTimer
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

        // ── SECTION : Dictionnaire de prononciation ──
        PronunciationSection(
            rules = pronunciationRules,
            onAddClick = { showAddRuleDialog = true },
            onDelete = onDeleteRule,
            onToggle = onToggleRule
        )

        Spacer(Modifier.height(16.dp))
    }

    // ── Dialogue d'ajout de règle ──
    if (showAddRuleDialog) {
        AddPronunciationRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { pattern, replacement, isRegex ->
                onAddRule(pattern, replacement, isRegex)
                showAddRuleDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────
//  SECTION : Minuteur de mise en veille
// ─────────────────────────────────────────────────────

@Composable
private fun SleepTimerSection(
    sleepTimerRemaining: Long?,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bedtime, "Sommeil",
                tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Minuteur de sommeil",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(8.dp))

        if (sleepTimerRemaining != null) {
            // Compte à rebours actif
            val totalSecs = sleepTimerRemaining / 1000
            val minutes = (totalSecs / 60).toInt()
            val seconds = (totalSecs % 60).toInt()
            val display = "%02d:%02d".format(minutes, seconds)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Mise en veille dans $display",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onCancel) {
                    Text("✕ Annuler", color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            // Boutons de préréglages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    FilterChip(
                        selected = false,
                        onClick = { onStart(mins) },
                        label = {
                            Text("${mins} min",
                                style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White.copy(alpha = 0.65f)
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  SECTION : Dictionnaire de prononciation
// ─────────────────────────────────────────────────────

@Composable
private fun PronunciationSection(
    rules: List<PronunciationRule>,
    onAddClick: () -> Unit,
    onDelete: (PronunciationRule) -> Unit,
    onToggle: (PronunciationRule) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Translate, "Prononciation",
                    tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Dictionnaire de prononciation",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, "Ajouter une règle",
                    tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        if (rules.isEmpty()) {
            Text(
                "Aucune règle. Ajoutez des corrections de prononciation.",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            rules.forEach { rule ->
                RuleItem(
                    rule = rule,
                    onToggle = { onToggle(rule) },
                    onDelete = { onDelete(rule) }
                )
            }
        }
    }
}

@Composable
private fun RuleItem(
    rule: PronunciationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (rule.isRegex) "/${rule.pattern}/" else rule.pattern,
                color = if (rule.isActive) Color.White.copy(alpha = 0.8f)
                        else Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "→ ${rule.replacement}",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Switch(
            checked = rule.isActive,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFB74D),
                checkedTrackColor = Color(0xFFFFB74D).copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Supprimer",
                tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────
//  DIALOGUE : Ajout de règle de prononciation
// ─────────────────────────────────────────────────────

@Composable
private fun AddPronunciationRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, replacement: String, isRegex: Boolean) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var patternError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252525),
        title = {
            Text("Nouvelle règle",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        patternError = false
                    },
                    label = { Text("Mot ou motif d'origine") },
                    isError = patternError,
                    supportingText = if (patternError) {
                        { Text("Ce champ ne peut pas être vide") }
                    } else null,
                    singleLine = true,
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Texte de remplacement") },
                    singleLine = true,
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRegex,
                        onCheckedChange = { isRegex = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFFFB74D),
                            uncheckedColor = Color.White.copy(alpha = 0.4f)
                        )
                    )
                    Text(
                        "Expression régulière",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { isRegex = !isRegex }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pattern.isBlank()) {
                    patternError = true
                } else {
                    onConfirm(pattern.trim(), replacement.trim(), isRegex)
                }
            }) {
                Text("Ajouter", color = Color(0xFFFFB74D))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.7f),
    focusedLabelColor = Color(0xFFFFB74D),
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
    cursorColor = Color(0xFFFFB74D),
    focusedBorderColor = Color(0xFFFFB74D),
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    errorBorderColor = Color(0xFFFF6B6B),
    errorLabelColor = Color(0xFFFF6B6B),
    errorSupportingTextColor = Color(0xFFFF6B6B)
)

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


