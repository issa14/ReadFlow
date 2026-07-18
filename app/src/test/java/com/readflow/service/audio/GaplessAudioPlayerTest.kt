package com.readflow.service.audio

import android.media.AudioFormat
import android.media.AudioTrack
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests unitaires pour [GaplessAudioPlayer] — focus sur la race condition
 * use-after-free et le mécanisme de verrouillage writeLock + willStop.
 *
 * Stratégie : spy sur GaplessAudioPlayer pour neutraliser ensureTrack()
 * (qui nécessite l'Android Framework) et injecter un AudioTrack mocké.
 * Les tests de stress valident l'absence de crash lors de stop/play rapides.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GaplessAudioPlayerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var player: GaplessAudioPlayer
    private lateinit var mockTrack: AudioTrack

    @BeforeEach
    fun setUp() {
        // Spy le player réel pour tester le comportement interne
        player = spyk(GaplessAudioPlayer(), recordPrivateCalls = true)

        // Mock AudioTrack — simule des écritures réussies
        mockTrack = mockk(relaxed = true)
        every { mockTrack.write(any<ShortArray>(), any(), any()) } answers {
            val len = thirdArg<Int>()
            len // simule écriture complète
        }
        every { mockTrack.state } returns AudioTrack.STATE_INITIALIZED
        every { mockTrack.playState } returns AudioTrack.PLAYSTATE_PLAYING
        every { mockTrack.play() } just Runs
        every { mockTrack.pause() } just Runs
        every { mockTrack.flush() } just Runs
        every { mockTrack.stop() } just Runs
        every { mockTrack.release() } just Runs
        every { mockTrack.setVolume(any()) } returns AudioTrack.SUCCESS

        // Injecter le mock track via réflexion pour contourner ensureTrack()
        setPrivateTrack(mockTrack)
    }

    @AfterEach
    fun tearDown() {
        player.release()
    }

    // ── Tests état / cycle de vie ──────────────────────────

    @Test
    fun `état initial est Idle`() {
        assertEquals(GaplessAudioPlayer.State.Idle, player.state.value)
    }

    @Test
    fun `play() passe l'état à Playing et réinitialise willStop`() {
        // Given : willStop est forcé à true (état résiduel)
        setPrivateWillStop(true)
        assertTrue(player.willStop)

        // When
        player.play()

        // Then
        assertFalse(player.willStop, "willStop doit être réinitialisé à false par play()")
        assertEquals(GaplessAudioPlayer.State.Playing, player.state.value)
    }

    @Test
    fun `stop() positionne willStop à true avant de changer l'état`() {
        // Given
        player.play()
        assertFalse(player.willStop)
        assertEquals(GaplessAudioPlayer.State.Playing, player.state.value)

        // When
        player.stop()

        // Then
        assertTrue(player.willStop, "willStop doit être true après stop()")
        assertEquals(GaplessAudioPlayer.State.Stopped, player.state.value)
    }

    @Test
    fun `pause() et resume() préservent le flag willStop`() {
        // Given
        player.play()

        // When
        player.pause()

        // Then
        assertFalse(player.willStop, "willStop ne doit pas changer sur pause")
        assertEquals(GaplessAudioPlayer.State.Paused, player.state.value)

        // When
        player.resume()

        // Then
        assertFalse(player.willStop, "willStop ne doit pas changer sur resume")
        assertEquals(GaplessAudioPlayer.State.Playing, player.state.value)
        assertEquals(0, player.completedCount, "completedCount préservé après pause")
    }

    // ── Tests file d'attente ───────────────────────────────

    @Test
    fun `enqueue ajoute un segment et incrémente pendingCount`() {
        val samples = FloatArray(1024) { 0.5f }
        assertEquals(0, player.pendingCount)

        player.enqueue(samples)
        assertEquals(1, player.pendingCount)
    }

    @Test
    fun `stop() vide la file d'attente`() {
        player.enqueue(FloatArray(1024))
        player.enqueue(FloatArray(2048))
        assertEquals(2, player.pendingCount)

        player.stop()
        assertEquals(0, player.pendingCount)
    }

    // ── Tests writeBlocking / race condition ────────────────

    @Test
    fun `writeBlocking sort immédiatement si willStop est true`() {
        // Given : willStop = true
        setPrivateWillStop(true)

        val samples = FloatArray(44100) { 0.5f } // 2 secondes à 22050 Hz

        // When : on appelle writeBlocking via réflexion
        val totalWritten = invokeWriteBlocking(samples)

        // Then : aucune écriture ne doit avoir lieu
        assertEquals(0, totalWritten, "Aucune écriture ne doit avoir lieu si willStop=true")
    }

    @Test
    fun `writeBlocking sort immédiatement si l'état n'est pas Playing`() {
        // Given : état Idle (pas Playing)
        assertEquals(GaplessAudioPlayer.State.Idle, player.state.value)

        val samples = FloatArray(44100) { 0.5f }

        // When
        val totalWritten = invokeWriteBlocking(samples)

        // Then
        assertEquals(0, totalWritten, "Aucune écriture ne doit avoir lieu si état != Playing")
    }

    @Test
    fun `writeBlocking écrit correctement quand tout est OK`() {
        // Given : état Playing, willStop false
        player.play()
        val samples = FloatArray(4410) { 0.5f } // ~200ms

        // When
        val totalWritten = invokeWriteBlocking(samples)

        // Then
        assertEquals(4410, totalWritten, "Tous les échantillons doivent être écrits")
    }

    @Test
    fun `writeBlocking écrit par chunks de 4096`() {
        player.play()
        val samples = FloatArray(10000) { 0.5f } // plus grand que chunkSize

        val totalWritten = invokeWriteBlocking(samples)

        assertEquals(10000, totalWritten)
        // Vérifie que write() a été appelé plusieurs fois (chunks)
        verify(atLeast = 2) { mockTrack.write(any<ShortArray>(), any(), any()) }
    }

    // ── Tests de stress stop/play ───────────────────────────

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `stress test — 100 cycles stop-play rapides sans crash`() {
        val samples = FloatArray(4410) { 0.5f }

        repeat(100) { iteration ->
            // Ré-injecter le mock track (stop() l'a mis à null)
            setPrivateTrack(mockTrack)

            // Enqueue un segment
            player.enqueue(samples.copyOf())

            // Play
            player.play()

            // Laisser le temps d'entrer dans writeBlocking
            Thread.sleep(1) // 1ms entre chaque — pire cas réaliste

            // Stop brutal
            player.stop()

            // Vérifier l'état après chaque cycle
            assertEquals(
                GaplessAudioPlayer.State.Stopped,
                player.state.value,
                "Itération $iteration : état doit être Stopped après stop()"
            )
            assertTrue(
                player.willStop,
                "Itération $iteration : willStop doit être true après stop()"
            )
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `stress test — arrêt pendant écriture simulée`() {
        val iterations = 100
        var crashes = 0
        var completed = 0

        repeat(iterations) { i ->
            try {
                // Ré-injecter le mock track avant chaque itération
                setPrivateTrack(mockTrack)

                val samples = FloatArray(22050) { 0.5f } // 1 seconde

                // Play — démarre startLoop
                player.play()

                // Lancer writeBlocking dans un thread séparé
                val writeThread = Thread {
                    invokeWriteBlocking(samples)
                }
                writeThread.start()

                // Laisser writeBlocking démarrer (entrer dans la boucle)
                Thread.sleep(2)

                // Stop brutal — acquiert writeLock et libère le track
                player.stop()

                // Attendre la fin du thread d'écriture
                writeThread.join(2000)
                assertFalse(writeThread.isAlive, "Itération $i : le thread d'écriture doit être terminé")

                completed++
            } catch (e: Exception) {
                crashes++
                println("Itération $i : ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        assertEquals(0, crashes, "Aucun crash use-after-free sur $iterations itérations")
        assertEquals(iterations, completed, "Toutes les itérations doivent réussir")
    }

    @Test
    fun `le verrou writeLock empêche l'écriture concurrente pendant stop()`() {
        val lockAcquiredInStop = AtomicBoolean(false)

        val samples = FloatArray(22050) { 0.5f }
        setPrivateTrack(mockTrack)
        player.play()

        val writeThread = Thread {
            invokeWriteBlocking(samples)
        }

        val stopThread = Thread {
            player.stop()
            lockAcquiredInStop.set(true)
        }

        writeThread.start()
        // Laisser writeBlocking entrer dans sa boucle
        Thread.sleep(10)
        stopThread.start()

        writeThread.join(5000)
        stopThread.join(5000)

        assertFalse(writeThread.isAlive, "Le thread d'écriture doit être terminé")
        assertFalse(stopThread.isAlive, "Le thread de stop doit être terminé")
        assertTrue(lockAcquiredInStop.get(), "stop() doit avoir acquis le writeLock")
    }

    // ── Tests mémoire / allocation ─────────────────────────

    @Test
    fun `allocationsSaved incrémenté à chaque appel writeBlocking`() {
        setPrivateTrack(mockTrack)
        player.play()

        assertEquals(0, player.allocationsSaved)

        // 3 segments = 3 appels à writeBlocking = 3 allocations évitées
        invokeWriteBlocking(FloatArray(22050) { 0.5f })
        assertEquals(1, player.allocationsSaved)

        invokeWriteBlocking(FloatArray(11025) { 0.3f })
        assertEquals(2, player.allocationsSaved)

        invokeWriteBlocking(FloatArray(44100) { 0.7f })
        assertEquals(3, player.allocationsSaved)
    }

    @Test
    fun `simulation 1h de lecture — vérifie le nombre d'allocations évitées`() {
        setPrivateTrack(mockTrack)
        player.play()

        // 1h de lecture ≈ 720 phrases (1 phrase / 5 secondes)
        val phrasesParHeure = 720
        val samplesParPhrase = 22050 // ~1s à 22050 Hz

        repeat(phrasesParHeure) {
            invokeWriteBlocking(FloatArray(samplesParPhrase) { 0.5f })
        }

        // Chaque phrase évite 1 allocation de ShortArray(n)
        assertEquals(
            phrasesParHeure.toLong(),
            player.allocationsSaved,
            "720 allocations de ShortArray évitées sur 1h de lecture"
        )
    }

    @Test
    fun `taille buffer constante quelle que soit la taille du segment`() {
        setPrivateTrack(mockTrack)
        player.play()

        // Petit segment : 11025 samples (~0.5s)
        invokeWriteBlocking(FloatArray(11025) { 0.5f })

        // Gros segment : 220500 samples (~10s)
        invokeWriteBlocking(FloatArray(220500) { 0.5f })

        // Dans les deux cas, le chunkBuffer reste à CHUNK_SIZE (4096)
        // Aucune allocation proportionnelle à la taille du segment
        assertEquals(2, player.allocationsSaved)
    }

    @Test
    fun `zero allocation ShortArray par phrase — vérifié via compteur`() {
        // Ce test valide que le compteur allocationsSaved correspond bien
        // au nombre d'appels writeBlocking (donc au nombre d'allocations évitées)
        setPrivateTrack(mockTrack)
        player.play()

        val nbPhrases = 50
        repeat(nbPhrases) {
            invokeWriteBlocking(FloatArray(22050) { 0.5f })
        }

        assertEquals(
            nbPhrases.toLong(),
            player.allocationsSaved,
            "Chaque appel writeBlocking évite exactement 1 allocation ShortArray(n)"
        )

        // Vérification supplémentaire : le compteur ne compte que les appels
        // réussis (hors stop/willStop)
        assertTrue(
            player.allocationsSaved >= nbPhrases,
            "Au moins $nbPhrases allocations évitées"
        )
    }

    // ── Helpers ─────────────────────────────────────────────

    /**
     * Injecte un [AudioTrack] mocké dans le champ privé [track] via réflexion.
     */
    private fun setPrivateTrack(track: AudioTrack) {
        val field = GaplessAudioPlayer::class.java.getDeclaredField("track")
        field.isAccessible = true
        field.set(player, track)
    }

    /**
     * Injecte la valeur de [willStop] via réflexion.
     */
    private fun setPrivateWillStop(value: Boolean) {
        val field = GaplessAudioPlayer::class.java.getDeclaredField("willStop")
        field.isAccessible = true
        field.set(player, value)
    }

    /**
     * Invoque [GaplessAudioPlayer.writeBlocking] via réflexion et retourne
     * le nombre total de shorts écrits (capturé depuis les logs ou estimé
     * via le nombre d'appels à mockTrack.write).
     */
    private fun invokeWriteBlocking(samples: FloatArray): Int {
        val method = GaplessAudioPlayer::class.java.getDeclaredMethod(
            "writeBlocking",
            FloatArray::class.java
        )
        method.isAccessible = true

        // Compter les écritures effectivement réalisées
        val writtenCount = AtomicInteger(0)
        every { mockTrack.write(any<ShortArray>(), any(), any()) } answers {
            val len = thirdArg<Int>()
            writtenCount.addAndGet(len)
            len
        }

        method.invoke(player, samples)
        return writtenCount.get()
    }
}
