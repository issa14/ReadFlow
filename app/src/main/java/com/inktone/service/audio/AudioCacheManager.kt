package com.inktone.service.audio

import androidx.collection.LruCache
import com.inktone.domain.model.SynthesisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache LRU en mémoire pour les résultats de synthèse TTS.
 *
 * - Capacité max : 15 Mo (taille réelle estimée avec +20% alignement heap)
 * - TTL : 10 minutes par entrée
 * - Éviction automatique via [LruCache] (AndroidX)
 *
 * Utilisé par [com.inktone.data.repository.TtsRepositoryImpl] pour éviter
 * de re-synthétiser les mêmes phrases lors des allers-retours dans un chapitre.
 *
 * **Formule sizeOf()** : inclut tous les champs (samples, text, voiceLabel,
 * engineId, primitives), les overheads objets (SynthesisResult, Entry,
 * LinkedHashMap.Node), et un facteur d'alignement heap de +20%.
 * La capacité a été réduite à 15 Mo pour compenser la précédente
 * sous-estimation (~30-40%).
 */
@Singleton
class AudioCacheManager @Inject constructor() {

    companion object {
        /**
         * Capacité maximale du cache en octets (taille réelle estimée).
         *
         * Réduit de 20 Mo à 15 Mo pour compenser la nouvelle formule [sizeOf]
         * qui inclut désormais tous les overheads (LinkedHashMap.Node, Strings
         * de métadonnées, primitives, alignement heap +20%).
         */
        private const val MAX_SIZE_BYTES = 15L * 1024 * 1024

        /** Durée de vie d'une entrée avant expiration. */
        private const val TTL_MS = 10L * 60 * 1000

        /**
         * Calcule la taille mémoire réelle estimée d'un [SynthesisResult].
         *
         * Inclut tous les champs et overheads :
         * - FloatArray samples : 4 octets/float + 24 octets overhead tableau
         * - String text, voiceLabel, engineId : ~2 octets/caractère (UTF-16) + 38 octets overhead
         * - Primitives (sampleRate, durations, hashCode) : 4+8+8+4 = 24 octets
         * - SynthesisResult object overhead : ~32 octets
         * - Entry wrapper overhead : ~24 octets
         * - LinkedHashMap.Node wrapper : ~48 octets (LruCache interne)
         * - Alignement/fragmentation heap : +20%
         */
        fun sizeOf(result: SynthesisResult): Long {
            var bytes = 0L

            // FloatArray samples
            bytes += result.samples.size.toLong() * 4L + 24L

            // Strings (text, voiceLabel, engineId) — chacun avec overhead String object
            bytes += result.text.length.toLong() * 2L + 38L
            bytes += result.voiceLabel.length.toLong() * 2L + 38L
            bytes += result.engineId.length.toLong() * 2L + 38L

            // Primitives : sampleRate(Int=4) + synthesisTimeMs(Long=8)
            //            + audioDurationMs(Long=8) + _hashCode(Int=4)
            bytes += 24L

            // SynthesisResult object overhead
            bytes += 32L

            // Entry wrapper overhead
            bytes += 24L

            // LinkedHashMap.Node (LruCache interne) : refs key+value + hash + next + prev
            bytes += 48L

            // Alignement / fragmentation heap : +20%
            bytes = (bytes * 1.2).toLong()

            return bytes
        }

        private fun debug(msg: String) {
            try { android.util.Log.d("AudioCache", msg) } catch (_: Exception) {}
        }
    }

    private data class Entry(
        val result: SynthesisResult,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val sizeBytes: Long get() = sizeOf(result)
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > TTL_MS
    }

    /**
     * Cache LRU avec éviction automatique basée sur la taille réelle.
     *
     * [LruCache] est thread-safe et utilise [sizeOf] pour le calcul
     * exact de la mémoire occupée. L'éviction est automatique.
     *
     * Note : [LruCache.size] retourne la taille totale en octets (unités de [sizeOf]),
     * pas le nombre d'entrées. Utiliser [entryCount] pour le nombre d'entrées.
     */
    private val cache = object : LruCache<String, Entry>(MAX_SIZE_BYTES.toInt()) {
        override fun sizeOf(key: String, value: Entry): Int {
            return value.sizeBytes.toInt()
        }
    }

    @Volatile var hitCount: Long = 0
        private set

    @Volatile var missCount: Long = 0
        private set

    // ── API publique ──────────────────────────────────

    @Synchronized
    fun get(key: String): SynthesisResult? {
        val entry = cache.get(key) ?: run { missCount++; return null }
        if (entry.isExpired()) {
            cache.remove(key)
            missCount++
            return null
        }
        hitCount++
        debug("HIT — \"${key.take(50)}\" (${entry.sizeBytes / 1024} Ko)")
        return entry.result
    }

    @Synchronized
    fun put(key: String, result: SynthesisResult) {
        val entry = Entry(result)
        val size = entry.sizeBytes

        if (size > MAX_SIZE_BYTES) {
            debug("Entrée trop grande (${size / 1024} Ko), ignorée")
            return
        }

        cache.put(key, entry)
        debug("PUT — \"${key.take(50)}\" (${size / 1024} Ko, cache=${entryCount} entrées)")
    }

    @Synchronized
    fun clear() {
        val count = entryCount
        cache.evictAll()
        debug("CLEAR — $count entrées supprimées")
    }

    /** Nombre d'entrées dans le cache. */
    @Synchronized
    fun size(): Int = cache.snapshot().size

    private val entryCount: Int get() = cache.snapshot().size

    val hitRatio: Float
        get() {
            val total = hitCount + missCount
            return if (total == 0L) 0f else hitCount.toFloat() / total
        }
}
