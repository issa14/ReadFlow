package com.readflow.data.repository

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
    private val cache: AudioCacheManager
) : TtsRepository {

    override suspend fun synthesize(
        text: String,
        voice: Int,
        speed: Float
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val voiceEnum = OnnxInferenceService.Voice.entries
            .find { it.sid == voice } ?: OnnxInferenceService.Voice.MIRO

        val key = "${text.trim()}|${voiceEnum.sid}|${"%.2f".format(speed)}"

        cache.get(key)?.let { return@withContext it }

        val result = inferenceService.synthesize(text, voiceEnum, speed)
        cache.put(key, result)
        result
    }
}
