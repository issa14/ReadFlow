package com.readflow.ui.screen.reader

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.repository.BookRepository
import com.readflow.service.audio.AudioPlaybackService
import com.readflow.service.audio.PlaybackOrchestrator
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
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val orchestrator: PlaybackOrchestrator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBook: Book? = null

    init {
        // Observer l'orchestrateur pour synchroniser l'UI
        viewModelScope.launch {
            orchestrator.state.collect { state ->
                _uiState.update {
                    it.copy(isPlaying = state is PlaybackOrchestrator.State.Playing)
                }
            }
        }
        viewModelScope.launch {
            orchestrator.progress.collect { progress ->
                _uiState.update {
                    it.copy(currentSentenceIndex = progress.sentenceIndex)
                }
            }
        }
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                val book = books.find { it.id == bookId }
                    ?: throw IllegalStateException("Livre introuvable")
                currentBook = book
                _uiState.update { it.copy(book = book, isLoading = false) }
                loadChapter(0)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun loadChapter(index: Int) {
        val book = currentBook ?: return
        if (index < 0 || index >= book.totalChapters) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val chapter = bookRepository.getChapter(book.id, index)
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index,
                        currentChapter = chapter,
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

    fun play(voice: Int = 0, speed: Float = 1.0f) {
        val chapter = _uiState.value.currentChapter ?: return
        val book = currentBook ?: return

        val intent = Intent(context, AudioPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)

        orchestrator.play(
            chapter.sentences,
            voice = voice,
            speed = speed,
            bookTitle = book.title,
            chapterTitle = chapter.title
        )
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        orchestrator.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        orchestrator.stop()
        _uiState.update { it.copy(isPlaying = false, currentSentenceIndex = 0) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
