package com.inktone.domain.provider

import com.inktone.domain.model.SynthesisResult
import com.inktone.service.edge.EdgeTtsClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider TTS utilisant Microsoft Edge TTS (cloud, gratuit).
 *
 * Wrapper autour de [EdgeTtsClient] qui adapte l'API WebSocket
 * au contrat [TtsProvider].
 *
 * Voix disponibles :
 * - fr-FR-VivienneNeural (féminine, défaut)
 * - fr-FR-HenriNeural    (masculine)
 */
@Singleton
class EdgeTtsProvider @Inject constructor(
    private val edgeTtsClient: EdgeTtsClient
) : TtsProvider {

    override val engineId: String = "edge"
    override val engineLabel: String = "Microsoft Edge (cloud)"

    override val isAvailable: Boolean
        get() = edgeTtsClient.isAvailable

    override val availableVoices: List<TtsVoice> = EdgeTtsClient.VOICES.map { name ->
        TtsVoice(
            id = name,
            label = when (name) {
                "fr-FR-VivienneNeural" -> "Vivienne (FR)"
                "fr-FR-HenriNeural"    -> "Henri (FR)"
                else                   -> name
            }
        )
    }

    override suspend fun synthesize(
        text: String,
        voice: String,
        speed: Float
    ): SynthesisResult {
        return edgeTtsClient.synthesize(text, voice, speed)
    }
}
