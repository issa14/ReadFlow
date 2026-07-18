package com.inktone.data.repository

import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.provider.TtsProvider
import com.inktone.domain.provider.TtsVoice
import com.inktone.service.audio.AudioCacheManager
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour [TtsRepositoryImpl] — vérifie le routage
 * des providers, le fallback Edge→Piper, et la mise en cache.
 */
class TtsRepositoryImplTest {

    // Mocks
    private val piperProvider = mockk<TtsProvider>(relaxed = true)
    private val edgeProvider = mockk<TtsProvider>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val cache = mockk<AudioCacheManager>(relaxed = true)
    private val pronunciationRuleDao = mockk<PronunciationRuleDao>(relaxed = true)

    private lateinit var repository: TtsRepositoryImpl

    private fun makeResult(text: String, engineId: String = "piper"): SynthesisResult {
        return SynthesisResult(
            samples = FloatArray(1000) { 0.5f },
            sampleRate = 22050,
            text = text,
            voiceLabel = "Miro",
            synthesisTimeMs = 50,
            audioDurationMs = 300,
            engineId = engineId
        )
    }

    @BeforeEach
    fun setUp() {
        // Piper provider setup
        every { piperProvider.engineId } returns "piper"
        every { piperProvider.engineLabel } returns "Piper ONNX (local)"
        every { piperProvider.isAvailable } returns true
        every { piperProvider.availableVoices } returns listOf(
            TtsVoice("miro", "Miro (FR)")
        )

        // Edge provider setup
        every { edgeProvider.engineId } returns "edge"
        every { edgeProvider.engineLabel } returns "Microsoft Edge (cloud)"
        every { edgeProvider.isAvailable } returns true
        every { edgeProvider.availableVoices } returns listOf(
            TtsVoice("fr-FR-VivienneNeural", "Vivienne (FR)")
        )

        // Settings defaults
        every { settingsRepository.ttsEngine } returns flowOf("piper")
        every { settingsRepository.edgeVoice } returns flowOf("fr-FR-VivienneNeural")

        // Pronunciation rules — empty by default
        coEvery { pronunciationRuleDao.getActiveRules() } returns emptyList()

        // Cache — always miss by default
        every { cache.get(any()) } returns null
        every { cache.put(any(), any()) } just Runs

        val providers = setOf<TtsProvider>(piperProvider, edgeProvider)
        repository = TtsRepositoryImpl(providers, settingsRepository, cache, pronunciationRuleDao)
    }

    // ── Synthèse de base ───────────────────────────────

    @Test
    fun `synthèse avec le provider Piper par défaut`() = runTest {
        coEvery { piperProvider.synthesize(any(), any(), any()) } returns makeResult("Bonjour", "piper")

        val result = repository.synthesize("Bonjour", voice = 0, speed = 1.0f)

        assertEquals("Bonjour", result.text)
        assertEquals("piper", result.engineId)
        coVerify(exactly = 1) { piperProvider.synthesize(any(), any(), any()) }
        coVerify(exactly = 0) { edgeProvider.synthesize(any(), any(), any()) }
    }

    @Test
    fun `synthèse avec le provider Edge si sélectionné`() = runTest {
        every { settingsRepository.ttsEngine } returns flowOf("edge")
        // Reconstruire le repository pour prendre en compte le nouveau flow
        val providers = setOf<TtsProvider>(piperProvider, edgeProvider)
        repository = TtsRepositoryImpl(providers, settingsRepository, cache, pronunciationRuleDao)

        coEvery { edgeProvider.synthesize(any(), any(), any()) } returns makeResult("Hello", "edge")

        val result = repository.synthesize("Hello", voice = 0, speed = 1.0f)

        assertEquals("edge", result.engineId)
        coVerify(exactly = 1) { edgeProvider.synthesize(any(), any(), any()) }
    }

    // ── Fallback Edge → Piper ──────────────────────────

