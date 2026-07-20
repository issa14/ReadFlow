package com.inktone.ui.screen.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inktone.data.database.entity.AnnotationEntity
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.RichBlock
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TextSpan
import com.inktone.domain.usecase.FrenchSentenceSplitter
import com.inktone.service.audio.PlaybackState
import com.inktone.service.audio.PlaybackStatus
import com.inktone.ui.theme.OpenDyslexicFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

enum class ReadingMode { PAGED, SCROLL }

@Composable
fun ReaderContent(
    chapter: Chapter,
    playbackState: PlaybackState,
    textColor: Color,
    accentColor: Color,
    readerFont: ReaderFont,
    fontSizeSp: Float,
    lineHeightEm: Float,
    horizontalMarginDp: Int,
    readingMode: ReadingMode,
    currentChapterIndex: Int,
    totalChapters: Int,
    isLoadingChapter: Boolean = false,
    onToggleMode: () -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit = {},
    onPageTurned: () -> Unit,
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity> = emptyList(),
    bookmarks: List<BookmarkEntity> = emptyList(),
    annotations: List<AnnotationEntity> = emptyList()
) {
    val bodyFont = when (readerFont) {
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.SANS_SERIF -> FontFamily.SansSerif
        ReaderFont.OPEN_DYSLEXIC -> OpenDyslexicFamily
    }

    val textStyle = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightEm.em,
        textAlign = TextAlign.Justify
    )
    val titleStyle = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = (fontSizeSp * 1.3f).sp,
        lineHeight = lineHeightEm.em
    )

    val activeIdx = playbackState.activeSentenceIndex
    val isSpeaking = playbackState.status == PlaybackStatus.PLAYING
    val sentences = chapter.sentences

    when (readingMode) {
        ReadingMode.PAGED -> PagedContent(
            chapter = chapter,
            sentences = sentences,
            activeIdx = activeIdx,
            isSpeaking = isSpeaking,
            textColor = textColor,
            accentColor = accentColor,
            textStyle = textStyle,
            titleStyle = titleStyle,
            horizontalMarginDp = horizontalMarginDp,
            playbackState = playbackState,
            currentChapterIndex = currentChapterIndex,
            totalChapters = totalChapters,
            isLoadingChapter = isLoadingChapter,
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onPageTurned = onPageTurned,
            onNextChapter = onNextChapter,
            onTextSelected = onTextSelected,
            onSelectionDismissed = onSelectionDismissed,
            highlights = highlights,
            bookmarks = bookmarks,
            annotations = annotations
        )
        ReadingMode.SCROLL -> ScrollContent(
            chapter = chapter,
            sentences = sentences,
            activeIdx = activeIdx,
            isSpeaking = isSpeaking,
            textColor = textColor,
            accentColor = accentColor,
            textStyle = textStyle,
            titleStyle = titleStyle,
            horizontalMarginDp = horizontalMarginDp,
            playbackState = playbackState,
            currentChapterIndex = currentChapterIndex,
            totalChapters = totalChapters,
            isLoadingChapter = isLoadingChapter,
            readingMode = readingMode,
            onToggleMode = onToggleMode,
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onNextChapter = onNextChapter,
            onTextSelected = onTextSelected,
            onSelectionDismissed = onSelectionDismissed,
            highlights = highlights,
            bookmarks = bookmarks,
            annotations = annotations
        )
    }
}

