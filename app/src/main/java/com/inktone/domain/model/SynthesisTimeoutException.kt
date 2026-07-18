package com.inktone.domain.model

/**
 * Exception levée lorsqu'une synthèse vocale dépasse le timeout imparti.
 *
 * Le timeout est fixé à [PlaybackOrchestrator.SYNTHESIS_TIMEOUT_MS] (2000ms).
 * En cas de timeout, la phrase est skipper avec un court silence (50ms)
 * pour ne pas bloquer le pipeline de lecture.
 */
class SynthesisTimeoutException(
    text: String,
    timeoutMs: Long
) : Exception("Synthèse timeout après ${timeoutMs}ms pour: \"${text.take(50)}\"")
