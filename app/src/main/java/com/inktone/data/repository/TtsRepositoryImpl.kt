package com.inktone.data.repository

import android.util.Log
import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.provider.TtsProvider
import com.inktone.domain.repository.TtsRepository
import com.inktone.service.audio.AudioCacheManager
import com.inktone.service.edge.EdgeTtsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsRepositoryImpl @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TtsProvider>,
    private val settingsRepository: SettingsRepository,
    private val cache: AudioCacheManager,
    private val pronunciationRuleDao: PronunciationRuleDao
) : TtsRepository {

    companion object {
        private const val TAG = "TtsRepository"
        private const val DEFAULT_ENGINE = "piper"
    }

    /** Cache du provider sélectionné (évite les lookups répétés). */
    @Volatile
    private var cachedProvider: TtsProvider? = null

    /** Identifiant du moteur correspondant au provider en cache. */
    @Volatile
    private var cachedEngineId: String? = null

    override suspend fun synthesize(
        text: String,
        voice: Int,
        speed: Float
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val provider = resolveProvider()
        val voiceId = resolveVoiceId(provider, voice)
        val correctedText = applyPronunciationRules(text)

        try {
            val key = "${provider.engineId}|${correctedText.trim()}|${voiceId}|${"%.2f".format(speed)}"
            cache.get(key)?.let { return@withContext it }

            val result = provider.synthesize(correctedText, voiceId, speed)
            cache.put(key, result)
            result
        } catch (e: Exception) {
            // Si le provider actif est Edge et que l'erreur est réseau → fallback Piper
            if (provider.engineId == "edge" && EdgeTtsClient.isNetworkError(e)) {
                Log.w(TAG, "Edge TTS échec réseau → fallback automatique sur Piper (session)")
                val piper = providers.find { it.engineId == "piper" }
                if (piper != null && piper.isAvailable) {
                    // Invalider le cache provider pour cette session
                    cachedProvider = piper
                    cachedEngineId = "piper"

                    val piperVoiceId = resolveVoiceId(piper, voice)
                    val piperKey = "piper|${correctedText.trim()}|${piperVoiceId}|${"%.2f".format(speed)}"
                    cache.get(piperKey)?.let { return@withContext it }

                    val piperResult = piper.synthesize(correctedText, piperVoiceId, speed)
                    cache.put(piperKey, piperResult)
                    return@withContext piperResult
                }
            }
            // Erreur non-réseau ou Piper indisponible → propager
            throw e
        }
    }

    override fun getAvailableEngines(): List<TtsProvider> {
        return providers.toList().sortedBy { it.engineId }
    }

    override fun getEngine(engineId: String): TtsProvider? {
        return providers.find { it.engineId == engineId }
    }

    /**
     * Résout le provider TTS actif à partir des préférences utilisateur.
     *
     * Lit [SettingsRepository.ttsEngine] (DataStore) pour déterminer
     * le moteur sélectionné par l'utilisateur.
     *
     * Stratégie de fallback :
     * 1. Cache valide → retourne immédiatement
     * 2. Lit le moteur sélectionné dans DataStore (défaut "piper")
     * 3. Si le moteur est trouvé ET disponible → retourne
     * 4. Sinon, cherche le premier provider disponible
     * 5. En dernier recours, retourne "piper" (même indisponible)
     */
    private suspend fun resolveProvider(): TtsProvider {
        // Cache valide : le provider est déjà sélectionné et disponible
        val cached = cachedProvider
        val cachedId = cachedEngineId
        if (cached != null && cachedId != null && cached.isAvailable) {
            return cached
        }

        // Lire le moteur sélectionné dans DataStore
        val selectedEngineId = settingsRepository.ttsEngine.first()

        // Chercher le provider correspondant
        val selected = providers.find { it.engineId == selectedEngineId && it.isAvailable }
        if (selected != null) {
            Log.d(TAG, "Provider sélectionné: $selectedEngineId (disponible)")
            cachedProvider = selected
            cachedEngineId = selectedEngineId
            return selected
        }

        // Fallback : premier provider disponible, sinon piper
        val fallback = providers.firstOrNull { it.isAvailable }
            ?: providers.find { it.engineId == DEFAULT_ENGINE }
            ?: providers.first()

        if (fallback.engineId != selectedEngineId) {
            Log.w(TAG, "Provider '$selectedEngineId' indisponible → fallback sur '${fallback.engineId}'")
        }

        cachedProvider = fallback
        cachedEngineId = fallback.engineId
        return fallback
    }

    /**
     * Convertit l'ancien identifiant numérique [voice] (sid Piper)
     * en identifiant string utilisé par les providers.
     *
     * Pour Piper : convertit le sid en nom de voix.
     * Pour Edge  : lit [SettingsRepository.edgeVoice] depuis DataStore.
     */
    private suspend fun resolveVoiceId(provider: TtsProvider, voice: Int): String {
        return when (provider.engineId) {
            "piper" -> {
                val voiceEnum = com.inktone.service.onnx.OnnxInferenceService.Voice.entries
                    .find { it.sid == voice }
                voiceEnum?.name?.lowercase() ?: provider.availableVoices.first().id
            }
            "edge" -> {
                // Lire la voix Edge sélectionnée dans DataStore
                settingsRepository.edgeVoice.first()
            }
            else -> {
                provider.availableVoices.firstOrNull()?.id ?: "fr-FR-VivienneNeural"
            }
        }
    }

    /**
     * Applique les règles de prononciation actives au texte avant synthèse.
     */
    private suspend fun applyPronunciationRules(text: String): String {
        return try {
            val rules = pronunciationRuleDao.getActiveRules()
            rules.fold(text) { currentText, rule ->
                if (rule.isRegex) {
                    try {
                        Regex(rule.pattern, RegexOption.IGNORE_CASE).replace(currentText, rule.replacement)
                    } catch (e: Exception) {
                        currentText
                    }
                } else {
                    currentText.replace(
                        Regex(Regex.escape(rule.pattern), RegexOption.IGNORE_CASE),
                        rule.replacement
                    )
                }
            }
        } catch (e: Exception) {
            text
        }
    }
}

