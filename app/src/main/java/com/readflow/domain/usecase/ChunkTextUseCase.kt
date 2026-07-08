package com.readflow.domain.usecase

import com.readflow.domain.model.Sentence
import javax.inject.Inject

/**
 * Découpe un texte brut en phrases françaises.
 * Délègue à [FrenchSentenceSplitter] pour les règles linguistiques.
 */
class ChunkTextUseCase @Inject constructor() {

    companion object {
        private const val MAX_SENTENCE_LENGTH = 500
    }

    operator fun invoke(text: String): List<Sentence> {
        val sentences = FrenchSentenceSplitter.split(text)

        // Sous-découpage des phrases trop longues
        var globalIdx = 0
        return sentences.flatMap { sentence ->
            if (sentence.text.length > MAX_SENTENCE_LENGTH) {
                sentence.text.split(Regex("(?<=,)\\s+"))
                    .mapIndexed { i, part ->
                        val s = part.trim()
                        Sentence(
                            index = globalIdx++,
                            text = s,
                            startOffset = sentence.startOffset + i,
                            endOffset = sentence.startOffset + i + s.length
                        )
                    }
            } else {
                listOf(sentence.copy(index = globalIdx++))
            }
        }
    }
}
