package com.readflow.ui.screen.reader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.repository.BookRepository
import com.readflow.service.audio.AudioPlaybackService
import com.readflow.service.audio.PlaybackOrchestrator
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.service.onnx.OnnxInferenceService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val error: String? = null,
    // ── UI state (HUD, sheets) ──
    val isHudVisible: Boolean = false,
    val isTtsSheetVisible: Boolean = false,
    val isTocSheetVisible: Boolean = false,
    val speed: Float = 1.0f,
    val voice: Int = 0,  // MIRO — voix française Piper VITS
    val readerTheme: ReaderTheme = ReaderTheme.NIGHT,
    val useOpenDyslexic: Boolean = false
)

enum class ReaderTheme { DAY, NIGHT, SEPIA }

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val orchestrator: PlaybackOrchestrator,
    private val onnxService: OnnxInferenceService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /**
     * État de lecture détaillé pour la synchronisation visuelle (Phase 2).
     *
     * Exposé directement depuis [PlaybackOrchestrator.playbackState]
     * pour que l'UI observe :
     * - L'index de la phrase active (surlignage)
     * - Le texte de la phrase active
     * - Le statut (PLAYING / PAUSED / BUFFERING)
     *
     * Le déclenchement de l'autoscroll se fait dans [ReaderScreen]
     * via un `LaunchedEffect` sur `playbackState.activeSentenceIndex`.
     */
    val playbackState: StateFlow<PlaybackState> = orchestrator.playbackState

    private var currentBook: Book? = null
    private var isPausedForResume = false

    // ── UI actions ───────────────────────────────────

    fun toggleHud() { _uiState.update { it.copy(isHudVisible = !it.isHudVisible) } }
    fun hideHud() { _uiState.update { it.copy(isHudVisible = false) } }
    fun showTtsSheet() { _uiState.update { it.copy(isTtsSheetVisible = true) } }
    fun hideTtsSheet() { _uiState.update { it.copy(isTtsSheetVisible = false) } }
    fun showTocSheet() { _uiState.update { it.copy(isTocSheetVisible = true) } }
    fun hideTocSheet() { _uiState.update { it.copy(isTocSheetVisible = false) } }
    fun setSpeed(s: Float) { _uiState.update { it.copy(speed = s.coerceIn(0.5f, 2.0f)) } }
    fun setVoice(v: Int) { _uiState.update { it.copy(voice = v) } }

    fun cycleTheme() {
        val next = when (_uiState.value.readerTheme) {
            ReaderTheme.NIGHT -> ReaderTheme.SEPIA
            ReaderTheme.SEPIA -> ReaderTheme.DAY
            ReaderTheme.DAY -> ReaderTheme.NIGHT
        }
        _uiState.update { it.copy(readerTheme = next) }
    }
    fun setTheme(theme: ReaderTheme) { _uiState.update { it.copy(readerTheme = theme) } }
    fun toggleOpenDyslexic() { _uiState.update { it.copy(useOpenDyslexic = !it.useOpenDyslexic) } }

    init {
        // P1: Initialiser le moteur TTS silencieusement dès l'ouverture du lecteur
        viewModelScope.launch {
            try {
                onnxService.initialize()
            } catch (e: Exception) {
                Log.e("ReaderVM", "Échec initialisation ONNX: ${e.message}", e)
                _uiState.update { it.copy(error = "Échec du moteur TTS : ${e.message}") }
            }
        }

        // Observer l'orchestrateur pour synchroniser l'UI
        viewModelScope.launch {
            orchestrator.state.collect { state ->
                val wasPlaying = _uiState.value.isPlaying
                val nowPlaying = state is PlaybackOrchestrator.State.Playing
                _uiState.update { it.copy(isPlaying = nowPlaying) }

                // P3: Fin naturelle → chapitre suivant automatique
                if (wasPlaying && state is PlaybackOrchestrator.State.Idle && !isPausedForResume) {
                    autoAdvanceIfAtEnd()
                }
            }
        }

        // Observer le PlaybackState pour la synchronisation d'index de phrase
        viewModelScope.launch {
            orchestrator.playbackState.collect { pbs ->
                _uiState.update {
                    it.copy(
                        currentSentenceIndex = pbs.activeSentenceIndex,
                        totalSentences = pbs.totalSentences
                    )
                }
                // Persister la position pour Process Death
                savedState["sentenceIndex"] = pbs.activeSentenceIndex
            }
        }
    }

    fun loadBook(bookId: String) {
        // Restaurer l'état après Process Death
        val savedChapter = savedState.get<Int>("chapterIndex") ?: 0
        val savedSentence = savedState.get<Int>("sentenceIndex") ?: 0
        val savedSpeed = savedState.get<Float>("speed") ?: 1.0f
        val savedVoice = savedState.get<Int>("voice") ?: 0
        val savedTheme = savedState.get<String>("theme")?.let {
            try { ReaderTheme.valueOf(it) } catch (_: Exception) { null }
        } ?: ReaderTheme.NIGHT
        val savedDyslexic = savedState.get<Boolean>("openDyslexic") ?: false

        _uiState.update { it.copy(
            speed = savedSpeed, voice = savedVoice,
            readerTheme = savedTheme, useOpenDyslexic = savedDyslexic
        )}

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                val book = books.find { it.id == bookId }
                    ?: throw IllegalStateException("Livre introuvable")
                currentBook = book
                _uiState.update { it.copy(book = book, isLoading = false) }

                // Charger la progression persistée depuis Room (survit aux redémarrages)
                val dbProgress = orchestrator.loadProgress(bookId)

                val targetChapter: Int
                val targetSentence: Int

                if (dbProgress != null) {
                    // Progression Room → prioritaire sur SavedStateHandle (process death)
                    targetChapter = dbProgress.chapterIndex.coerceIn(0, book.totalChapters - 1)
                    targetSentence = dbProgress.sentenceIndex
                    Log.d("ReaderVM", "Progression restaurée depuis Room: ch=$targetChapter sent=$targetSentence")
                } else {
                    // Fallback sur SavedStateHandle (survie au process death uniquement)
                    targetChapter = savedChapter
                    targetSentence = savedSentence
                    Log.d("ReaderVM", "Progression restaurée depuis SavedState: ch=$targetChapter sent=$targetSentence")
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
        // Persister l'état pour Process Death
        savedState["chapterIndex"] = index
        savedState["sentenceIndex"] = sentenceIndex
        savedState["speed"] = _uiState.value.speed
        savedState["voice"] = _uiState.value.voice
        savedState["theme"] = _uiState.value.readerTheme.name
        savedState["openDyslexic"] = _uiState.value.useOpenDyslexic
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val chapter = bookRepository.getChapter(book.id, index)
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index,
                        currentChapter = chapter,
                        totalSentences = chapter.sentences.size,
                        currentSentenceIndex = sentenceIndex,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun previousChapter() {
        val idx = _uiState.value.currentChapterIndex - 1
        if (idx >= 0) loadChapter(idx)
    }

    fun nextChapter() {
        val book = currentBook ?: return
        val idx = _uiState.value.currentChapterIndex + 1
        if (idx < book.totalChapters) loadChapter(idx)
    }

    fun previousSentence() {
        if (_uiState.value.isPlaying) {
            orchestrator.seekToPrevious()
        } else {
            val prevIdx = (_uiState.value.currentSentenceIndex - 1).coerceAtLeast(0)
            _uiState.update { it.copy(currentSentenceIndex = prevIdx) }
        }
    }

    fun nextSentence() {
        val maxIdx = (_uiState.value.totalSentences - 1).coerceAtLeast(0)
        if (_uiState.value.isPlaying) {
            orchestrator.seekToNext()
        } else {
            val nextIdx = (_uiState.value.currentSentenceIndex + 1).coerceAtMost(maxIdx)
            _uiState.update { it.copy(currentSentenceIndex = nextIdx) }
        }
    }

    fun goToChapter(index: Int) {
        val book = currentBook ?: return
        if (index in 0 until book.totalChapters) loadChapter(index)
    }

    fun play() {
        val chapter = _uiState.value.currentChapter ?: return
        val book = currentBook ?: return

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _uiState.update { it.copy(error = "Permission notification requise.") }
                return
            }
        }

        val intent = Intent(context, AudioPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)

        // P2: Reprendre à la phrase sauvegardée (Pause) ou démarrer du début (Stop/Idle)
        val startFrom = if (isPausedForResume) _uiState.value.currentSentenceIndex else 0
        isPausedForResume = false

        val s = _uiState.value
        orchestrator.play(
            chapter.sentences,
            voice = s.voice,
            speed = s.speed,
            startFrom = startFrom,
            bookTitle = book.title,
            chapterTitle = chapter.title,
            bookId = book.id,
            chapterIndex = s.currentChapterIndex
        )
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        isPausedForResume = true  // P2: mémoriser la position pour reprise
        orchestrator.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        isPausedForResume = false  // P2: stop = réinitialisation complète
        orchestrator.stop()
        _uiState.update { it.copy(isPlaying = false, currentSentenceIndex = 0) }
    }

    // P3: Avancer automatiquement au chapitre suivant si on est à la dernière phrase
    private fun autoAdvanceIfAtEnd() {
        val book = currentBook ?: return
        val chapter = _uiState.value.currentChapter ?: return
        val lastIdx = chapter.sentences.lastIndex
        val currentIdx = _uiState.value.currentSentenceIndex

        if (currentIdx >= lastIdx) {
            val next = _uiState.value.currentChapterIndex + 1
            if (next < book.totalChapters) {
                loadChapter(next)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

