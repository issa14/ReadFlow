package com.readflow.domain.usecase

import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.model.SynthesisTimeoutException
import com.readflow.domain.repository.TtsRepository
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour [SynthesizeUseCase] — vérifie le timeout
 * de synthèse et la levée de [SynthesisTimeoutException].
 */
class SynthesizeUseCaseTest {

    private val ttsRepository = mockk<TtsRepository>(relaxed = true)
    private lateinit var useCase: SynthesizeUseCase

    @BeforeEach
    fun setUp() {
        useCase = SynthesizeUseCase(ttsRepository)
    }

    private fun makeResult(text: String): SynthesisResult {
        return SynthesisResult(
            samples = FloatArray(1000) { 0.5f },
            sampleRate = 22050,
            text = text,
            voiceLabel = "Miro",
            synthesisTimeMs = 100,
            audioDurationMs = 300,
            engineId = "piper"
        )
    }

    // ── Tests de base ──────────────────────────────────────

    @Test
    fun `synthèse réussie retourne le résultat`() = runTest {
        coEvery { ttsRepository.synthesize(any(), any(), any()) } returns makeResult("Bonjour")

        val result = useCase("Bonjour")
        assertEquals("Bonjour", result.text)
        assertEquals("piper", result.engineId)
    }

    @Test
    fun `texte vide lève IllegalArgumentException`() = runTest {
        try {
            useCase("   ")
            fail("Devrait lever IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Attendu
        }
    }

    @Test
    fun `texte avec uniquement des espaces lève IllegalArgumentException`() = runTest {
        try {
            useCase("")
            fail("Devrait lever IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Attendu
        }
    }

    // ── Tests timeout ──────────────────────────────────────

    @Test
    fun `synthèse qui dépasse 2 secondes lève SynthesisTimeoutException`() = runTest {
        // Simuler une synthèse qui bloque 3 secondes (au-delà du timeout de 2s)
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            delay(3000) // plus long que TIMEOUT_MS (2000)
            makeResult("Trop lent")
        }

        try {
            useCase("Trop lent")
            fail("Devrait lever SynthesisTimeoutException")
        } catch (e: SynthesisTimeoutException) {
            assertTrue(e.message!!.contains("2000ms"))
            assertTrue(e.message!!.contains("Trop lent"))
        }
    }

    @Test
    fun `synthèse sous le timeout réussit normalement`() = runTest {
        // 500ms < 2000ms timeout → OK
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            delay(500)
            makeResult("Rapide")
        }

        val result = useCase("Rapide")
        assertEquals("Rapide", result.text)
    }

    @Test
    fun `synthèse exactement au timeout réussit`() = runTest {
        // 1999ms < 2000ms → OK (à la limite)
        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            delay(1999)
            makeResult("Limite")
        }

        val result = useCase("Limite")
        assertEquals("Limite", result.text)
    }

    @Test
    fun `le message d'exception contient le texte tronqué à 50 caractères`() = runTest {
        val longText = "A".repeat(100)

        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            delay(3000)
            makeResult(longText)
        }

        try {
            useCase(longText)
            fail("Devrait lever SynthesisTimeoutException")
        } catch (e: SynthesisTimeoutException) {
            // Le texte dans le message doit être tronqué
            assertTrue(e.message!!.length < 120)
        }
    }

    // ── Tests paramètres ───────────────────────────────────

    @Test
    fun `les paramètres voice et speed sont transmis au repository`() = runTest {
        var capturedVoice = 0
        var capturedSpeed = 1.0f

        coEvery { ttsRepository.synthesize(any(), any(), any()) } coAnswers {
            capturedVoice = secondArg()
            capturedSpeed = thirdArg()
            makeResult("Test")
        }

        useCase("Test", voice = 2, speed = 1.5f)

        assertEquals(2, capturedVoice)
        assertEquals(1.5f, capturedSpeed)
    }
}
