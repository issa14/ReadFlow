package com.inktone.domain.provider

import com.inktone.domain.model.SynthesisResult

/**
 * Voix disponible pour un moteur TTS.
 */
data class TtsVoice(
    /** Identifiant unique de la voix (ex: "fr-FR-VivienneNeural"). */
    val id: String,
    /** Libellé affichable (ex: "Vivienne (FR)"). */
    val label: String
)

/**
 * Contrat pour un moteur de synthèse vocale (provider).
 *
 * Chaque implémentation encapsule un moteur spécifique
 * (ONNX local, Microsoft Edge cloud, Google Cloud, etc.)
 * et expose ses voix disponibles.
 *
 * Les providers sont injectés via `@IntoSet` dans le module Hilt
 * et routés par [com.inktone.data.repository.TtsRepositoryImpl].
 */
interface TtsProvider {

    /** Identifiant unique du moteur (ex: "piper", "edge"). */
    val engineId: String

    /** Libellé affichable (ex: "Piper ONNX (local)"). */
    val engineLabel: String

    /** Indique si le moteur est prêt à synthétiser. */
    val isAvailable: Boolean

    /** Voix disponibles pour ce moteur. */
    val availableVoices: List<TtsVoice>

    /**
     * Synthétise un texte en audio PCM.
     *
     * @param text  Texte à synthétiser (déjà nettoyé des ponctuations excessives).
     * @param voice Identifiant de la voix (correspond à [TtsVoice.id]).
     * @param speed Vitesse d'élocution (0.5 à 2.0).
     * @return Résultat de synthèse contenant les échantillons PCM.
     */
    suspend fun synthesize(
        text: String,
        voice: String,
        speed: Float
    ): SynthesisResult
}
