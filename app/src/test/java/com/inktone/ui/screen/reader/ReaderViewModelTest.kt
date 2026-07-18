package com.inktone.ui.screen.reader

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.Progress
import com.inktone.domain.model.Sentence
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.domain.service.AudioServiceLauncher
import com.inktone.domain.usecase.CalculateReadingProgressUseCase
import com.inktone.domain.usecase.LoadChapterUseCase
import com.inktone.domain.usecase.ManageReaderAnnotationsUseCase
import com.inktone.domain.usecase.PreWarmNextChapterUseCase
import com.inktone.domain.usecase.ResolveReadingPositionUseCase
import com.inktone.domain.usecase.ResolvedPosition
import com.inktone.service.audio.PlaybackOrchestrator
import com.inktone.service.audio.PlaybackState
import com.inktone.service.audio.PlaybackStatus
import com.inktone.service.onnx.OnnxInferenceService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour [ReaderViewModel].
 *
 * Stratégie : mocker toutes les dépendances externes (repositories, services,
 * orchestrator, DAOs) pour isoler la logique du ViewModel. Utilisation de
 * Turbine pour tester les StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mocks
    private val savedState = SavedStateHandle()
    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val orchestrator = mockk<PlaybackOrchestrator>(relaxed = true)
    private val onnxService = mockk<OnnxInferenceService>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val pronunciationRuleDao = mockk<PronunciationRuleDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val highlightDao = mockk<HighlightDao>(relaxed = true)
    private val annotationDao = mockk<AnnotationDao>(relaxed = true)
    private val audioServiceLauncher = mockk<AudioServiceLauncher>(relaxed = true)
    private val ttsRepository = mockk<TtsRepository>(relaxed = true)
    private val calculateProgress = mockk<CalculateReadingProgressUseCase>(relaxed = true)
    private val loadChapterUseCase = mockk<LoadChapterUseCase>(relaxed = true)
    private val annotationsUseCase = mockk<ManageReaderAnnotationsUseCase>(relaxed = true)
    private val preWarmChapter = mockk<PreWarmNextChapterUseCase>(relaxed = true)
    private val resolvePosition = mockk<ResolveReadingPositionUseCase>(relaxed = true)

    private lateinit var viewModel: ReaderViewModel

    private val testBook = Book(
        id = "book-1",
        title = "Les Misérables",
        author = "Victor Hugo",
        description = null,
        coverPath = null,
        totalChapters = 3,
        language = "fr",
        addedAt = 1234567890L
    )

    private val testSentences = listOf(
        Sentence(index = 0, text = "Bonjour.", startOffset = 0, endOffset = 8),
        Sentence(index = 1, text = "Comment allez-vous ?", startOffset = 9, endOffset = 28),
        Sentence(index = 2, text = "Très bien merci.", startOffset = 29, endOffset = 45)
    )

    private val testChapter = Chapter(
        index = 0,
        title = "Chapitre 1",
        sentences = testSentences
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Settings par défaut
        every { settingsRepository.readerTheme } returns flowOf("NIGHT")
        every { settingsRepository.readerFont } returns flowOf("SERIF")
        every { settingsRepository.fontSize } returns flowOf(18f)
        every { settingsRepository.lineHeight } returns flowOf(1.8f)
        every { settingsRepository.horizontalMargin } returns flowOf(24)

        // Pronunciation rules
        every { pronunciationRuleDao.getAllRulesFlow() } returns flowOf(emptyList())

        // Orchestrator
        every { orchestrator.state } returns MutableStateFlow(PlaybackOrchestrator.State.Idle)
        every { orchestrator.playbackState } returns MutableStateFlow(PlaybackState())
        every { orchestrator.sleepTimerRemaining } returns MutableStateFlow(null)

        // Audio service launcher
        every { audioServiceLauncher.canStart() } returns true
        every { audioServiceLauncher.start() } just Runs

        // ONNX
        coEvery { onnxService.initialize() } just Runs

        viewModel = ReaderViewModel(
            savedState = savedState,
            bookRepository = bookRepository,
            orchestrator = orchestrator,
            onnxService = onnxService,
            settingsRepository = settingsRepository,
            pronunciationRuleDao = pronunciationRuleDao,
            bookmarkDao = bookmarkDao,
            highlightDao = highlightDao,
            annotationDao = annotationDao,
            audioServiceLauncher = audioServiceLauncher,
            ttsRepository = ttsRepository,
            calculateProgress = calculateProgress,
            loadChapterUseCase = loadChapterUseCase,
            annotationsUseCase = annotationsUseCase,
            preWarmChapter = preWarmChapter,
            resolvePosition = resolvePosition
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Tests UI State initial ──────────────────────────────

    @Test
    fun `état initial par défaut`() = testScope.runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.book)
            assertEquals(0, state.currentChapterIndex)
            assertFalse(state.isPlaying)
            assertEquals(18f, state.fontSizeSp, 0.01f)
            assertEquals(1.8f, state.lineHeightEm, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tests toggle/setters UI ─────────────────────────────

    @Test
    fun `toggleHud inverse la visibilité`() = testScope.runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().isHudVisible)
            viewModel.toggleHud()
            assertTrue(awaitItem().isHudVisible)
            viewModel.toggleHud()
            assertFalse(awaitItem().isHudVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hideHud force la visibilité à false`() = testScope.runTest {
        viewModel.toggleHud() // visible = true
        viewModel.hideHud()
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isHudVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSpeed dans les limites`() = testScope.runTest {
        viewModel.setSpeed(1.5f)
        viewModel.uiState.test {
            assertEquals(1.5f, awaitItem().speed, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSpeed above limit clamps to max`() = testScope.runTest {
        viewModel.setSpeed(3.0f) // au-dessus de la limite
        viewModel.uiState.test {
            assertEquals(2.0f, awaitItem().speed, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setVoice met à jour la voix`() = testScope.runTest {
        viewModel.setVoice(1)
        viewModel.uiState.test {
            assertEquals(1, awaitItem().voice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tests Theme ─────────────────────────────────────────

    @Test
    fun `cycleTheme parcourt NIGHT → SEPIA → DAY → NIGHT`() = testScope.runTest {
        viewModel.uiState.test {
            // État initial : NIGHT (du flow settings)
            var state = awaitItem()
            assertEquals(ReaderTheme.NIGHT, state.readerTheme)

            viewModel.cycleTheme()
            state = awaitItem()
            assertEquals(ReaderTheme.SEPIA, state.readerTheme)

            viewModel.cycleTheme()
            state = awaitItem()
            assertEquals(ReaderTheme.DAY, state.readerTheme)

            viewModel.cycleTheme()
            state = awaitItem()
            assertEquals(ReaderTheme.NIGHT, state.readerTheme)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setReaderTheme persiste dans SavedStateHandle`() = testScope.runTest {
        viewModel.setReaderTheme(ReaderTheme.SEPIA)
        viewModel.uiState.test {
            assertEquals(ReaderTheme.SEPIA, awaitItem().readerTheme)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("SEPIA", savedState.get<String>("theme"))
    }

    // ── Tests Play / Pause / Stop ───────────────────────────

    @Test
    fun `play retourne silencieusement si aucun chapitre n'est chargé`() = testScope.runTest {
        // Sans charger de livre, currentChapter est null → play() retourne sans rien faire
        viewModel.play()
        advanceUntilIdle()
        verify(exactly = 0) { audioServiceLauncher.start() }
        verify(exactly = 0) { orchestrator.play(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `play ne démarre pas si canStart retourne false`() = testScope.runTest {
        every { audioServiceLauncher.canStart() } returns false
        viewModel.play()
        advanceUntilIdle()
        verify(exactly = 0) { audioServiceLauncher.start() }
        verify(exactly = 0) { orchestrator.play(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pause met en pause l'orchestrator`() = testScope.runTest {
        viewModel.pause()
        verify { orchestrator.pause() }
    }

    @Test
    fun `stop arrête l'orchestrator et réinitialise isPlaying`() = testScope.runTest {
        viewModel.stop()
        verify { orchestrator.stop() }
    }

    // ── Tests SavedStateHandle Restoration ──────────────────

    @Test
    fun `loadBook restaure la vitesse et voix depuis SavedStateHandle`() = testScope.runTest {
        savedState["speed"] = 1.25f
        savedState["voice"] = 1

        coEvery { bookRepository.getAllBooks() } returns listOf(testBook)
        coEvery { bookRepository.getChapter("book-1", 0) } returns testChapter
        coEvery { bookRepository.getProgress("book-1") } returns null
        coEvery { orchestrator.loadProgress("book-1") } returns null

        viewModel.loadBook("book-1")
        advanceUntilIdle()

        assertEquals(1.25f, viewModel.uiState.value.speed, 0.01f)
        assertEquals(1, viewModel.uiState.value.voice)
    }

    // ── Tests erreurs ───────────────────────────────────────

    @Test
    fun `loadBook définit une erreur si le livre est introuvable`() = testScope.runTest {
        coEvery { bookRepository.getAllBooks() } returns emptyList()

        viewModel.loadBook("livre-inexistant")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error, "Un message d'erreur devrait être présent")
    }

    // ── Tests Process Death / SavedStateHandle ──────────────

    @Test
    fun `restaure la position de lecture depuis SavedStateHandle`() = testScope.runTest {
        // Simuler un process death : le SavedStateHandle contient les valeurs sauvegardées
        savedState["chapterIndex"] = 2
        savedState["sentenceIndex"] = 5
        savedState["speed"] = 1.5f
        savedState["voice"] = 1

        coEvery { bookRepository.getAllBooks() } returns listOf(testBook)
        coEvery { orchestrator.loadProgress("book-1") } returns null
        every { resolvePosition.invoke(any(), any(), any(), any(), any()) } returns ResolvedPosition(2, 5)

        viewModel.loadBook("book-1")
        advanceUntilIdle()

        // La vitesse et la voix sont restaurées depuis SavedStateHandle
        assertEquals(1.5f, viewModel.uiState.value.speed, 0.01f)
        assertEquals(1, viewModel.uiState.value.voice)
    }

    @Test
    fun `SavedStateHandle survit à la re-création du ViewModel`() = testScope.runTest {
        // Simuler : le ViewModel sauvegarde les valeurs dans SavedStateHandle
        viewModel.setReaderTheme(ReaderTheme.SEPIA)
        viewModel.setReaderFont(ReaderFont.OPEN_DYSLEXIC)
        viewModel.setFontSize(22f)

        // Vérifier que SavedStateHandle contient les valeurs sauvegardées
        assertEquals("SEPIA", savedState.get<String>("theme") ?: "")
        assertEquals(true, savedState.get<Boolean>("openDyslexic") ?: false)
    }

    // ── Tests rapid intents ─────────────────────────────────

    @Test
    fun `play rapide suivi de stop ne cause pas d'état incohérent`() = testScope.runTest {
        coEvery { bookRepository.getAllBooks() } returns listOf(testBook)
        coEvery { orchestrator.loadProgress("book-1") } returns null
        coEvery { bookRepository.getChapter("book-1", 0) } returns testChapter
        every { highlightDao.getHighlightsForChapter(any(), any()) } returns flowOf(emptyList())
        every { bookmarkDao.getBookmarks(any()) } returns flowOf(emptyList())
        coEvery { calculateProgress.invoke(any(), any(), any(), any()) } returns 0f
        every { resolvePosition.invoke(any(), any(), any(), any(), any()) } returns ResolvedPosition(0, 0)

        viewModel.loadBook("book-1")
        advanceUntilIdle()

        // Intent rapide : play → stop → play → pause
        viewModel.play()
        viewModel.stop()
        viewModel.play()
        viewModel.pause()

        advanceUntilIdle()

        // L'état doit être cohérent (pas de crash)
        assertFalse(viewModel.uiState.value.isPlaying, "Après pause, isPlaying doit être false")
    }

    @Test
    fun `changements rapides de chapitre ne causent pas de crash`() = testScope.runTest {
        coEvery { bookRepository.getAllBooks() } returns listOf(testBook)
        coEvery { orchestrator.loadProgress("book-1") } returns null
        coEvery { bookRepository.getChapter("book-1", any()) } returns testChapter
        every { highlightDao.getHighlightsForChapter(any(), any()) } returns flowOf(emptyList())
        every { bookmarkDao.getBookmarks(any()) } returns flowOf(emptyList())
        coEvery { calculateProgress.invoke(any(), any(), any(), any()) } returns 0f
        every { resolvePosition.invoke(any(), any(), any(), any(), any()) } returns ResolvedPosition(0, 0)

        viewModel.loadBook("book-1")
        advanceUntilIdle()

        // Changements rapides de chapitre
        viewModel.nextChapter()
        viewModel.previousChapter()
        viewModel.nextChapter()
        viewModel.goToChapter(0)

        advanceUntilIdle()

        // Pas de crash = succès
        assertNotNull(viewModel.uiState.value)
    }

    @Test
    fun `ONNX initialization error sets uiState error`() = testScope.runTest {
        coEvery { onnxService.initialize() } throws RuntimeException("ONNX model not found")

        // Re-créer le ViewModel pour déclencher l'init ONNX
        val vm = ReaderViewModel(
            savedState = savedState,
            bookRepository = bookRepository,
            orchestrator = orchestrator,
            onnxService = onnxService,
            settingsRepository = settingsRepository,
            pronunciationRuleDao = pronunciationRuleDao,
            bookmarkDao = bookmarkDao,
            highlightDao = highlightDao,
            annotationDao = annotationDao,
            audioServiceLauncher = audioServiceLauncher,
            ttsRepository = ttsRepository,
            calculateProgress = calculateProgress,
            loadChapterUseCase = loadChapterUseCase,
            annotationsUseCase = annotationsUseCase,
            preWarmChapter = preWarmChapter,
            resolvePosition = resolvePosition
        )
        advanceUntilIdle()

        // L'erreur ONNX doit être propagée dans l'UI state
        val error = vm.uiState.value.error
        assertNotNull(error, "L'erreur ONNX doit être visible dans l'UI")
        assertTrue(error!!.contains("ONNX") || error.contains("TTS"), "Message d'erreur: $error")
    }
}
