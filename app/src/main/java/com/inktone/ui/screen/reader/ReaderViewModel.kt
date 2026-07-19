package com.inktone.ui.screen.reader

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.domain.usecase.CalculateReadingProgressUseCase
import com.inktone.domain.usecase.LoadChapterUseCase
import com.inktone.domain.usecase.ManageReaderAnnotationsUseCase
import com.inktone.domain.usecase.PreWarmNextChapterUseCase
import com.inktone.domain.usecase.ResolveReadingPositionUseCase
import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.AnnotationEntity
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import com.inktone.service.audio.PlaybackOrchestrator
import com.inktone.service.audio.PlaybackState
import com.inktone.service.audio.PlaybackStatus
import com.inktone.service.onnx.OnnxInferenceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val currentChapterIndex: Int = 0,
    val currentChapter: Chapter? = null,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingChapter: Boolean = false,
    val error: String? = null,
    val isHudVisible: Boolean = false,
    val isTtsSheetVisible: Boolean = false,
    val isTocSheetVisible: Boolean = false,
    val isSettingsSheetVisible: Boolean = false,
    val speed: Float = 1.0f,
    val voice: Int = 0,
    val readerTheme: ReaderTheme = ReaderTheme.NIGHT,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val fontSizeSp: Float = 18f,
    val lineHeightEm: Float = 1.8f,
    val horizontalMarginDp: Int = 24,
    val useOpenDyslexic: Boolean = false,
    val lastAction: String? = null,
    val highlights: List<HighlightEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val showReaderTooltip: Boolean = false,
    val showPlayTooltip: Boolean = false,
    val etaMinutes: Int? = null,
    val chapterProgressFraction: Float = 0f
)

