package com.readflow.service.audio

import android.util.Log
import com.readflow.domain.model.SynthesisResult
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache LRU en mémoire pour les résultats de synthèse TTS.
 *
 * - Capacité max : 30 Mo
 * - TTL : 10 minutes par entrée
 * - Éviction LRU (les moins récemment utilisés en premier)
 *
 * Utilisé par [com.readflow.data.repository.TtsRepositoryImpl] pour éviter
 * de re-synthétiser les mêmes phrases lors des allers-retours dans un chapitre.
 */
@Singleton
class AudioCacheManager @Inject constructor() {

    companion object {
        private const val TAG = "AudioCache"
        private const val MAX_SIZE_BYTES = 30L * 1024 * 1024  // 30 Mo
        private const val TTL_MS = 10L * 60 * 1000             // 10 minutes

        /** Retourne la taille en octets d'un SynthesisResult. */
        fun sizeOf(result: SynthesisResult): Long {
            // FloatArray = 4 octets par float + overhead tableau (~24 bytes)
            return result.samples.size.toLong() * 4 + 24
        }
    }

    private data class Entry(
        val result: SynthesisResult,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val sizeBytes: Long get() = sizeOf(result)
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > TTL_MS
    }

    // Access-order = true → LRU (les plus récemment accédés en fin de map)
    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return false // On gère l'éviction manuellement
        }
    }

    @Volatile var currentSizeBytes: Long = 0
        private set

    @Volatile var hitCount: Long = 0
        private set

    @Volatile var missCount: Long = 0
        private set

    // ── API publique ──────────────────────────────────

    /**
     * Cherche un résultat dans le cache.
     * @param key clé unique (texte + voix + vitesse)
     * @return le [SynthesisResult] ou null si absent/expiré
     */
    @Synchronized
    fun get(key: String): SynthesisResult? {
        val entry = cache[key] ?: run { missCount++; return null }
        if (entry.isExpired()) {
            cache.remove(key)
            currentSizeBytes -= entry.sizeBytes
            missCount++
            return null
        }
        hitCount++
        Log.d(TAG, "HIT — \"${key.take(50)}\" (${entry.sizeBytes / 1024} Ko)")
        return entry.result
    }

    /**
     * Stocke un résultat dans le cache.
     * @param key clé unique
     * @param result résultat de synthèse
     */
    @Synchronized
    fun put(key: String, result: SynthesisResult) {
        val size = sizeOf(result)

        // Si une seule entrée dépasse la capacité, ne pas la stocker
        if (size > MAX_SIZE_BYTES) {
            Log.w(TAG, "Entrée trop grande (${size / 1024} Ko), ignorée")
            return
        }

        // Évincer si nécessaire
        evictToFit(size)

        // Remplacer si la clé existe déjà
        cache[key]?.let { currentSizeBytes -= it.sizeBytes }

        cache[key] = Entry(result)
        currentSizeBytes += size
        Log.d(TAG, "PUT — \"${key.take(50)}\" (${size / 1024} Ko, total=${currentSizeBytes / 1024}/${MAX_SIZE_BYTES / 1024} Ko)")
    }

    /** Vide complètement le cache. */
    @Synchronized
    fun clear() {
        val count = cache.size
        cache.clear()
        currentSizeBytes = 0
        Log.d(TAG, "CLEAR — $count entrées supprimées")
    }

    /** Nombre d'entrées actuellement dans le cache. */
    @Synchronized
    fun size(): Int = cache.size

    /** Ratio de hits (0.0 à 1.0). */
    val hitRatio: Float
        get() {
            val total = hitCount + missCount
            return if (total == 0L) 0f else hitCount.toFloat() / total
        }

    // ── Private ────────────────────────────────────────

    private fun evictToFit(neededBytes: Long) {
        val target = MAX_SIZE_BYTES - neededBytes
        val iter = cache.entries.iterator()
        while (currentSizeBytes > target && iter.hasNext()) {
            val (key, entry) = iter.next()
            iter.remove()
            currentSizeBytes -= entry.sizeBytes
            Log.d(TAG, "EVICT — \"${key.take(50)}\" (${entry.sizeBytes / 1024} Ko)")
        }
        // Supprimer aussi les entrées expirées
        cache.entries.removeAll { (_, entry) -> entry.isExpired() }
    }
}
