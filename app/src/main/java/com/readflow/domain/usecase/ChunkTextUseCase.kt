package com.readflow.domain.usecase

import android.util.Log
import com.readflow.data.database.SentenceCacheDao
import com.readflow.data.database.entity.SentenceCacheEntity
import com.readflow.domain.model.Sentence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Découpe un texte brut en phrases françaises avec mise en cache Room.
 *
 * **Architecture de cache :**
 * - `invoke(bookId, chapterIndex, text)` consulte d'abord [SentenceCacheDao]
 *   pour vérifier si le chapitre a déjà été segmenté.
 * - Si le cache est chaud : lecture directe depuis SQLite (O(n) lignes).
 * - Si le cache est froid : segmentation via [FrenchSentenceSplitter]
 *   exécutée sur [Dispatchers.Default] (thread pool CPU-bound), puis
 *   persistance atomique dans Room.
 *
 * **Optimisation GC :**
 * - [FrenchSentenceSplitter] utilise [java.text.BreakIterator] (ICU natif)
 *   → zéro allocation de [java.util.regex.Pattern]/[java.util.regex.Matcher].
 * - Le sous-découpage des phrases longues utilise un parcours caractère
 *   par caractère (split manuel sur virgules) au lieu d'un [Regex.split].
 * - La segmentation s'exécute sur [Dispatchers.Default] pour ne pas bloquer
 *   le thread UI et pour permettre au GC de s'exécuter entre les batches.
 *
 * **Thread safety :**
 * - [SentenceCacheDao] utilise Room (suspend, thread-safe).
 * - La segmentation pure ([FrenchSentenceSplitter.split]) est stateless
 *   → pas de synchronisation nécessaire.
 * - [Dispatchers.Default] garantit que la segmentation CPU-intensive
 *   ne sature pas le pool IO (utilisé par Room).
 */
@Singleton
class ChunkTextUseCase @Inject constructor(
    private val sentenceCacheDao: SentenceCacheDao
) {

    companion object {
        private const val TAG = "ChunkText"
        /**
         * Longueur maximale d'une phrase avant sous-découpage.
         * Portée à 1200 pour laisser le TTS natif gérer les pauses
         * internes (virgules, incises). Seules les phrases très longues
         * sont sous-découpées, et uniquement sur `;` et `:`.
         */
        private const val MAX_SENTENCE_LENGTH = 1200
    }

    // ── API avec cache (utilisée par BookRepository) ──

    /**
     * Segmente le texte d'un chapitre, avec cache Room.
     *
     * @param bookId Identifiant du livre (clé de cache).
     * @param chapterIndex Index du chapitre dans le livre.
     * @param text Texte brut du chapitre (HTML nettoyé).
     * @return Liste ordonnée de [Sentence].
     */
    suspend operator fun invoke(
        bookId: String,
        chapterIndex: Int,
        text: String
    ): List<Sentence> {
        // 1. Tentative de lecture depuis le cache Room
        val cached = sentenceCacheDao.getSentences(bookId, chapterIndex)
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Cache HIT — bookId=$bookId ch=$chapterIndex (${cached.size} phrases)")
            return cached.toDomainSentences()
        }

        // 2. Cache froid : segmentation asynchrone sur Dispatchers.Default
        Log.d(TAG, "Cache MISS — segmentation pour bookId=$bookId ch=$chapterIndex")
        val sentences = withContext(Dispatchers.Default) {
            splitAndSubdivide(text)
        }

        // 3. Persistance atomique dans Room
        if (sentences.isNotEmpty()) {
            val entities = sentences.map { it.toCacheEntity(bookId, chapterIndex) }
            sentenceCacheDao.insertAll(entities)
            Log.d(TAG, "Cache STORE — bookId=$bookId ch=$chapterIndex (${entities.size} phrases)")
        }

        return sentences
    }

    // ── API sans cache (backward compat, import initial) ──

    /**
     * Segmente [text] en phrases, SANS mise en cache.
     *
     * Utilisé lors de l'import initial d'un EPUB quand le bookId
     * n'est pas encore disponible, ou pour la prévisualisation rapide.
     *
     * ⚠️ Cette méthode est synchrone et s'exécute sur le thread appelant.
     * L'appelant doit s'assurer d'être sur un thread d'arrière-plan.
     */
    operator fun invoke(text: String): List<Sentence> {
        return splitAndSubdivide(text)
    }

    // ── Logique de segmentation (pure, sans I/O) ─────

    /**
     * Segmente [text] et sous-découpe les phrases trop longues.
     *
     * Cette méthode est CPU-bound et stateless. Elle peut être appelée
     * sur n'importe quel thread — idéalement [Dispatchers.Default].
     */
    private fun splitAndSubdivide(text: String): List<Sentence> {
        val sentences = FrenchSentenceSplitter.split(text)

        var globalIdx = 0
        return sentences.flatMap { sentence ->
            if (sentence.text.length > MAX_SENTENCE_LENGTH) {
                subdivideLongSentence(sentence, globalIdx).also {
                    globalIdx += it.size
                }
            } else {
                listOf(sentence.copy(index = globalIdx++))
            }
        }
    }

    /**
     * Sous-découpe une phrase excessivement longue (> 1200 car.)
     * UNIQUEMENT sur les points-virgules et deux-points,
     * PAS sur les virgules simples, pour préserver l'intonation
     * naturelle que le TTS natif applique aux pauses internes.
     */
    private fun subdivideLongSentence(
        sentence: Sentence,
        startIndex: Int
    ): List<Sentence> {
        val result = mutableListOf<Sentence>()
        val text = sentence.text
        var segmentStart = 0
        var localIdx = 0

        for (i in text.indices) {
            val c = text[i]
            // Découpe UNIQUEMENT sur ; et : suivis d'espace (fins de propositions)
            val isSplitPoint = c == ';' || c == ':'
            if (isSplitPoint && i + 1 < text.length && text[i + 1].isWhitespace()) {
                val segment = text.substring(segmentStart, i + 1).trim()
                if (segment.isNotEmpty()) {
                    result.add(Sentence(
                        index = startIndex + localIdx++,
                        text = segment,
                        startOffset = sentence.startOffset + segmentStart,
                        endOffset = sentence.startOffset + i + 1
                    ))
                }
                segmentStart = i + 1
            }
        }

        val last = text.substring(segmentStart).trim()
        if (last.isNotEmpty()) {
            result.add(Sentence(
                index = startIndex + localIdx,
                text = last,
                startOffset = sentence.startOffset + segmentStart,
                endOffset = sentence.startOffset + text.length
            ))
        }

        return result.ifEmpty {
            listOf(sentence.copy(index = startIndex))
        }
    }

    // ── Mappers ──────────────────────────────────────

    private fun List<SentenceCacheEntity>.toDomainSentences(): List<Sentence> {
        return map { entity ->
            Sentence(
                index = entity.sentenceIndex,
                text = entity.text,
                startOffset = entity.startOffset,
                endOffset = entity.endOffset
            )
        }
    }

    private fun Sentence.toCacheEntity(
        bookId: String,
        chapterIndex: Int
    ): SentenceCacheEntity {
        return SentenceCacheEntity(
            bookId = bookId,
            chapterIndex = chapterIndex,
            sentenceIndex = index,
            text = text,
            startOffset = startOffset,
            endOffset = endOffset,
            chapterTitle = ""
        )
    }
}