enum class ReaderTheme { DAY, NIGHT, SEPIA }
enum class ReaderFont { SERIF, SANS_SERIF, OPEN_DYSLEXIC }

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val orchestrator: PlaybackOrchestrator,
    private val onnxService: OnnxInferenceService,
    private val settingsRepository: com.inktone.data.settings.SettingsRepository,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val annotationDao: AnnotationDao,
    private val readingSessionDao: com.inktone.data.database.ReadingSessionDao,
    private val audioServiceLauncher: com.inktone.domain.service.AudioServiceLauncher,
    private val ttsRepository: TtsRepository,
    private val calculateProgress: CalculateReadingProgressUseCase,
    private val loadChapterUseCase: LoadChapterUseCase,
    private val annotationsUseCase: ManageReaderAnnotationsUseCase,
    private val preWarmChapter: PreWarmNextChapterUseCase,
    private val resolvePosition: ResolveReadingPositionUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderVM"
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val sleepTimerRemaining: StateFlow<Int?> = orchestrator.sleepTimerRemaining

    val playbackState: StateFlow<PlaybackState> = orchestrator.playbackState

    private var currentBook: Book? = null
    private var isPausedForResume = false

    fun toggleHud() { _uiState.update { it.copy(isHudVisible = !it.isHudVisible) } }
    fun hideHud() { _uiState.update { it.copy(isHudVisible = false) } }

    fun dismissReaderTooltip() {
        _uiState.update { it.copy(showReaderTooltip = false) }
        viewModelScope.launch { settingsRepository.markReaderTooltipSeen() }
    }

    fun dismissPlayTooltip() {
        _uiState.update { it.copy(showPlayTooltip = false) }
        viewModelScope.launch { settingsRepository.markPlayTooltipSeen() }
    }

    /** Double tap → sélectionne la phrase courante. */
    fun selectCurrentSentence() {
        val chapter = _uiState.value.currentChapter ?: return
        val idx = _uiState.value.currentSentenceIndex
        val sentence = chapter.sentences.getOrNull(idx) ?: return
        _uiState.update {
            it.copy(lastAction = "Phrase sélectionnée — copiez, surlignez, marque-page")
        }
    }
    fun showTtsSheet() { _uiState.update { it.copy(isTtsSheetVisible = true) } }
    fun hideTtsSheet() { _uiState.update { it.copy(isTtsSheetVisible = false) } }
    fun showTocSheet() { _uiState.update { it.copy(isTocSheetVisible = true) } }
    fun hideTocSheet() { _uiState.update { it.copy(isTocSheetVisible = false) } }
    fun showSettingsSheet() { _uiState.update { it.copy(isSettingsSheetVisible = true) } }
    fun hideSettingsSheet() { _uiState.update { it.copy(isSettingsSheetVisible = false) } }
    fun setSpeed(s: Float) { _uiState.update { it.copy(speed = s.coerceIn(0.5f, 2.0f)) } }
    fun setVoice(v: Int) { _uiState.update { it.copy(voice = v) } }

    fun startSleepTimer(minutes: Int) { orchestrator.startSleepTimer(minutes) }
    fun cancelSleepTimer() { orchestrator.cancelSleepTimer() }

    fun setReaderTheme(theme: ReaderTheme) {
        _uiState.update { it.copy(readerTheme = theme) }
        savedState["theme"] = theme.name
        viewModelScope.launch { settingsRepository.setReaderTheme(theme.name) }
    }

    fun setReaderFont(font: ReaderFont) {
        _uiState.update { it.copy(readerFont = font, useOpenDyslexic = (font == ReaderFont.OPEN_DYSLEXIC)) }
        savedState["openDyslexic"] = (font == ReaderFont.OPEN_DYSLEXIC)
        viewModelScope.launch { settingsRepository.setReaderFont(font.name) }
    }

    fun setFontSize(size: Float) {
        _uiState.update { it.copy(fontSizeSp = size.coerceIn(12f, 32f)) }
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setLineHeight(height: Float) {
        _uiState.update { it.copy(lineHeightEm = height.coerceIn(1.2f, 2.4f)) }
        viewModelScope.launch { settingsRepository.setLineHeight(height) }
    }

    fun setHorizontalMargin(margin: Int) {
        _uiState.update { it.copy(horizontalMarginDp = margin.coerceIn(8, 48)) }
        viewModelScope.launch { settingsRepository.setHorizontalMargin(margin) }
    }

    fun cycleTheme() {
        val next = when (_uiState.value.readerTheme) {
            ReaderTheme.NIGHT -> ReaderTheme.SEPIA
            ReaderTheme.SEPIA -> ReaderTheme.DAY
            ReaderTheme.DAY -> ReaderTheme.NIGHT
        }
        setReaderTheme(next)
    }

    fun setTheme(theme: ReaderTheme) { setReaderTheme(theme) }

    fun toggleOpenDyslexic() {
        val nextFont = if (_uiState.value.readerFont == ReaderFont.OPEN_DYSLEXIC) ReaderFont.SERIF else ReaderFont.OPEN_DYSLEXIC
        setReaderFont(nextFont)
    }

    init {
        // Charger les préférences de lecture depuis DataStore (remplace SharedPreferences)
        viewModelScope.launch {
            combine(
                settingsRepository.readerTheme,
                settingsRepository.readerFont,
                settingsRepository.fontSize,
                settingsRepository.lineHeight,
                settingsRepository.horizontalMargin
            ) { theme, font, size, lh, margin ->
                val initialTheme = try { ReaderTheme.valueOf(theme) } catch (_: Exception) { ReaderTheme.NIGHT }
                val initialFont = try { ReaderFont.valueOf(font) } catch (_: Exception) { ReaderFont.SERIF }
                _uiState.update {
                    it.copy(
                        readerTheme = initialTheme, readerFont = initialFont,
                        fontSizeSp = size, lineHeightEm = lh,
                        horizontalMarginDp = margin,
                        useOpenDyslexic = (initialFont == ReaderFont.OPEN_DYSLEXIC)
                    )
                }
            }.collect()
        }

        // Navigation vers une position précise depuis Recherche/Signets (retour d'écran)
        viewModelScope.launch {
            savedState.getStateFlow<Int?>("jumpChapter", null).collect { jumpChapter ->
                val jumpSentence = savedState.get<Int>("jumpSentence")
                if (jumpChapter != null && jumpSentence != null) {
                    savedState.remove<Int>("jumpChapter")
                    savedState.remove<Int>("jumpSentence")
                    loadChapter(jumpChapter, jumpSentence)
                }
            }
        }

        // Tooltip premier lancement reader
        viewModelScope.launch {
            if (!settingsRepository.hasSeenReaderTooltip.first()) {
                _uiState.update { it.copy(showReaderTooltip = true) }
            }
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                onnxService.initialize()
            } catch (e: Exception) {
                Log.e("ReaderVM", "Echec initialisation ONNX: ${e.message}", e)
                _uiState.update { it.copy(error = "Echec du moteur TTS : ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            orchestrator.state.collect { state ->
                val wasPlaying = _uiState.value.isPlaying
                val nowPlaying = state is PlaybackOrchestrator.State.Playing
                _uiState.update { it.copy(isPlaying = nowPlaying) }
                if (wasPlaying && state is PlaybackOrchestrator.State.Idle && !isPausedForResume) {
                    autoAdvanceIfAtEnd()
                }
            }
        }

        viewModelScope.launch {
            var firstAudioReported = false
            orchestrator.playbackState.collect { pbs ->
                _uiState.update {
                    it.copy(currentSentenceIndex = pbs.activeSentenceIndex, totalSentences = pbs.totalSentences)
                }
                _uiState.value.currentChapter?.let { chapter ->
                    updateEta(pbs.activeSentenceIndex, pbs.totalSentences, chapter)
                }
                // Détecter la première sortie audio
                if (!firstAudioReported && pbs.activeSentenceIndex == 0 && pbs.status == com.inktone.service.audio.PlaybackStatus.PLAYING) {
                    firstAudioReported = true
                    com.inktone.PerfLogger.markFirstAudioOutput(0)
                }
                // Persister la position pour Process Death
                savedState["sentenceIndex"] = pbs.activeSentenceIndex

                // Persister la progression via UseCase (logique métier extraite)
                val book = currentBook
                if (book != null) {
                    try {
                        calculateProgress(
                            book = book,
                            chapterIndex = _uiState.value.currentChapterIndex,
                            sentenceIndex = pbs.activeSentenceIndex,
                            totalSentences = pbs.totalSentences
                        )
                    } catch (e: Exception) {
                        Log.e("ReaderVM", "Error saving progress: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun loadBook(bookId: String) {
        val savedChapter = savedState.get<Int>("chapterIndex") ?: 0
        val savedSentence = savedState.get<Int>("sentenceIndex") ?: 0
        val savedSpeed = savedState.get<Float>("speed") ?: 1.0f
        val savedVoice = savedState.get<Int>("voice") ?: 0
        _uiState.update { it.copy(speed = savedSpeed, voice = savedVoice) }

        // Synchroniser la voix avec les Settings si jamais modifiée (1ère ouverture)
        if (savedVoice == 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val settingsVoiceName = settingsRepository.voice.first()
                val settingsVoice = OnnxInferenceService.Voice.entries
                    .find { it.name.equals(settingsVoiceName, ignoreCase = true) }
                if (settingsVoice != null) {
                    _uiState.update { it.copy(voice = settingsVoice.sid) }
                }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                val book = books.find { it.id == bookId } ?: throw IllegalStateException("Livre introuvable")
                currentBook = book
                _uiState.update { it.copy(book = book, isLoading = false) }
                val dbProgress = orchestrator.loadProgress(bookId)
                val position = resolvePosition(
                    dbChapterIndex = dbProgress?.chapterIndex,
                    dbSentenceIndex = dbProgress?.sentenceIndex,
                    savedChapterIndex = savedChapter,
                    savedSentenceIndex = savedSentence,
                    totalChapters = book.totalChapters
                )
                val targetChapter = position.chapterIndex
                val targetSentence = position.sentenceIndex
                loadChapter(targetChapter, targetSentence)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun loadChapter(index: Int, sentenceIndex: Int = 0, autoPlay: Boolean = false) {
        val book = currentBook ?: return
        if (index < 0 || index >= book.totalChapters) return
        // Empêche les appels concurrents qui causent la boucle infinie
        if (_uiState.value.isLoadingChapter && index == _uiState.value.currentChapterIndex) return
        savedState["chapterIndex"] = index
        savedState["sentenceIndex"] = sentenceIndex
        savedState["speed"] = _uiState.value.speed
        savedState["voice"] = _uiState.value.voice
        savedState["theme"] = _uiState.value.readerTheme.name
        savedState["openDyslexic"] = (_uiState.value.readerFont == ReaderFont.OPEN_DYSLEXIC)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isLoadingChapter = true, error = null) }
            try {
                val result = loadChapterUseCase(book.id, index)
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index, currentChapter = result.chapter,
                        totalSentences = result.chapter.sentences.size, currentSentenceIndex = sentenceIndex,
                        highlights = result.highlights, bookmarks = result.bookmarks,
                        isLoading = false, isLoadingChapter = false
                    )
                }

                // Lancer le préchauffage du chapitre suivant en arrière-plan
                viewModelScope.launch(Dispatchers.IO) {
                    preWarmChapter(book, index, _uiState.value.voice, _uiState.value.speed)
                }

                updateEta(sentenceIndex, result.chapter.sentences.size, result.chapter)

                // Relancer la lecture automatiquement après un auto-advance
                if (autoPlay) {
                    play()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false, isLoadingChapter = false) }
            }
        }
    }

    fun previousChapter() {
        if (_uiState.value.isLoadingChapter) return
        val idx = _uiState.value.currentChapterIndex - 1
        if (idx >= 0) loadChapter(idx)
    }

    fun nextChapter() {
        if (_uiState.value.isLoadingChapter) return
        val book = currentBook ?: return
        val idx = _uiState.value.currentChapterIndex + 1
        if (idx < book.totalChapters) loadChapter(idx)
    }

    fun previousSentence() {
        if (_uiState.value.isPlaying) orchestrator.seekToPrevious()
        else {
            val prevIdx = (_uiState.value.currentSentenceIndex - 1).coerceAtLeast(0)
            _uiState.update { it.copy(currentSentenceIndex = prevIdx) }
            _uiState.value.currentChapter?.let { updateEta(prevIdx, _uiState.value.totalSentences, it) }
        }
    }

    fun nextSentence() {
        val maxIdx = (_uiState.value.totalSentences - 1).coerceAtLeast(0)
        if (_uiState.value.isPlaying) orchestrator.seekToNext()
        else {
            val nextIdx = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxIdx)
            _uiState.update { it.copy(currentSentenceIndex = nextIdx) }
            _uiState.value.currentChapter?.let { updateEta(nextIdx, _uiState.value.totalSentences, it) }
        }
    }

    /** Calcule le temps de lecture restant estimé pour le chapitre courant, à partir du WPM moyen de l'utilisateur. */
    private fun updateEta(currentSentenceIndex: Int, totalSentences: Int, chapter: Chapter) {
        viewModelScope.launch(Dispatchers.Default) {
            val sessions = readingSessionDao.getAllSync()
            val totalWords = sessions.sumOf { it.wordsRead.toLong() }
            val totalSeconds = sessions.sumOf { it.durationSeconds }
            val chapterPct = if (totalSentences > 0)
                currentSentenceIndex.toFloat() / totalSentences else 0f

            if (totalSeconds < 60) {
                _uiState.update { it.copy(chapterProgressFraction = chapterPct) }
                return@launch
            }

            val wpm = (totalWords * 60.0 / totalSeconds).toInt().coerceIn(80, 500)
            val wordsRemaining = chapter.sentences.drop(currentSentenceIndex).sumOf { it.text.split(" ").size }
            val etaMin = (wordsRemaining / wpm.toDouble()).toInt().coerceAtLeast(1)

            _uiState.update { it.copy(etaMinutes = etaMin, chapterProgressFraction = chapterPct) }
        }
    }

    fun goToChapter(index: Int, sentenceIndex: Int = 0) {
        if (_uiState.value.isLoadingChapter) return
        val book = currentBook ?: return
        if (index in 0 until book.totalChapters) loadChapter(index, sentenceIndex)
    }

    fun play() {
        Log.d(TAG, "DEBUG play() called, isPausedForResume=$isPausedForResume")
        // Reprise après pause : pas de destruction/reconstruction, reprise instantanée
        if (isPausedForResume) {
            Log.d(TAG, "DEBUG play() → resume path")
            isPausedForResume = false
            orchestrator.resume()
            _uiState.update { it.copy(isPlaying = true) }
            return
        }

        val chapter = _uiState.value.currentChapter
        if (chapter == null) {
            Log.w(TAG, "DEBUG play() → ABORT: currentChapter is null")
            return
        }
        val book = currentBook
        if (book == null) {
            Log.w(TAG, "DEBUG play() → ABORT: currentBook is null")
            return
        }
        Log.d(TAG, "DEBUG play() book=${book.title}, chapter=${chapter.title}, sentences=${chapter.sentences.size}")
        if (!audioServiceLauncher.canStart()) {
            Log.w(TAG, "DEBUG play() → ABORT: canStart()=false (notification permission)")
            _uiState.update { it.copy(error = "Permission notification requise.") }
            return
        }
        Log.d(TAG, "DEBUG play() → canStart OK, starting service...")
        audioServiceLauncher.start()
        Log.d(TAG, "DEBUG play() → service started, calling orchestrator.play()...")
        val s = _uiState.value
        com.inktone.PerfLogger.markTtsPlayRequest()
        orchestrator.play(
            chapter.sentences, voice = s.voice, speed = s.speed, startFrom = 0,
            bookTitle = book.title, chapterTitle = chapter.title, bookId = book.id, chapterIndex = s.currentChapterIndex
        )
        Log.d(TAG, "DEBUG play() → orchestrator.play() done")
        _uiState.update { it.copy(isPlaying = true) }

        // Tooltip 2 : "Le surlignage suit chaque mot" après le 1er play
        viewModelScope.launch {
            if (!settingsRepository.hasSeenPlayTooltip.first()) {
                kotlinx.coroutines.delay(3000)
                _uiState.update { it.copy(showPlayTooltip = true) }
            }
        }
    }

    fun pause() {
        isPausedForResume = true
        orchestrator.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        isPausedForResume = false
        orchestrator.stop()
        _uiState.update { it.copy(isPlaying = false, currentSentenceIndex = 0) }
    }

    private fun autoAdvanceIfAtEnd() {
        val book = currentBook ?: return
        val chapter = _uiState.value.currentChapter ?: return
        val lastIdx = chapter.sentences.lastIndex
        val currentIdx = _uiState.value.currentSentenceIndex
        if (currentIdx >= lastIdx) {
            val next = _uiState.value.currentChapterIndex + 1
            if (next < book.totalChapters) {
                loadChapter(next, autoPlay = true)
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    // ── Marque-pages, surlignages, annotations ─

    fun addBookmark(sentenceIndex: Int, text: String) {
        val book = currentBook ?: return
        val chapterIdx = _uiState.value.currentChapterIndex
        viewModelScope.launch {
            try {
                val result = annotationsUseCase.toggleBookmark(book.id, chapterIdx, sentenceIndex, text)
                if (result is com.inktone.domain.usecase.AnnotationResult.Success) {
                    _uiState.update { it.copy(lastAction = result.message) }
                }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error bookmark: ${e.message}", e)
            }
        }
    }

    fun addHighlight(sentenceIndex: Int, selectedText: String, startOffset: Int, endOffset: Int) {
        val book = currentBook ?: return
        val chapterIdx = _uiState.value.currentChapterIndex
        viewModelScope.launch {
            try {
                val result = annotationsUseCase.addHighlight(book.id, chapterIdx, sentenceIndex, selectedText, startOffset, endOffset)
                if (result is com.inktone.domain.usecase.AnnotationResult.Success) {
                    _uiState.update { it.copy(lastAction = result.message) }
                }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error highlight: ${e.message}", e)
            }
        }
    }

    fun addAnnotation(sentenceIndex: Int, selectedText: String) {
        val book = currentBook ?: return
        val chapterIdx = _uiState.value.currentChapterIndex
        viewModelScope.launch {
            try {
                val result = annotationsUseCase.addAnnotation(book.id, chapterIdx, sentenceIndex, selectedText)
                if (result is com.inktone.domain.usecase.AnnotationResult.Success) {
                    _uiState.update { it.copy(lastAction = result.message) }
                }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error annotation: ${e.message}", e)
            }
        }
    }

    private suspend fun reloadAnnotations(bookId: String, chapterIdx: Int) {
        try {
            val reloaded = annotationsUseCase.reloadAnnotations(bookId, chapterIdx)
            _uiState.update { it.copy(highlights = reloaded.highlights, bookmarks = reloaded.bookmarks) }
        } catch (e: Exception) {
            Log.e("ReaderVM", "Error reloading annotations: ${e.message}", e)
        }
    }

    fun clearAction() {
        _uiState.update { it.copy(lastAction = null) }
    }
}
