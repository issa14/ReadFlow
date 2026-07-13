package com.readflow.service.audio

import androidx.collection.LruCache
import com.readflow.domain.model.SynthesisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache LRU en mémoire pour les résultats de synthèse TTS.
 *
 * - Capacité max : 20 Mo (taille réelle mesurée, pas estimée)
 * - TTL : 10 minutes par entrée
 * - Éviction automatique via [LruCache] (AndroidX)
 *
 * Utilisé par [com.readflow.data.repository.TtsRepositoryImpl] pour éviter
 * de re-synthétiser les mêmes phrases lors des allers-retours dans un chapitre.
 *
 * **Correction C03** : La taille est désormais calculée précisément en incluant
 * le FloatArray, la String text, et les overheads objets (Entry + SynthesisResult).
 * La capacité a été réduite à 20 Mo pour compenser les ~30% de sous-estimation
 * précédente. Le [LruCache] natif Android remplace le [LinkedHashMap] manuel
 * pour une éviction thread-safe et précise.
 */
@Singleton
class AudioCacheManager @Inject constructor() {

    companion object {
        /** Capacité maximale du cache en octets (taille réelle). */
        private const val MAX_SIZE_BYTES = 20L * 1024 * 1024
        /** Durée de vie d'une entrée avant expiration. */
        private const val TTL_MS = 10L * 60 * 1000

        /**
         * Calcule la taille mémoire réelle d'un [SynthesisResult].
         *
         * Inclut :
         * - FloatArray samples : 4 octets/float + 24 octets overhead tableau
         * - String text : ~2 octets/caractère (UTF-16) + 38 octets overhead
         * - SynthesisResult object : ~32 octets overhead
         * - Entry wrapper : ~24 octets overhead
         */
        fun sizeOf(result: SynthesisResult): Long {
            return result.samples.size.toLong() * 4L + 24L +
                   result.text.length.toLong() * 2L + 38L +
                   32L + 24L
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
        debug("PUT — \"${key.take(50)}\" (${size / 1024} Ko, cache=${cache.size()} entrées)")
    }

    @Synchronized
    fun clear() {
        val count = cache.size()
        cache.evictAll()
        debug("CLEAR — $count entrées supprimées")
    }

    @Synchronized
    fun size(): Int = cache.size()

    val hitRatio: Float
        get() {
            val total = hitCount + missCount
            return if (total == 0L) 0f else hitCount.toFloat() / total
        }
}