@Composable
private fun PagedContent(
    chapter: Chapter,
    sentences: List<Sentence>,
    activeIdx: Int,
    isSpeaking: Boolean,
    textColor: Color,
    accentColor: Color,
    textStyle: TextStyle,
    titleStyle: TextStyle,
    horizontalMarginDp: Int,
    playbackState: PlaybackState,
    currentChapterIndex: Int,
    totalChapters: Int,
    isLoadingChapter: Boolean = false,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit = {},
    onPageTurned: () -> Unit,
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Pagination asynchrone via produceState : la mesure de texte (coûteuse)
    // s'exécute sur Dispatchers.Default pour ne pas bloquer le thread UI.
    // yield() périodique garantit que la coroutine reste coopérative.
    val pages by produceState(emptyList<List<Pair<Int, Sentence>>>(),
        sentences, containerSize, textStyle, titleStyle, chapter.title
    ) {
        if (containerSize.width == 0 || containerSize.height == 0 || sentences.isEmpty()) {
            value = emptyList()
            return@produceState
        }

        withContext(Dispatchers.Default) {
            val result = mutableListOf<List<Pair<Int, Sentence>>>()
            var currentPage = mutableListOf<Pair<Int, Sentence>>()
            var currentHeight = 0
            val constraints = Constraints(maxWidth = containerSize.width)

            val paddingPx = with(density) { 4.dp.roundToPx() }
            val titleBottomPaddingPx = with(density) { 28.dp.roundToPx() }
            val topPaddingPx = with(density) { 24.dp.roundToPx() }

            val titleLayout = measurer.measure(
                text = AnnotatedString(chapter.title),
                style = titleStyle,
                constraints = constraints
            )
            currentHeight += titleLayout.size.height + titleBottomPaddingPx + topPaddingPx

            for ((index, sentence) in sentences.withIndex()) {
                if (index % 100 == 0) yield()

                val layoutResult = measurer.measure(
                    text = AnnotatedString(sentence.text),
                    style = textStyle,
                    constraints = constraints
                )
                val itemHeight = layoutResult.size.height + paddingPx

                if (currentHeight + itemHeight > containerSize.height && currentPage.isNotEmpty()) {
                    result.add(currentPage.toList())
                    currentPage = mutableListOf()
                    currentHeight = 0
                }
                currentPage.add(index to sentence)
                currentHeight += itemHeight
            }
            if (currentPage.isNotEmpty()) {
                result.add(currentPage.toList())
            }
            value = result
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size + 1 }) // +1 pour la page virtuelle "suite"
    val scope = rememberCoroutineScope()
    val isLastChapter = currentChapterIndex >= totalChapters - 1

    // Auto-chargement du chapitre suivant quand on atteint la page virtuelle.
    // Gardes critiques :
    // 1. pages.isNotEmpty() — évite le déclenchement quand les données ne sont pas encore chargées
    //    (sinon pages.size == 0 et currentPage == 0 → 0 == 0 → boucle infinie)
    // 2. !isLoadingChapter — évite les appels concurrents pendant un chargement en cours
    LaunchedEffect(pagerState.currentPage, pages.size) {
        if (pages.isNotEmpty() && !isLoadingChapter &&
            pagerState.currentPage == pages.size && !isLastChapter) {
            onNextChapter()
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            onPageTurned()
        }
    }

    LaunchedEffect(activeIdx, pages.size) {
        if (pages.isNotEmpty() && activeIdx in sentences.indices) {
            val targetPage = pages.indexOfFirst { page -> page.any { it.first == activeIdx } }
            if (targetPage != -1 && targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = horizontalMarginDp.dp, vertical = 16.dp)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (containerSize.width > 0) {
                            val left = containerSize.width / 3f
                            val right = 2f * containerSize.width / 3f
                            when {
                                offset.x < left -> {
                                    onPageTurned()
                                    scope.launch {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                }
                                offset.x > right -> {
                                    onPageTurned()
                                    scope.launch {
                                        if (pagerState.currentPage < pages.size) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                }
                                else -> onTap(offset)
                            }
                        }
                    },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            if (pageIndex < pages.size) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (pageIndex == 0) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = chapter.title,
                            style = titleStyle,
                            color = textColor.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(28.dp))
                    }

                    pages[pageIndex].forEach { (index, sentence) ->
                        SelectableSentence(
                            index = index,
                            sentence = sentence,
                            activeIdx = activeIdx,
                            isSpeaking = isSpeaking,
                            textStyle = textStyle,
                            textColor = textColor,
                            accentColor = accentColor,
                            playbackState = playbackState,
                            onTextSelected = onTextSelected,
                            onDismiss = onSelectionDismissed,
                            highlights = highlights,
                            bookmarks = bookmarks,
                            annotations = annotations
                        )
                    }
                }
            } else {
                // Page virtuelle : chargement du chapitre suivant ou fin du livre
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLastChapter) {
                        Text(
                            text = "Fin du livre",
                            style = textStyle.copy(
                                color = textColor.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Chargement du chapitre suivant...",
                                style = textStyle.copy(
                                    fontSize = 14.sp,
                                    color = textColor.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollContent(
    chapter: Chapter,
    sentences: List<Sentence>,
    activeIdx: Int,
    isSpeaking: Boolean,
    textColor: Color,
    accentColor: Color,
    textStyle: TextStyle,
    titleStyle: TextStyle,
    horizontalMarginDp: Int,
    playbackState: PlaybackState,
    currentChapterIndex: Int,
    totalChapters: Int,
    isLoadingChapter: Boolean = false,
    readingMode: ReadingMode,
    onToggleMode: () -> Unit,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit = {},
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>
) {
    val lazyListState = rememberLazyListState()

    // Points d'ancrage pour les blocs purement structurels (image, séparateur) qui n'ont
    // aucun texte dans le flux de phrases — ils peuvent donc s'intercaler sans risque de
    // duplication ni de désynchronisation avec le surlignage TTS (qui reste piloté par
    // Sentence/activeIdx, inchangé). Les blocs textuels (titre, citation, poème) restent
    // rendus via le flux de phrases existant pour préserver la sélection/surlignage/signets.
    val richBlockAnchors = remember(chapter.index, chapter.richBlocks) {
        computeStructuralBlockAnchors(chapter.richBlocks)
    }

    LaunchedEffect(activeIdx) {
        if (activeIdx in sentences.indices) {
            lazyListState.animateScrollToItem(
                index = activeIdx + 1, // +1 pour le titre en position 0
                scrollOffset = 0
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = horizontalMarginDp.dp, vertical = 16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset -> onTap(offset) },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Titre du chapitre en item 0
            item(key = "chapter_title") {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = chapter.title,
                    style = titleStyle,
                    color = textColor.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(28.dp))
            }

            // Blocs structurels (image, séparateur) situés avant la première phrase
            richBlockAnchors[0]?.forEach { block ->
                item(key = "richblock_${block.index}") {
                    RichBlockRenderer(block = block, textStyle = textStyle, textColor = textColor, accentColor = accentColor)
                }
            }

            itemsIndexed(
                items = sentences,
                key = { index, _ -> index }
            ) { index, sentence ->
                Column {
                    SelectableSentence(
                        index = index,
                        sentence = sentence,
                        activeIdx = activeIdx,
                        isSpeaking = isSpeaking,
                        textStyle = textStyle,
                        textColor = textColor,
                        accentColor = accentColor,
                        playbackState = playbackState,
                        onTextSelected = onTextSelected,
                        onDismiss = onSelectionDismissed,
                        highlights = highlights,
                        bookmarks = bookmarks,
                        annotations = annotations
                    )

                    richBlockAnchors[index + 1]?.forEach { block ->
                        RichBlockRenderer(block = block, textStyle = textStyle, textColor = textColor, accentColor = accentColor)
                    }
                }
            }

            // Déclencheur automatique inter-chapitres
            item(key = "next_chapter_trigger") {
                NextChapterTrigger(
                    sentences = sentences,
                    isLoadingChapter = isLoadingChapter,
                    isLastChapter = currentChapterIndex >= totalChapters - 1,
                    textColor = textColor,
                    onNextChapter = onNextChapter
                )
            }
        }
    }
}

@Composable
private fun NextChapterTrigger(
    sentences: List<Sentence>,
    isLoadingChapter: Boolean,
    isLastChapter: Boolean,
    textColor: Color,
    onNextChapter: () -> Unit
) {
    // Ne déclenche le chargement automatique que si :
    // 1. Les phrases sont chargées (sentences.isNotEmpty())
    // 2. Le chapitre n'est pas déjà en cours de chargement
    // 3. Ce n'est pas le dernier chapitre
    // Le LaunchedEffect utilise sentences.size comme clé pour ne se déclencher
    // qu'une fois les données réellement disponibles, pas à la composition initiale.
    LaunchedEffect(sentences.size) {
        if (sentences.isNotEmpty() && !isLoadingChapter && !isLastChapter) {
            onNextChapter()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLastChapter) {
            Text(
                text = "Fin du livre",
                style = TextStyle(
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = textColor.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Chargement du chapitre suivant...",
                    style = TextStyle(
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SelectableSentence(
    index: Int,
    sentence: Sentence,
    activeIdx: Int,
    isSpeaking: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    accentColor: Color,
    playbackState: PlaybackState,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onDismiss: () -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>
) {
    val defaultToolbar = LocalTextToolbar.current

    val toolbar = remember(index, sentence) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = defaultToolbar.status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                onTextSelected(index, sentence.text)
            }

            override fun hide() {
                defaultToolbar.hide()
                onDismiss()
            }
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer {
            SentenceRenderer(
                index = index,
                sentence = sentence,
                activeIdx = activeIdx,
                isSpeaking = isSpeaking,
                textStyle = textStyle,
                textColor = textColor,
                accentColor = accentColor,
                playbackState = playbackState,
                highlights = highlights,
                bookmarks = bookmarks,
                annotations = annotations
            )
        }
    }
}

@Composable
private fun SentenceRenderer(
    index: Int,
    sentence: Sentence,
    activeIdx: Int,
    isSpeaking: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    accentColor: Color,
    playbackState: PlaybackState,
    highlights: List<HighlightEntity> = emptyList(),
    bookmarks: List<BookmarkEntity> = emptyList(),
    annotations: List<AnnotationEntity> = emptyList()
) {
    val isActive = index == activeIdx && isSpeaking
    val highlight = highlights.find { it.sentenceIndex == index }
    val hasBookmark = bookmarks.any { it.sentenceIndex == index }
    val hasNote = annotations.any { it.sentenceIndex == index }

    val highlightColor = highlight?.let {
        try {
            Color(android.graphics.Color.parseColor(it.colorHex))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.tertiary
        }
    }

    val bgModifier = when {
        isActive -> Modifier
            .background(
                color = accentColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
        highlightColor != null -> Modifier
            .background(
                color = highlightColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
        else -> Modifier.padding(vertical = 2.dp)
    }

    Row(modifier = bgModifier, verticalAlignment = Alignment.CenterVertically) {
        if (hasBookmark) {
            Icon(
                imageVector = com.inktone.ui.theme.AppIcons.Bookmark,
                contentDescription = "Marque-page",
                tint = accentColor,
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 4.dp)
            )
        }
        if (hasNote) {
            Icon(
                imageVector = com.inktone.ui.theme.AppIcons.Note,
                contentDescription = "Note",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 4.dp)
            )
        }

        if (isActive) {
            ActiveSentenceText(
                text = sentence.text,
                style = textStyle,
                accentColor = accentColor,
                textColor = textColor,
                durationMs = playbackState.sentenceDurationMs,
                startTimestamp = playbackState.sentenceStartTimestamp
            )
        } else {
            // Reading trail : phrases déjà lues grisées, à venir normales
            val isAlreadyRead = index < activeIdx
            Text(
                text = sentence.text,
                style = textStyle.copy(
                    fontWeight = FontWeight.Normal,
                    color = if (isAlreadyRead) textColor.copy(alpha = 0.40f)
                            else textColor.copy(alpha = 0.88f)
                )
            )
        }
    }
}

@Composable
private fun ActiveSentenceText(
    text: String,
    style: TextStyle,
    accentColor: Color,
    textColor: Color,
    durationMs: Long,
    startTimestamp: Long,
    modifier: Modifier = Modifier
) {
    var rawElapsed by remember(startTimestamp) { mutableStateOf(0L) }

    // Tracking à ~60fps pour un suivi précis du mot actif
    LaunchedEffect(startTimestamp, durationMs) {
        val start = startTimestamp
        while (System.currentTimeMillis() - start < durationMs) {
            rawElapsed = System.currentTimeMillis() - start
            kotlinx.coroutines.delay(16)
        }
        rawElapsed = durationMs
    }

    val cleanWords = remember(text) { text.split(Regex("\\s+")).filter { it.isNotEmpty() } }

    if (cleanWords.isEmpty() || durationMs <= 0) {
        Text(text, style = style.copy(color = accentColor, fontWeight = FontWeight.Medium), modifier = modifier)
        return
    }

    val totalChars = cleanWords.sumOf { it.length }.toFloat()
    val activeWordIdx = remember(rawElapsed, cleanWords, durationMs) {
        val fraction = (rawElapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        val targetCharCount = fraction * totalChars
        var currentCharCount = 0
        var foundIdx = 0
        for (i in cleanWords.indices) {
            currentCharCount += cleanWords[i].length
            if (currentCharCount >= targetCharCount) { foundIdx = i; break }
        }
        foundIdx
    }

    val annotatedString = remember(cleanWords, activeWordIdx, accentColor, textColor) {
        val builder = AnnotatedString.Builder()
        val originalWords = text.split(" ")

        var wordCounter = 0
        originalWords.forEachIndexed { index, part ->
            if (part.trim().isEmpty()) {
                builder.append(" ")
                return@forEachIndexed
            }
            if (wordCounter == activeWordIdx) {
                builder.pushStyle(style.toSpanStyle().copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = style.fontSize * 1.08f
                ))
                builder.append(part)
                builder.pop()
            } else if (wordCounter in (activeWordIdx + 1)..(activeWordIdx + 2)) {
                builder.pushStyle(style.toSpanStyle().copy(
                    color = accentColor.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Normal
                ))
                builder.append(part)
                builder.pop()
            } else {
                builder.pushStyle(style.toSpanStyle().copy(
                    color = textColor.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Normal
                ))
                builder.append(part)
                builder.pop()
            }
            wordCounter++
            if (index < originalWords.lastIndex) builder.append(" ")
        }
        builder.toAnnotatedString()
    }

    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────
//  RENDU SÉMANTIQUE — RichBlock (structure EPUB préservée)
// ─────────────────────────────────────────────────────

/**
 * Rendu typographique d'un [RichBlock] (titre, citation, poème, image, séparateur).
 *
 * Coexiste avec le rendu par [Sentence] (surlignage TTS mot-à-mot, sélection, signets) :
 * seuls les blocs purement structurels sans contrepartie textuelle dans le flux de phrases
 * ([RichBlock.EpubImage], [RichBlock.SectionBreak]) sont actuellement intercalés dans
 * [ScrollContent] (voir [computeStructuralBlockAnchors]) — les blocs textuels restent
 * disponibles ici pour un usage futur sans risquer de dupliquer le texte déjà rendu par
 * phrase ou de désynchroniser la sélection/le surlignage.
 */
@Composable
fun RichBlockRenderer(
    block: RichBlock,
    textStyle: TextStyle,
    textColor: Color,
    accentColor: Color
) {
    when (block) {
        is RichBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.5f
                2 -> 1.3f
                3 -> 1.15f
                else -> 1.05f
            }
            Spacer(Modifier.height(if (block.level <= 2) 24.dp else 16.dp))
            Text(
                text = buildSpanString(block.spans, textColor),
                style = textStyle.copy(
                    fontSize = textStyle.fontSize * scale,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            )
            Spacer(Modifier.height(8.dp))
        }

        is RichBlock.Paragraph -> {
            Text(
                text = buildSpanString(block.spans, textColor),
                style = textStyle,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        is RichBlock.BlockQuote -> {
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildSpanString(block.spans, textColor.copy(alpha = 0.75f)),
                    style = textStyle,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        is RichBlock.PoemLine -> {
            Text(
                text = buildSpanString(block.spans, textColor),
                style = textStyle.copy(
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        is RichBlock.EpubImage -> {
            val imageFile = remember(block.href) { File(block.href) }
            if (imageFile.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = block.alt.ifBlank { null },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        is RichBlock.SectionBreak -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = 0.25f))
                    )
                    if (it < 2) Spacer(Modifier.width(8.dp))
                }
            }
        }

        is RichBlock.Footnote -> { /* Ignoré dans le flux principal */ }
    }
}

private fun buildSpanString(spans: List<TextSpan>, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        spans.forEach { span ->
            withStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                    baselineShift = if (span.superscript) BaselineShift.Superscript else BaselineShift.None,
                    fontSize = if (span.superscript) TextUnit(0.75f, TextUnitType.Em) else TextUnit.Unspecified,
                    color = baseColor
                )
            ) {
                append(span.text)
            }
            if (span.noteRef != null) append(" ")  // Espace après référence footnote
        }
    }
}

/**
 * Associe à chaque bloc purement structurel ([RichBlock.EpubImage], [RichBlock.SectionBreak])
 * le nombre de phrases qui le précèdent dans le chapitre — permet de l'intercaler au bon
 * endroit dans le flux de [Sentence] sans avoir besoin d'offsets de caractères partagés
 * entre les deux segmentations (HTML sémantique vs texte aplati pour le TTS), qui sont
 * calculées indépendamment et ne s'alignent pas caractère-à-caractère.
 *
 * Approximatif par nature (les deux segmentations peuvent découper légèrement différemment),
 * mais suffisant pour un placement visuel — contrairement à une désynchronisation du
 * surlignage TTS ou une duplication de texte, une image décalée d'une phrase est un
 * défaut mineur et sans risque.
 */
private fun computeStructuralBlockAnchors(richBlocks: List<RichBlock>): Map<Int, List<RichBlock>> {
    val anchors = mutableMapOf<Int, MutableList<RichBlock>>()
    var sentenceCursor = 0
    for (block in richBlocks) {
        when (block) {
            is RichBlock.EpubImage, is RichBlock.SectionBreak -> {
                anchors.getOrPut(sentenceCursor) { mutableListOf() }.add(block)
            }
            is RichBlock.Paragraph -> sentenceCursor += estimateSentenceCount(block.spans)
            is RichBlock.Heading -> sentenceCursor += estimateSentenceCount(block.spans)
            is RichBlock.BlockQuote -> sentenceCursor += estimateSentenceCount(block.spans)
            is RichBlock.PoemLine -> sentenceCursor += 1
            is RichBlock.Footnote -> {}
        }
    }
    return anchors
}

private fun estimateSentenceCount(spans: List<TextSpan>): Int {
    val text = spans.joinToString("") { it.text }
    if (text.isBlank()) return 0
    return FrenchSentenceSplitter.split(text).size.coerceAtLeast(1)
}
