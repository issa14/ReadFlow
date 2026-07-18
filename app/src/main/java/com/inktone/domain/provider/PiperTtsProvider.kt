package com.inktone.domain.provider

import com.inktone.domain.model.SynthesisResult
import com.inktone.service.onnx.OnnxInferenceService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider TTS utilisant le moteur ONNX local Sherpa-ONNX / Piper VITS.
 *
 * Wrapper autour de [OnnxInferenceService] qui adapte l'API existante
 * au contrat [TtsProvider], permettant une interchangeabilité avec
 * d'autres moteurs (Edge TTS, etc.).
 */
@Singleton
class PiperTtsProvider @Inject constructor(
    private val onnxService: OnnxInferenceService
) : TtsProvider {

    override val engineId: String = "piper"
    override val engineLabel: String = "Piper ONNX (local)"

    override val isAvailable: Boolean
        get() = onnxService.isInitialized

    override val availableVoices: List<TtsVoice> = OnnxInferenceService.Voice.entries.map { voice ->
        TtsVoice(id = voice.name.lowercase(), label = voice.label)
    }

    override suspend fun synthesize(
        text: String,
        voice: String,
        speed: Float
    ): SynthesisResult {
        val voiceEnum = OnnxInferenceService.Voice.entries
            .find { it.name.lowercase() == voice.lowercase() }
            ?: OnnxInferenceService.Voice.MIRO

        val result = onnxService.synthesize(text, voiceEnum, speed)
        return result.copy(engineId = engineId)
    }
}
