package com.inktone.domain.repository

import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.provider.TtsProvider

interface TtsRepository {
    /** Synthétise une phrase en français. */
    suspend fun synthesize(
        text: String,
        voice: Int = 0,
        speed: Float = 1.0f
    ): SynthesisResult

    /** Liste des moteurs TTS disponibles. */
    fun getAvailableEngines(): List<TtsProvider>

    /**
     * Retourne le moteur TTS correspondant à [engineId],
     * ou `null` si aucun moteur ne correspond.
     */
    fun getEngine(engineId: String): TtsProvider?
}
