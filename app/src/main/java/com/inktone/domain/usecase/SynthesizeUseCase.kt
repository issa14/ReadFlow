package com.inktone.domain.usecase

import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.model.SynthesisTimeoutException
import com.inktone.domain.repository.TtsRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Synthétise un texte en audio via le moteur TTS. */
class SynthesizeUseCase @Inject constructor(
    private val ttsRepository: TtsRepository
) {

    companion object {
        /** Timeout maximum pour une synthèse (2 secondes). */
        const val TIMEOUT_MS = 2000L
    }

    /**
     * Synthétise [text] en audio avec un timeout de [TIMEOUT_MS].
     *
     * Si la synthèse dépasse le timeout, une [SynthesisTimeoutException]
     * est levée. L'appelant (typiquement [PlaybackOrchestrator]) doit
     * skipper la phrase et injecter un court silence (50ms).
     *
     * @throws SynthesisTimeoutException si la synthèse dépasse 2 secondes
     * @throws IllegalArgumentException si [text] est vide
     */
    suspend operator fun invoke(
        text: String,
        voice: Int = 0,
        speed: Float = 1.0f
    ): SynthesisResult {
        require(text.isNotBlank()) { "Le texte ne peut pas être vide" }

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            ttsRepository.synthesize(text, voice, speed)
        }

        if (result == null) {
            throw SynthesisTimeoutException(text, TIMEOUT_MS)
        }

        return result
    }
}
