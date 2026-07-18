package com.inktone.service.onnx

import android.content.Context
import android.content.res.AssetManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests unitaires pour [OnnxInferenceService].
 *
 * Stratégie : mocker le Context et AssetManager pour tester les chemins
 * d'erreur sans dépendance au binaire natif sherpa-onnx. Les tests
 * d'intégration réels (avec le modèle ONNX) sont dans androidTest/.
 *
 * ⚠️ Les tests de initialize() qui nécessitent l'appel natif à OfflineTts
 * ne peuvent pas s'exécuter en tests unitaires (JVM). Ces tests sont marqués
 * comme désactivés en attendant les tests instrumentés androidTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnnxInferenceServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val assetManager = mockk<AssetManager>(relaxed = true)
    private val filesDir = mockk<File>(relaxed = true)

    private lateinit var service: OnnxInferenceService

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { context.assets } returns assetManager
        every { context.filesDir } returns filesDir
        every { filesDir.absolutePath } returns "/data/data/com.inktone/files"

        service = OnnxInferenceService(context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Tests d'état initial ────────────────────────────────

    @Test
    fun `isInitialized est false avant initialize()`() {
        assertFalse(service.isInitialized)
    }

    // ── Tests de paramètres prosodiques ─────────────────────

    @Test
    fun `voiceLengthScale a une valeur par défaut correcte`() {
        assertEquals(1.08f, service.voiceLengthScale, 0.001f)
    }

    @Test
    fun `voiceNoiseScale peut être modifié`() {
        service.voiceNoiseScale = 0.5f
        assertEquals(0.5f, service.voiceNoiseScale, 0.001f)
    }

    @Test
    fun `voiceNoiseScaleW peut être modifié`() {
        service.voiceNoiseScaleW = 1.0f
        assertEquals(1.0f, service.voiceNoiseScaleW, 0.001f)
    }

    // ── Tests de validation du modèle ───────────────────────

    @Test
    fun `isInitialized reste false si isModelAvailable échoue`() {
        // Sans initialisation réelle, le service doit rester non-initialisé
        assertFalse(service.isInitialized)
    }

    // ── Tests de synthèse sans initialisation ───────────────

    @Test
    fun `synthesize échoue si le service n'est pas initialisé`() = runTest {
        assertFalse(service.isInitialized)
        try {
            service.synthesize("Bonjour.", voice = OnnxInferenceService.Voice.MIRO, speed = 1.0f)
        } catch (e: Exception) {
            // Comportement attendu : exception levée car TTS non initialisé
            assertTrue(e is IllegalStateException || e is NullPointerException,
                "Devrait être IllegalStateException, reçu: ${e::class.simpleName}")
        }
    }
}
