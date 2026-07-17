package com.readflow.service.edge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client WebSocket pour le service Microsoft Edge TTS (gratuit, cloud).
 *
 * Protocole :
 * 1. Connexion WebSocket à l'endpoint Speech Platform Bing
 * 2. Envoi d'une trame de configuration (speech.config)
 * 3. Envoi du SSML contenant le texte à synthétiser
 * 4. Réception de trames binaires (MP3) + trame de fin (turn.end)
 * 5. Décodage MP3 → PCM via [Mp3Decoder]
 *
 * Voix supportées :
 * - fr-FR-VivienneNeural (féminine, défaut)
 * - fr-FR-HenriNeural    (masculine)
 *
 * Timeout de synthèse : 15 secondes par défaut.
 */
@Singleton
class EdgeTtsClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mp3Decoder: Mp3Decoder
) {
    companion object {
        private const val TAG = "EdgeTtsClient"

        /** Token de confiance pour l'API Edge TTS (non-officielle). */
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"

        /** Endpoint WebSocket Microsoft Edge TTS. */
        private const val WS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"

        /** Timeout de synthèse (ms). */
        private const val SYNTHESIS_TIMEOUT_MS = 15_000L

        /** Voix disponibles. */
        val VOICES = listOf(
            "fr-FR-VivienneNeural",
            "fr-FR-HenriNeural"
        )

        /** Construit le SSML pour une requête de synthèse. */
        fun buildSsml(text: String, voiceName: String, speed: Float): String {
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")

            // Edge TTS attend un rate sous forme de pourcentage (ex: "+0%", "-50%", "+100%")
            val ratePercent = ((speed - 1.0f) * 100).toInt()
            val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"

            return """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="http://www.w3.org/2001/mstts" xml:lang="fr-FR"><voice name="$voiceName"><prosody rate="$rateStr" pitch="+0Hz">$escaped</prosody></voice></speak>"""
        }
    }

    /** Cache du HttpClient OkHttp (thread-safe, OkHttpClient est immuable après build()). */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Indique si le réseau est disponible pour une synthèse cloud.
     */
    val isAvailable: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    /**
     * Synthétise un texte via Microsoft Edge TTS.
     *
     * @param text      Texte à synthétiser (déjà nettoyé).
     * @param voiceName Nom complet de la voix (ex: "fr-FR-VivienneNeural").
     * @param speed     Vitesse d'élocution (0.5 à 2.0).
     * @return [SynthesisResult] contenant les échantillons PCM.
     * @throws IllegalStateException si le réseau est indisponible.
     * @throws kotlinx.coroutines.TimeoutCancellationException si le timeout est dépassé.
     */
    suspend fun synthesize(
        text: String,
        voiceName: String,
        speed: Float
    ): SynthesisResult = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            throw IllegalStateException("Réseau indisponible pour la synthèse Edge TTS")
        }

        val voice = if (voiceName in VOICES) voiceName else VOICES.first()
        val ssml = buildSsml(text, voice, speed.coerceIn(0.5f, 2.0f))

        Log.d(TAG, "Synthèse Edge TTS: voice=$voice, speed=$speed, text=\"${text.take(60)}...\"")

        val startMs = System.currentTimeMillis()

        val mp3Bytes = withTimeout(SYNTHESIS_TIMEOUT_MS) {
            synthesizeViaWebSocket(ssml)
        }

        val networkMs = System.currentTimeMillis() - startMs

        // Décodage MP3 → PCM
        val decoded = mp3Decoder.decode(mp3Bytes, context.cacheDir)

        val totalMs = System.currentTimeMillis() - startMs
        val durationMs = ((decoded.samples.size.toFloat() / decoded.sampleRate) * 1000).toLong()

        Log.i(TAG, "Synthèse OK: réseau=${networkMs}ms, décodage=${totalMs - networkMs}ms, " +
                "audio=${durationMs}ms, PCM=${decoded.samples.size} éch.")

        SynthesisResult(
            samples = decoded.samples,
            sampleRate = decoded.sampleRate,
            text = text,
            voiceLabel = voiceDisplayName(voice),
            synthesisTimeMs = totalMs,
            audioDurationMs = durationMs,
            engineId = "edge"
        )
    }

    /**
     * Ouvre un WebSocket, envoie la config + SSML, et collecte les chunks MP3.
     */
    private suspend fun synthesizeViaWebSocket(ssml: String): ByteArray {
        val deferred = CompletableDeferred<ByteArray>()

        val connectId = UUID.randomUUID().toString().replace("-", "")
        val wsUrl = "$WS_BASE&ConnectionId=$connectId"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
            .build()

        val listener = object : WebSocketListener() {
            private val audioChunks = ByteArrayOutputStream()
            private var audioStarted = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket ouvert — envoi config + SSML")
                // Trame de configuration
                val configUuid = UUID.randomUUID().toString().replace("-", "")
                val configBody = """{"context":{"system":{"name":"SpeechSDK","version":"1.19.0","build":"20220101","lang":"fr-FR"},"os":{"platform":"Android","name":"Android"}}}"""
                webSocket.send(
                    "X-RequestId:$configUuid\r\n" +
                    "Content-Type:application/json; charset=utf-8\r\n" +
                    "Path:speech.config\r\n\r\n" +
                    configBody
                )

                // Trame SSML (synthèse)
                val ssmlUuid = UUID.randomUUID().toString().replace("-", "")
                webSocket.send(
                    "X-RequestId:$ssmlUuid\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "Path:ssml\r\n\r\n" +
                    ssml
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Vérifier si c'est la trame de fin
                if (text.contains("Path:turn.end")) {
                    Log.d(TAG, "Turn.end reçu — ${audioChunks.size()} octets audio")
                    webSocket.close(1000, "OK")
                    deferred.complete(audioChunks.toByteArray())
                } else if (text.contains("Path:audio")) {
                    audioStarted = true
                    Log.d(TAG, "Début flux audio MP3")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (audioStarted) {
                    audioChunks.write(bytes.toByteArray())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                deferred.completeExceptionally(
                    IllegalStateException("Edge TTS: échec WebSocket — ${t.message}", t)
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(
                        IllegalStateException("Edge TTS: WebSocket fermé inopinément (code=$code)")
                    )
                }
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)

        return try {
            deferred.await()
        } finally {
            // Assurer la fermeture du WebSocket en cas d'erreur
            if (!deferred.isCompleted) {
                webSocket.close(1000, "Cancelled")
            }
        }
    }

    /**
     * Convertit un identifiant technique de voix en libellé affichable.
     */
    private fun voiceDisplayName(voiceName: String): String = when (voiceName) {
        "fr-FR-VivienneNeural" -> "Vivienne (FR Edge)"
        "fr-FR-HenriNeural"    -> "Henri (FR Edge)"
        else                   -> voiceName
    }
}