    @Test
    fun `fallback Edge vers Piper en cas d'erreur réseau`() = runTest {
        every { settingsRepository.ttsEngine } returns flowOf("edge")
        val providers = setOf<TtsProvider>(piperProvider, edgeProvider)
        repository = TtsRepositoryImpl(providers, settingsRepository, cache, pronunciationRuleDao)

        // Edge échoue avec une vraie erreur réseau (UnknownHostException)
        // EdgeTtsClient.isNetworkError() retourne true pour ce type
        coEvery { edgeProvider.synthesize(any(), any(), any()) } throws java.net.UnknownHostException("Unable to resolve host")

        // Piper prend le relais
        coEvery { piperProvider.synthesize(any(), any(), any()) } returns makeResult("Fallback", "piper")

        val result = repository.synthesize("Fallback", voice = 0, speed = 1.0f)

        assertEquals("piper", result.engineId, "Le fallback Piper doit être utilisé")
        coVerify(exactly = 1) { edgeProvider.synthesize(any(), any(), any()) }
        coVerify(exactly = 1) { piperProvider.synthesize(any(), any(), any()) }
    }

    @Test
    fun `erreur non-réseau sur Edge propage l'exception`() = runTest {
        every { settingsRepository.ttsEngine } returns flowOf("edge")
        val providers = setOf<TtsProvider>(piperProvider, edgeProvider)
        repository = TtsRepositoryImpl(providers, settingsRepository, cache, pronunciationRuleDao)

        // Edge échoue avec une erreur NON réseau (IllegalArgumentException)
        // EdgeTtsClient.isNetworkError() retourne false pour ce type
        coEvery { edgeProvider.synthesize(any(), any(), any()) } throws IllegalArgumentException("Invalid voice")

        try {
            repository.synthesize("Test", voice = 0, speed = 1.0f)
            fail("Devrait propager l'exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Invalid voice", e.message)
        }
    }

    // ── Cache ──────────────────────────────────────────

    @Test
    fun `cache hit retourne le résultat sans appeler le provider`() = runTest {
        val cachedResult = makeResult("Cache hit", "piper")
        every { cache.get(any()) } returns cachedResult

        val result = repository.synthesize("Cache hit", voice = 0, speed = 1.0f)

        assertEquals("Cache hit", result.text)
        coVerify(exactly = 0) { piperProvider.synthesize(any(), any(), any()) }
    }

    @Test
    fun `cache miss appelle le provider puis met en cache`() = runTest {
        coEvery { piperProvider.synthesize(any(), any(), any()) } returns makeResult("Nouveau")

        repository.synthesize("Nouveau", voice = 0, speed = 1.0f)

        verify(exactly = 1) { cache.put(any(), any()) }
    }

    // ── Liste des moteurs ──────────────────────────────

    @Test
    fun `getAvailableEngines retourne les providers triés`() = runTest {
        val engines = repository.getAvailableEngines()

        assertEquals(2, engines.size)
        assertEquals("edge", engines[0].engineId) // "edge" < "piper" alphabétiquement
        assertEquals("piper", engines[1].engineId)
    }

    // ── Résolution de provider indisponible ────────────

    @Test
    fun `fallback automatique si le provider sélectionné est indisponible`() = runTest {
        every { settingsRepository.ttsEngine } returns flowOf("edge")
        every { edgeProvider.isAvailable } returns false // Edge indisponible
        val providers = setOf<TtsProvider>(piperProvider, edgeProvider)
        repository = TtsRepositoryImpl(providers, settingsRepository, cache, pronunciationRuleDao)

        coEvery { piperProvider.synthesize(any(), any(), any()) } returns makeResult("Fallback dispo", "piper")

        val result = repository.synthesize("Fallback dispo", voice = 0, speed = 1.0f)

        assertEquals("piper", result.engineId, "Doit utiliser Piper si Edge est indisponible")
    }
}
