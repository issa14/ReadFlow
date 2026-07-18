package com.inktone.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Sentence
import com.inktone.service.audio.PlaybackState
import com.inktone.service.audio.PlaybackStatus
import com.inktone.ui.theme.OpenDyslexicFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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
    onPageTurned: () -> Unit,
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity> = emptyList(),
    bookmarks: List<BookmarkEntity> = emptyList()
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
            onPageTurned = onPageTurned,
            onNextChapter = onNextChapter,
            onTextSelected = onTextSelected,
            onSelectionDismissed = onSelectionDismissed,
            highlights = highlights,
            bookmarks = bookmarks
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
            onNextChapter = onNextChapter,
            onTextSelected = onTextSelected,
            onSelectionDismissed = onSelectionDismissed,
            highlights = highlights,
            bookmarks = bookmarks
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
    onPageTurned: () -> Unit,
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>
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
                detectTapGestures { offset ->
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
                }
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
                            bookmarks = bookmarks
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
    onNextChapter: () -> Unit,
    onTextSelected: (sentenceIndex: Int, selectedText: String) -> Unit,
    onSelectionDismissed: () -> Unit,
    highlights: List<HighlightEntity>,
    bookmarks: List<BookmarkEntity>
) {
    val lazyListState = rememberLazyListState()

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
                detectTapGestures { offset ->
                    onTap(offset)
                }
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

            itemsIndexed(
                items = sentences,
                key = { index, _ -> index }
            ) { index, sentence ->
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
                    bookmarks = bookmarks
                )
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
    bookmarks: List<BookmarkEntity>
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
                bookmarks = bookmarks
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
    bookmarks: List<BookmarkEntity> = emptyList()
) {
    val isActive = index == activeIdx && isSpeaking
    val highlight = highlights.find { it.sentenceIndex == index }
    val hasBookmark = bookmarks.any { it.sentenceIndex == index }

    val highlightColor = highlight?.let {
        try {
            Color(android.graphics.Color.parseColor(it.colorHex))
        } catch (_: Exception) {
            Color(0xFFFFEB3D)
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
            Text(
                "🔖",
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp)
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
            Text(
                text = sentence.text,
                style = textStyle.copy(
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.88f)
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
    var elapsed by remember(startTimestamp) { mutableStateOf(System.currentTimeMillis() - startTimestamp) }
    
    LaunchedEffect(startTimestamp, durationMs) {
        val start = startTimestamp
        while (System.currentTimeMillis() - start < durationMs) {
            elapsed = System.currentTimeMillis() - start
            kotlinx.coroutines.delay(50)
        }
        elapsed = durationMs
    }
    
    val cleanWords = remember(text) { text.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    
    if (cleanWords.isEmpty() || durationMs <= 0) {
        Text(text, style = style.copy(color = accentColor, fontWeight = FontWeight.Medium), modifier = modifier)
        return
    }
    
    val totalChars = cleanWords.sumOf { it.length }.toFloat()
    val activeWordIdx = remember(elapsed, cleanWords, durationMs) {
        val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        val targetCharCount = fraction * totalChars
        var currentCharCount = 0
        var foundIdx = 0
        for (i in cleanWords.indices) {
            currentCharCount += cleanWords[i].length
            if (currentCharCount >= targetCharCount) {
                foundIdx = i
                break
            }
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
                builder.pushStyle(style.toSpanStyle().copy(color = accentColor, fontWeight = FontWeight.Bold))
                builder.append(part)
                builder.pop()
            } else {
                builder.pushStyle(style.toSpanStyle().copy(color = textColor.copy(alpha = 0.88f), fontWeight = FontWeight.Normal))
                builder.append(part)
                builder.pop()
            }
            wordCounter++
            if (index < originalWords.lastIndex) {
                builder.append(" ")
            }
        }
        builder.toAnnotatedString()
    }
    
    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}
