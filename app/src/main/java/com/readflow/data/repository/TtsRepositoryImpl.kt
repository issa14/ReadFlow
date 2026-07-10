package com.readflow.data.repository

import com.readflow.data.database.PronunciationRuleDao
import com.readflow.domain.model.SynthesisResult
import com.readflow.domain.repository.TtsRepository
import com.readflow.service.audio.AudioCacheManager
import com.readflow.service.onnx.OnnxInferenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsRepositoryImpl @Inject constructor(
    private val inferenceService: OnnxInferenceService,
    private val cache: AudioCacheManager,
    private val pronunciationRuleDao: PronunciationRuleDao
) : TtsRepository {

    override suspend fun synthesize(
        text: String,
        voice: Int,
        speed: Float
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val voiceEnum = OnnxInferenceService.Voice.entries
            .find { it.sid == voice } ?: OnnxInferenceService.Voice.MIRO

        val correctedText = applyPronunciationRules(text)

        val key = "${correctedText.trim()}|${voiceEnum.sid}|${"%.2f".format(speed)}"

        cache.get(key)?.let { return@withContext it }

        val result = inferenceService.synthesize(correctedText, voiceEnum, speed)
        cache.put(key, result)
        result
    }

    /**
     * Applique les règles de prononciation actives au texte avant synthèse.
     * 
     * Les règles sont appliquées séquentiellement : d'abord les règles par
     * expression régulière (isRegex=true), puis les règles de remplacement
     * simple insensible à la casse (isRegex=false).
     */
    private suspend fun applyPronunciationRules(text: String): String {
        return try {
            val rules = pronunciationRuleDao.getActiveRules()
            rules.fold(text) { currentText, rule ->
                if (rule.isRegex) {
                    try {
                        Regex(rule.pattern, RegexOption.IGNORE_CASE).replace(currentText, rule.replacement)
                    } catch (e: Exception) {
                        // Pattern regex invalide, on ignore cette règle
                        currentText
                    }
                } else {
                    // Remplacement simple insensible à la casse
                    currentText.replace(
                        Regex(Regex.escape(rule.pattern), RegexOption.IGNORE_CASE),
                        rule.replacement
                    )
                }
            }
        } catch (e: Exception) {
            // En cas d'erreur d'accès à la base, on retourne le texte original
            text
        }
    }
}
