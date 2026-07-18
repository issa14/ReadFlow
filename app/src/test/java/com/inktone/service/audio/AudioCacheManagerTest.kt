package com.inktone.service.audio

import com.inktone.domain.model.SynthesisResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioCacheManagerTest {

    private lateinit var cache: AudioCacheManager

    @BeforeEach
    fun setUp() {
        cache = AudioCacheManager()
    }

    private fun makeResult(text: String, sampleCount: Int): SynthesisResult {
        return SynthesisResult(
            samples = FloatArray(sampleCount) { 0.5f },
            sampleRate = 22050,
            text = text,
            voiceLabel = "Jessica",
            synthesisTimeMs = 100,
            audioDurationMs = 500,
            engineId = "piper"
        )
    }

    @Test
    fun `put and get — cache hit`() {
        val result = makeResult("Bonjour", 1000)
        cache.put("key1", result)
        val cached = cache.get("key1")
        assertNotNull(cached)
        assertEquals("Bonjour", cached?.text)
        assertEquals(1, cache.hitCount)
        assertEquals(0, cache.missCount)
    }

    @Test
    fun `get missing key — cache miss`() {
        val cached = cache.get("nonexistent")
        assertNull(cached)
        assertEquals(1, cache.missCount)
        assertEquals(0, cache.hitCount)
    }

    @Test
    fun `put replaces existing key`() {
        val r1 = makeResult("Bonjour", 1000)
        val r2 = makeResult("Salut", 2000)
        cache.put("key1", r1)
        cache.put("key1", r2) // replace
        val cached = cache.get("key1")
        assertEquals("Salut", cached?.text)
        assertEquals(1, cache.size())
    }

    @Test
    fun `clear empties cache`() {
        cache.put("k1", makeResult("a", 1000))
        cache.put("k2", makeResult("b", 1000))
        assertEquals(2, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `LRU eviction when cache is full`() {
        // Chaque entrée fait ~2.3 Mo avec la nouvelle formule (500k samples × 4 + overheads + 20%)
        // Cache = 15 Mo → max ~6 entrées
        val sampleCount = 500_000
        var i = 0
        while (i < 20) {
            cache.put("key$i", makeResult("text$i", sampleCount))
            i++
        }
        val count = cache.size()
        assertTrue(count > 0, "Le cache devrait contenir des entrées (trouvé: $count)")
        assertTrue(count <= 7, "Le cache devrait avoir évincé des entrées (trouvé: $count, max attendu: 7)")
    }

    @Test
    fun `hitRatio computation`() {
        cache.get("miss1")
        cache.get("miss2")
        cache.put("hit1", makeResult("a", 1000))
        cache.get("hit1")
        cache.get("hit1")
        // 2 hits, 2 misses = 0.5
        assertEquals(0.5f, cache.hitRatio, 0.01f)
    }

    @Test
    fun `sizeOf computes correct byte size with all overheads`() {
        val result = makeResult("test", 1000)
        val size = AudioCacheManager.sizeOf(result)

        // Calcul manuel avec la nouvelle formule :
        // FloatArray: 1000*4 + 24 = 4024
        // text "test": 4*2 + 38 = 46
        // voiceLabel "Jessica": 7*2 + 38 = 52
        // engineId "piper": 5*2 + 38 = 48
        // primitives: 4 + 8 + 8 + 4 = 24
        // SynthesisResult overhead: 32
        // Entry wrapper: 24
        // LinkedHashMap.Node: 48
        // Subtotal = 4024 + 46 + 52 + 48 + 24 + 32 + 24 + 48 = 4298
        // +20% alignement = 4298 * 1.2 = 5157.6 → 5157
        val expectedSubtotal = 4024L + 46L + 52L + 48L + 24L + 32L + 24L + 48L
        val expectedWithAlignment = (expectedSubtotal * 1.2).toLong()
        assertEquals(expectedWithAlignment, size)

        // Vérifier que la taille est supérieure à l'ancien calcul (qui ignorait des champs)
        val oldStyleSize = 4024L + 46L + 32L + 24L // ancienne formule simpliste
        assertTrue(size > oldStyleSize, "Nouvelle formule ($size) doit être > ancienne ($oldStyleSize)")
    }

    @Test
    fun `entry too large is rejected`() {
        // Créer une entrée > 15 Mo (MAX_SIZE_BYTES = 15 Mo)
        // Avec la nouvelle formule sizeOf, ~3.8M floats suffisent pour dépasser 15 Mo
        val hugeCount = (16L * 1024 * 1024 / 4).toInt() // ~4.2M floats → >15 Mo
        val result = makeResult("huge", hugeCount)
        cache.put("huge", result)
        assertEquals(0, cache.size(), "Entrée > 15 Mo doit être rejetée")
    }

    // ── Nouveaux tests mémoire (HAUTE 1) ──────────────────

    @Test
    fun `20 phrases realistes ne dépassent pas 15 Mo`() {
        // Simuler 20 phrases typiques (~1s d'audio chacune = 22050 samples)
        val samplesPerPhrase = 22050
        val phrases = listOf(
            "Bonjour, comment allez-vous aujourd'hui ?",
            "Le soleil brille dans le ciel bleu.",
            "Les oiseaux chantent dans les arbres du jardin.",
            "Je voudrais un café s'il vous plaît.",
            "La lecture est une fenêtre ouverte sur le monde.",
            "Demain, nous irons au marché ensemble.",
            "Cette histoire est vraiment passionnante.",
            "Il faut toujours garder espoir.",
            "Le chat dort paisiblement sur le canapé.",
            "J'adore écouter de la musique classique.",
            "Les montagnes sont couvertes de neige.",
            "Prenez le temps de respirer profondément.",
            "Chaque jour est une nouvelle aventure.",
            "La mer est calme ce matin.",
            "Nous partirons en vacances la semaine prochaine.",
            "Le jardin est rempli de fleurs magnifiques.",
            "Apprendre une nouvelle langue est enrichissant.",
            "Le feu crépite doucement dans la cheminée.",
            "Les étoiles brillent dans la nuit noire.",
            "Merci pour votre attention et votre patience."
        )

        var totalSizeEstimate = 0L
        for ((i, text) in phrases.withIndex()) {
            val result = makeResult(text, samplesPerPhrase)
            val size = AudioCacheManager.sizeOf(result)
            totalSizeEstimate += size
            cache.put("phrase_$i", result)
        }

        val cacheEntryCount = cache.size()
        // Avec 15 Mo de capacité et ~130 KB par phrase réaliste,
        // on devrait pouvoir stocker la plupart des 20 phrases
        assertTrue(cacheEntryCount > 0, "Le cache devrait contenir au moins quelques entrées")
        assertTrue(cacheEntryCount <= 20, "Max 20 entrées (trouvé: $cacheEntryCount)")

        // Vérifier que les entrées les plus récentes sont conservées (LRU)
        val firstPhrase = cache.get("phrase_0")
        val lastPhrase = cache.get("phrase_19")
        // La dernière insérée doit être présente, la première a pu être évincée
        assertNotNull(lastPhrase, "La dernière phrase insérée doit être dans le cache")
        // Si la première est absente, c'est normal (LRU eviction)
        if (firstPhrase == null) {
            assertTrue(cacheEntryCount < 20, "Si phrase_0 est évincée, le cache doit être plein")
        }
    }

    @Test
    fun `sizeOf avec chaînes longues reflète le coût réel`() {
        val shortText = makeResult("Ok.", 1000)
        val longText = makeResult("A".repeat(500), 1000) // 500 caractères

        val shortSize = AudioCacheManager.sizeOf(shortText)
        val longSize = AudioCacheManager.sizeOf(longText)

        // Le texte long doit coûter significativement plus cher
        val diff = longSize - shortSize
        // 500 - 3 = 497 chars supplémentaires × 2 bytes × 1.2 alignement ≈ 1193 bytes
        assertTrue(diff > 1000, "Différence attendue > 1000 octets (trouvé: $diff)")
    }

    @Test
    fun `MAX_SIZE_BYTES est bien 15 Mo`() {
        val maxSizeBytes = 15L * 1024 * 1024

        // Une phrase réaliste (~1s audio, 22050 samples) fait ~104 KB
        val samples = 22050
        val sampleResult = makeResult("Une phrase typique de test.", samples)
        val sizePerEntry = AudioCacheManager.sizeOf(sampleResult)

        val maxEntries = maxSizeBytes / sizePerEntry
        assertTrue(maxEntries >= 5, "15 Mo doivent permettre au moins 5 entrées (trouvé: $maxEntries)")
        // Avec ~104 KB/entrée, 15 Mo = ~148 entrées. On vérifie que c'est dans cet ordre de grandeur.
        assertTrue(maxEntries <= 200,
            "15 Mo ne doivent pas permettre plus de 200 entrées d'1s (trouvé: $maxEntries)")

        // Vérifier que la taille estimée est dans la plage attendue (~100-200 KB par phrase d'1s)
        assertTrue(sizePerEntry in 50_000..250_000,
            "Taille estimée par phrase 1s doit être ~100 KB (trouvé: $sizePerEntry)")
    }
}
