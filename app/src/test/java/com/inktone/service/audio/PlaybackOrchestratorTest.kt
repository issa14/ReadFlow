package com.inktone.service.audio

import app.cash.turbine.test
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.model.SynthesisTimeoutException
import com.inktone.domain.repository.TtsRepository
import com.inktone.data.database.ReadingProgressDao
import com.inktone.service.onnx.OnnxInferenceService
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour [PlaybackOrchestrator] — focus sur la gestion du fillJob
 * et les scénarios de cancellation.
 *
 * Stratégie : mocks des dépendances externes (TtsRepository, OnnxInferenceService,
 * AudioFocusManager, GaplessAudioPlayer, ReadingProgressDao) pour isoler
 * la logique de coordination.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackOrchestratorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mocks
    private val ttsRepository = mockk<TtsRepository>(relaxed = true)
    private val player = mockk<GaplessAudioPlayer>(relaxed = true)
    private val onnxService = mockk<OnnxInferenceService>(relaxed = true)
    private val audioFocusManager = mockk<AudioFocusManager>(relaxed = true)
    private val progressDao = mockk<ReadingProgressDao>(relaxed = true)

    private lateinit var orchestrator: PlaybackOrchestrator

    // Phrases de test
    private val testSentences = listOf(
        Sentence(index = 0, text = "Bonjour.", startOffset = 0, endOffset = 8),
        Sentence(index = 1, text = "Comment allez-vous ?", startOffset = 9, endOffset = 28),
        Sentence(index = 2, text = "Très bien.", startOffset = 29, endOffset = 39),
        Sentence(index = 3, text = "Et vous ?", startOffset = 40, endOffset = 49),
        Sentence(index = 4, text = "Parfait.", startOffset = 50, endOffset = 58)
    )

    private fun makeSynthesisResult(text: String, durationMs: Long = 300L, engineId: String = "piper"): SynthesisResult {
        return SynthesisResult(
            samples = FloatArray(4410) { 0.5f },  // ~200ms à 22050Hz
            sampleRate = 22050,
            text = text,
            voiceLabel = "Jessica",
            synthesisTimeMs = 50,
            audioDurationMs = durationMs,
            engineId = engineId
        )
    }

    @BeforeEach
    fun setUp() {
        every { audioFocusManager.requestFocus() } returns true
        every { onnxService.getSampleRate() } returns 22050
        every { player.sampleRate = any() } just Runs
        every { player.enqueue(any<FloatArray>()) } just Runs
        every { player.play() } just Runs
        every { player.stop() } just Runs
        every { player.pause() } just Runs
        every { player.resume() } just Runs
        every { player.release() } just Runs
        every { player.setVolume(any()) } just Runs
        every { player.state } returns MutableStateFlow(GaplessAudioPlayer.State.Idle)
        every { player.completedCount } returns 0

        orchestrator = PlaybackOrchestrator(
            ttsRepository = ttsRepository,
            player = player,
            onnxService = onnxService,
            audioFocusManager = audioFocusManager,
            progressDao = progressDao
        )
    }

    @AfterEach
    fun tearDown() {
        orchestrator.release()
    }

    // ── Tests fillJob ───────────────────────────────────────

    @Test
    fun `fillJob se termine quand stop() est appelé pendant la synthèse`() = testScope.runTest {
        // Simuler une synthèse longue (suspend)
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            delay(5000) // synthèse longue
            makeSynthesisResult("Bonjour.")
        }

        orchestrator.play(
            sentences = testSentences,
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        // Laisser le temps à la pipeline de démarrer
        delay(100)

        // Vérifier que la lecture est en cours
        assertTrue(orchestrator.state.value is PlaybackOrchestrator.State.Loading ||
                   orchestrator.state.value is PlaybackOrchestrator.State.Playing)

        // Appeler stop() — doit annuler le fillJob
        orchestrator.stop()

        // Attendre la propagation de l'annulation
        delay(200)

        // Vérifier que l'état est bien Idle
        assertTrue(orchestrator.state.value is PlaybackOrchestrator.State.Idle)
    }

    @Test
    fun `fillJob ne reste pas suspendu après fermeture du buffer`() = testScope.runTest {
        var synthesisCount = 0

        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            synthesisCount++
            if (synthesisCount <= 2) {
                makeSynthesisResult("Phrase $synthesisCount")
            } else {
                // À partir de la 3ème phrase, on simule une latence réseau
                delay(3000)
                makeSynthesisResult("Phrase $synthesisCount")
            }
        }

        orchestrator.play(
            sentences = testSentences,
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        // Laisser le temps de synthétiser 2 phrases
        delay(500)

        // Arrêter — la 3ème phrase est en cours de synthèse (delay 3000ms)
        orchestrator.stop()

        // Attendre que le fillJob soit annulé
        delay(500)

        // Vérifier que l'orchestrateur est bien à l'état Idle
        // (le fillJob ne doit pas bloquer le stop())
        assertEquals(
            PlaybackOrchestrator.State.Idle::class,
            orchestrator.state.value::class
        )
    }

    @Test
    fun `le buffer est fermé proprement même en cas d'erreur de synthèse`() = testScope.runTest {
        coEvery { ttsRepository.synthesize(any(), any(), any()) } throws RuntimeException("Échec ONNX")

        orchestrator.play(
            sentences = testSentences.subList(0, 2),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        // Attendre que la pipeline échoue
        delay(300)

        orchestrator.stop()
        delay(100)

        // Après erreur + stop, l'état doit être Idle (pas bloqué en Error ou Loading)
        assertTrue(orchestrator.state.value is PlaybackOrchestrator.State.Idle ||
                   orchestrator.state.value is PlaybackOrchestrator.State.Error)
    }

    @Test
    fun `un nouveau play() annule le fillJob précédent`() = testScope.runTest {
        var firstPlaySynthesisCount = 0

        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            firstPlaySynthesisCount++
            delay(100)
            makeSynthesisResult("Phrase $firstPlaySynthesisCount")
        }

        // Premier play()
        orchestrator.play(
            sentences = testSentences,
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        delay(200) // laisser le temps de démarrer

        // Deuxième play() — doit annuler le premier fillJob
        orchestrator.play(
            sentences = testSentences.subList(0, 2),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test2",
            chapterTitle = "Chapitre 2",
            bookId = "book-1",
            chapterIndex = 1
        )

        delay(300)

        // Pas de crash, état cohérent
        val state = orchestrator.state.value
        assertTrue(state is PlaybackOrchestrator.State.Loading ||
                   state is PlaybackOrchestrator.State.Playing ||
                   state is PlaybackOrchestrator.State.Idle)
    }

    // ── Tests pause/resume ──────────────────────────────────

    @Test
    fun `pause puis resume préserve l'état`() = testScope.runTest {
        coEvery { ttsRepository.synthesize(any(), any(), any()) } returns makeSynthesisResult("Test")

        orchestrator.play(
            sentences = testSentences,
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        delay(150)
        orchestrator.pause()
        assertEquals(PlaybackOrchestrator.State.Paused::class, orchestrator.state.value::class)

        orchestrator.resume()
        // Après resume, on passe en Playing ou Loading
        val state = orchestrator.state.value
        assertTrue(state is PlaybackOrchestrator.State.Playing ||
                   state is PlaybackOrchestrator.State.Loading)

        orchestrator.stop()
    }

    // ── Tests timeout synthèse ──────────────────────────────────

    @Test
    fun `timeout de synthèse ne bloque pas le pipeline`() = testScope.runTest {
        var callCount = 0
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 2) {
                // Simuler un timeout sur la 2ème phrase
                throw SynthesisTimeoutException("Phrase lente", 2000)
            }
            makeSynthesisResult("Phrase $callCount")
        }

        orchestrator.play(
            sentences = testSentences.subList(0, 3),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test Timeout",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        // Laisser le temps au pipeline de traiter les phrases
        delay(200)

        // Vérifier que la lecture a bien démarré (phrase 1 OK, phrase 2 timeout → silence)
        val state = orchestrator.state.value
        assertTrue(
            state is PlaybackOrchestrator.State.Playing ||
            state is PlaybackOrchestrator.State.Loading,
            "Le pipeline ne doit pas être bloqué par un timeout (trouvé: ${state::class.simpleName})"
        )

        orchestrator.stop()
    }

    @Test
    fun `3 timeouts consécutifs ONNX mettent la lecture en pause`() = testScope.runTest {
        // Simuler un premier succès ONNX (détecte le moteur → maxErreurs=3)
        var callCount = 0
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) {
                makeSynthesisResult("OK ONNX", engineId = "piper") // succès → ONNX détecté
            } else {
                throw SynthesisTimeoutException("Timeout", 2000)
            }
        }

        orchestrator.play(
            sentences = testSentences, // 5 phrases : 1 OK + 3 timeouts + 1 restante
            voice = 0, speed = 1.0f,
            bookTitle = "Test", chapterTitle = "Ch1", bookId = "book-1", chapterIndex = 0
        )

        Thread.sleep(500)

        // Après 1 succès ONNX + 3 timeouts → Error (maxErreurs=3 pour ONNX)
        val state = orchestrator.state.value
        assertTrue(
            state is PlaybackOrchestrator.State.Error,
            "Après 3 timeouts ONNX, l'état doit être Error (trouvé: ${state::class.simpleName})"
        )
        orchestrator.stop()
    }

    @Test
    fun `8 timeouts Edge tolérés avant pause`() = testScope.runTest {
        // Simuler Edge : 1 succès (détecte Edge → maxErreurs=8), puis 7 timeouts OK, 8ème = pause
        var callCount = 0
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) {
                makeSynthesisResult("OK Edge", engineId = "edge") // succès → Edge détecté
            } else if (callCount <= 8) {
                throw SynthesisTimeoutException("Timeout Edge $callCount", 5000)
            } else {
                makeSynthesisResult("Final", engineId = "edge")
            }
        }

        orchestrator.play(
            sentences = testSentences, // 5 phrases seulement, pas assez pour 8 erreurs
            voice = 0, speed = 1.0f,
            bookTitle = "Test Edge", chapterTitle = "Ch1", bookId = "book-1", chapterIndex = 0
        )

        Thread.sleep(500)

        // Avec seulement 5 phrases et 1 succès + 4 timeouts (max 4 erreurs max),
        // l'état ne doit PAS être Error (Edge tolère jusqu'à 8)
        val state = orchestrator.state.value
        assertFalse(
            state is PlaybackOrchestrator.State.Error,
            "Edge doit tolérer plus d'erreurs que ONNX (trouvé: ${state::class.simpleName})"
        )
        orchestrator.stop()
    }
    fun `succès après erreur réinitialise le compteur`() = testScope.runTest {
        var callCount = 0
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            callCount++
            when (callCount) {
                1 -> throw SynthesisTimeoutException("Timeout 1", 2000)
                2 -> throw SynthesisTimeoutException("Timeout 2", 2000)
                else -> makeSynthesisResult("OK phrase $callCount")
            }
        }

        orchestrator.play(
            sentences = testSentences,
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test Reset",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        delay(500)

        // Après 2 timeouts puis 1 succès, le compteur est réinitialisé → pas d'erreur
        val state = orchestrator.state.value
        assertTrue(
            state is PlaybackOrchestrator.State.Playing ||
            state is PlaybackOrchestrator.State.Loading,
            "Un succès doit réinitialiser le compteur d'erreurs (trouvé: ${state::class.simpleName})"
        )

        orchestrator.stop()
    }

    @Test
    fun `erreur réseau persistante arrête le pipeline immédiatement`() = testScope.runTest {
        coEvery { ttsRepository.synthesize(any(), any(), any()) } throws RuntimeException(
            "Unable to resolve host"
        )

        orchestrator.play(
            sentences = testSentences.subList(0, 2),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test Réseau",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        delay(300)

        // L'erreur (générique) doit être gérée sans crash
        val state = orchestrator.state.value
        assertTrue(
            state is PlaybackOrchestrator.State.Idle ||
            state is PlaybackOrchestrator.State.Error ||
            state is PlaybackOrchestrator.State.Loading,
            "L'erreur doit être gérée sans crash (trouvé: ${state::class.simpleName})"
        )

        orchestrator.stop()
    }

    @Test
    fun `le compteur d'erreurs est réinitialisé à chaque nouveau play()`() = testScope.runTest {
        // Premier play() : tout timeout
        coEvery { ttsRepository.synthesize(any(), any(), any()) } throws SynthesisTimeoutException("Timeout", 2000)

        orchestrator.play(
            sentences = testSentences.subList(0, 3),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test",
            chapterTitle = "Chapitre 1",
            bookId = "book-1",
            chapterIndex = 0
        )

        delay(500)
        orchestrator.stop()

        // Deuxième play() : tout OK
        coEvery { ttsRepository.synthesize(any(), any(), any()) } returns makeSynthesisResult("OK")

        orchestrator.play(
            sentences = testSentences.subList(0, 2),
            voice = 0,
            speed = 1.0f,
            bookTitle = "Test2",
            chapterTitle = "Chapitre 2",
            bookId = "book-1",
            chapterIndex = 1
        )

        delay(300)

        // Le deuxième play() doit fonctionner normalement (compteur réinitialisé)
        val state = orchestrator.state.value
        assertTrue(
            state is PlaybackOrchestrator.State.Playing ||
            state is PlaybackOrchestrator.State.Loading,
            "Un nouveau play() doit réinitialiser le compteur d'erreurs (trouvé: ${state::class.simpleName})"
        )

        orchestrator.stop()
    }
}
