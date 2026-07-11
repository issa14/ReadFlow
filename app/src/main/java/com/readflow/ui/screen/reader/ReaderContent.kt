package com.readflow.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Sentence
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.ui.theme.OpenDyslexicFamily
import kotlinx.coroutines.launch

private const val BLUETOOTH_LATENCY_COMPENSATION_MS = 180L

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
    onTap: (Offset) -> Unit,
    onPageTurned: () -> Unit
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

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val sentences = chapter.sentences
    val pages = remember(sentences, containerSize, textStyle, titleStyle, density) {
        if (containerSize.width == 0 || containerSize.height == 0 || sentences.isEmpty()) {
            return@remember emptyList<List<Pair<Int, Sentence>>>()
        }
        val result = mutableListOf<List<Pair<Int, Sentence>>>()
        var currentPage = mutableListOf<Pair<Int, Sentence>>()
        var currentHeight = 0
        val constraints = Constraints(maxWidth = containerSize.width)

        val paddingPx = with(density) { 4.dp.roundToPx() }
        val titleBottomPaddingPx = with(density) { 28.dp.roundToPx() }
        val topPaddingPx = with(density) { 24.dp.roundToPx() }

        // Hauteur du titre sur la première page
        val titleLayout = measurer.measure(
            text = AnnotatedString(chapter.title),
            style = titleStyle,
            constraints = constraints
        )
        currentHeight += titleLayout.size.height + titleBottomPaddingPx + topPaddingPx

        for ((index, sentence) in sentences.withIndex()) {
            val layoutResult = measurer.measure(
                text = AnnotatedString(sentence.text),
                style = textStyle,
                constraints = constraints
            )
            val itemHeight = layoutResult.size.height + paddingPx

            if (currentHeight + itemHeight > containerSize.height && currentPage.isNotEmpty()) {
                result.add(currentPage)
                currentPage = mutableListOf()
                currentHeight = 0
            }
            currentPage.add(index to sentence)
            currentHeight += itemHeight
        }
        if (currentPage.isNotEmpty()) {
            result.add(currentPage)
        }
        result
    }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            onPageTurned()
        }
    }

    val activeIdx = playbackState.activeSentenceIndex
    val isSpeaking = playbackState.status == PlaybackStatus.PLAYING

    LaunchedEffect(activeIdx, isSpeaking, pages) {
        if (isSpeaking && activeIdx in sentences.indices && pages.isNotEmpty()) {
            kotlinx.coroutines.delay(BLUETOOTH_LATENCY_COMPENSATION_MS)
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
                                    if (pagerState.currentPage < pages.size - 1) {
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
        if (pages.isNotEmpty()) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
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
                            val isActive = index == activeIdx && isSpeaking
                            val bgModifier = if (isActive) {
                                Modifier
                                    .background(
                                        color = accentColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            } else {
                                Modifier.padding(vertical = 2.dp)
                            }

                            Text(
                                text = sentence.text,
                                style = textStyle.copy(
                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isActive) accentColor else textColor.copy(alpha = 0.88f)
                                ),
                                modifier = bgModifier
                            )
                        }
                    }
                }
            }
        }
    }
}
