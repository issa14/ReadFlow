package com.readflow.domain.model

/**
 * Résultat d'une synthèse vocale.
 * Appartient au domain layer (pas de dépendance Android).
 */
data class SynthesisResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val text: String,
    val voiceLabel: String,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long
) {
    val realTimeFactor: Float
        get() = synthesisTimeMs.toFloat() / audioDurationMs.coerceAtLeast(1)

    /**
     * Hash pré-calculé une seule fois à la construction.
     *
     * Évite l'appel O(n) à [FloatArray.contentHashCode] à chaque
     * insertion/recherche dans une HashMap/LruCache. Le tableau
     * [samples] est traité comme immuable après construction.
     */
    private val _hashCode: Int =
        31 * (31 * (31 * (31 * (31 * samples.contentHashCode() + sampleRate) +
                text.hashCode()) + voiceLabel.hashCode()) +
                synthesisTimeMs.hashCode()) + audioDurationMs.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesisResult) return false
        if (_hashCode != other._hashCode) return false
        return samples.contentEquals(other.samples) &&
                sampleRate == other.sampleRate &&
                text == other.text &&
                voiceLabel == other.voiceLabel
    }

    override fun hashCode(): Int = _hashCode
}
