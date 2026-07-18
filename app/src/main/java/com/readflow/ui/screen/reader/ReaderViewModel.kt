package com.readflow.ui.screen.reader

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.repository.BookRepository
import com.readflow.domain.repository.TtsRepository
import com.readflow.data.database.AnnotationDao
import com.readflow.data.database.BookmarkDao
import com.readflow.data.database.HighlightDao
import com.readflow.data.database.entity.AnnotationEntity
import com.readflow.data.database.entity.BookmarkEntity
import com.readflow.data.database.entity.HighlightEntity
import com.readflow.service.audio.PlaybackOrchestrator
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.service.onnx.OnnxInferenceService
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
    val bookmarks: List<BookmarkEntity> = emptyList()
)

enum class ReaderTheme { DAY, NIGHT, SEPIA }
enum class ReaderFont { SERIF, SANS_SERIF, OPEN_DYSLEXIC }

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val orchestrator: PlaybackOrchestrator,
    private val onnxService: OnnxInferenceService,
    private val settingsRepository: com.readflow.data.settings.SettingsRepository,
    private val pronunciationRuleDao: com.readflow.data.database.PronunciationRuleDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val annotationDao: AnnotationDao,
    private val audioServiceLauncher: com.readflow.domain.service.AudioServiceLauncher,
    private val ttsRepository: TtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val sleepTimerRemaining: StateFlow<Int?> = orchestrator.sleepTimerRemaining

    val pronunciationRules: StateFlow<List<com.readflow.data.database.entity.PronunciationRule>> = 
        pronunciationRuleDao.getAllRulesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<PlaybackState> = orchestrator.playbackState

    private var currentBook: Book? = null
    private var isPausedForResume = false

    fun toggleHud() { _uiState.update { it.copy(isHudVisible = !it.isHudVisible) } }
    fun hideHud() { _uiState.update { it.copy(isHudVisible = false) } }
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

    fun addPronunciationRule(original: String, replacement: String, isRegex: Boolean) {
        viewModelScope.launch {
            try {
                pronunciationRuleDao.insertRule(
                    com.readflow.data.database.entity.PronunciationRule(
                        pattern = original, replacement = replacement, isRegex = isRegex
                    )
                )
            } catch (e: Exception) { Log.e("ReaderVM", "Error inserting pronunciation rule", e) }
        }
    }

    fun deletePronunciationRule(rule: com.readflow.data.database.entity.PronunciationRule) {
        viewModelScope.launch {
            try { pronunciationRuleDao.deleteRule(rule) }
            catch (e: Exception) { Log.e("ReaderVM", "Error deleting pronunciation rule", e) }
        }
    }

    fun togglePronunciationRule(rule: com.readflow.data.database.entity.PronunciationRule) {
        viewModelScope.launch {
            try { pronunciationRuleDao.toggleRuleActive(rule.id, !rule.isActive) }
            catch (e: Exception) { Log.e("ReaderVM", "Error updating pronunciation rule", e) }
        }
    }

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
            orchestrator.playbackState.collect { pbs ->
                _uiState.update {
                    it.copy(currentSentenceIndex = pbs.activeSentenceIndex, totalSentences = pbs.totalSentences)
                }
                // Persister la position pour Process Death
                savedState["sentenceIndex"] = pbs.activeSentenceIndex

                // Mettre à jour ProgressEntity de la bibliothèque en arrière-plan
                val book = currentBook
                if (book != null) {
                    val chapterIdx = _uiState.value.currentChapterIndex
                    val totalSent = pbs.totalSentences.coerceAtLeast(1)
                    val fraction = (chapterIdx.toFloat() + pbs.activeSentenceIndex.toFloat() / totalSent) / book.totalChapters.coerceAtLeast(1)
                    
                    try {
                        bookRepository.saveProgress(
                            com.readflow.domain.model.Progress(
                                bookId = book.id,
                                currentChapterIndex = chapterIdx,
                                currentSentenceIndex = pbs.activeSentenceIndex,
                                totalProgressFraction = fraction.coerceIn(0f, 1f)
                            )
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

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                val book = books.find { it.id == bookId } ?: throw IllegalStateException("Livre introuvable")
                currentBook = book
                _uiState.update { it.copy(book = book, isLoading = false) }
                val dbProgress = orchestrator.loadProgress(bookId)
                val targetChapter: Int
                val targetSentence: Int
                if (dbProgress != null) {
                    targetChapter = dbProgress.chapterIndex.coerceIn(0, book.totalChapters - 1)
                    targetSentence = dbProgress.sentenceIndex
                } else {
                    targetChapter = savedChapter
                    targetSentence = savedSentence
                }
                loadChapter(targetChapter, targetSentence)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun loadChapter(index: Int, sentenceIndex: Int = 0) {
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
                val chapter = bookRepository.getChapter(book.id, index)
                val highlights = highlightDao.getHighlightsForChapter(book.id, index).first()
                val bookmarks = bookmarkDao.getBookmarks(book.id).first()
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index, currentChapter = chapter,
                        totalSentences = chapter.sentences.size, currentSentenceIndex = sentenceIndex,
                        highlights = highlights, bookmarks = bookmarks,
                        isLoading = false, isLoadingChapter = false
                    )
                }

                // Lancer le préchauffage du chapitre suivant en arrière-plan
                preWarmNextChapter(book, index)
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
        }
    }

    fun nextSentence() {
        val maxIdx = (_uiState.value.totalSentences - 1).coerceAtLeast(0)
        if (_uiState.value.isPlaying) orchestrator.seekToNext()
        else {
            val nextIdx = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxIdx)
            _uiState.update { it.copy(currentSentenceIndex = nextIdx) }
        }
    }

    fun goToChapter(index: Int) {
        if (_uiState.value.isLoadingChapter) return
        val book = currentBook ?: return
        if (index in 0 until book.totalChapters) loadChapter(index)
    }

    fun play() {
        // Reprise après pause : pas de destruction/reconstruction, reprise instantanée
        if (isPausedForResume) {
            isPausedForResume = false
            orchestrator.resume()
            _uiState.update { it.copy(isPlaying = true) }
            return
        }

        val chapter = _uiState.value.currentChapter ?: return
        val book = currentBook ?: return
        if (!audioServiceLauncher.canStart()) {
            _uiState.update { it.copy(error = "Permission notification requise.") }
            return
        }
        audioServiceLauncher.start()
        val s = _uiState.value
        orchestrator.play(
            chapter.sentences, voice = s.voice, speed = s.speed, startFrom = 0,
            bookTitle = book.title, chapterTitle = chapter.title, bookId = book.id, chapterIndex = s.currentChapterIndex
        )
        _uiState.update { it.copy(isPlaying = true) }
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
                loadChapter(next)
                // Relancer la lecture avec le nouveau chapitre
                play()
            }
        }
    }

    /**
     * Pré-synthétise la première phrase du chapitre suivant en arrière-plan.
     *
     * Appelé après chaque [loadChapter] pour que le passage au chapitre N+1
     * soit instantané (gap zéro, pas d'attente de synthèse WebSocket/ONNX).
     */
    private fun preWarmNextChapter(book: Book, currentIndex: Int) {
        val nextIndex = currentIndex + 1
        if (nextIndex >= book.totalChapters) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextChapter = bookRepository.getChapter(book.id, nextIndex)
                val firstSentence = nextChapter.sentences.firstOrNull() ?: return@launch
                val s = _uiState.value
                val result = ttsRepository.synthesize(firstSentence.text, s.voice, s.speed)
                orchestrator.preWarm(result)
                Log.d("ReaderVM", "Pre-warm OK: chapitre ${nextIndex + 1}, phrase 1 (${result.engineId})")
            } catch (e: Exception) {
                Log.w("ReaderVM", "Pre-warm échoué pour chapitre ${nextIndex + 1}: ${e.message}")
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    // ── Marque-pages, surlignages, annotations ─

    fun addBookmark(sentenceIndex: Int, text: String) {
        val book = currentBook ?: return
        viewModelScope.launch {
            try {
                val chapterIdx = _uiState.value.currentChapterIndex
                val existing = bookmarkDao.findByPosition(book.id, chapterIdx, sentenceIndex)
                if (existing != null) {
                    bookmarkDao.delete(existing)
                    _uiState.update { it.copy(lastAction = "Marque-page retiré") }
                } else {
                    bookmarkDao.insert(
                        BookmarkEntity(
                            bookId = book.id,
                            chapterIndex = chapterIdx,
                            sentenceIndex = sentenceIndex,
                            text = text.take(120)
                        )
                    )
                    _uiState.update { it.copy(lastAction = "Marque-page ajouté") }
                }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error bookmark: ${e.message}", e)
            }
        }
    }

    fun addHighlight(sentenceIndex: Int, selectedText: String, startOffset: Int, endOffset: Int) {
        val book = currentBook ?: return
        viewModelScope.launch {
            try {
                val chapterIdx = _uiState.value.currentChapterIndex
                highlightDao.insertHighlight(
                    HighlightEntity(
                        bookId = book.id,
                        chapterIndex = chapterIdx,
                        sentenceIndex = sentenceIndex,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        selectedText = selectedText,
                        colorHex = "#FFEB3D"
                    )
                )
                _uiState.update { it.copy(lastAction = "Surlignage ajouté") }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error highlight: ${e.message}", e)
            }
        }
    }

    fun addAnnotation(sentenceIndex: Int, selectedText: String) {
        val book = currentBook ?: return
        viewModelScope.launch {
            try {
                val chapterIdx = _uiState.value.currentChapterIndex
                annotationDao.insertAnnotation(
                    AnnotationEntity(
                        bookId = book.id,
                        chapterIndex = chapterIdx,
                        sentenceIndex = sentenceIndex,
                        selectedText = selectedText,
                        colorHex = "#FFF9C4"
                    )
                )
                _uiState.update { it.copy(lastAction = "Annotation ajoutée") }
                reloadAnnotations(book.id, chapterIdx)
            } catch (e: Exception) {
                Log.e("ReaderVM", "Error annotation: ${e.message}", e)
            }
        }
    }

    private suspend fun reloadAnnotations(bookId: String, chapterIdx: Int) {
        try {
            val highlights = highlightDao.getHighlightsForChapter(bookId, chapterIdx).first()
            val bookmarks = bookmarkDao.getBookmarks(bookId).first()
            _uiState.update { it.copy(highlights = highlights, bookmarks = bookmarks) }
        } catch (e: Exception) {
            Log.e("ReaderVM", "Error reloading annotations: ${e.message}", e)
        }
    }

    fun clearAction() {
        _uiState.update { it.copy(lastAction = null) }
    }
}
