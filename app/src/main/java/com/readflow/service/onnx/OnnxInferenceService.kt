package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service d'inférence TTS via Sherpa-ONNX / Piper VITS français.
 *
 * Modèle : vits-piper-fr_FR-miro-high (64 Mo, voix masculine FR haute qualité).
 * Architecture VITS légère → RTF excellent sur Snapdragon 680.
 */
@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"
        private const val ASSET_DIR = "models/vits-piper-fr_FR-miro-high"
        private const val ONNX_FILE  = "fr_FR-miro-high.onnx"
        private const val TOKENS_TXT = "tokens.txt"
    }

    @Volatile private var tts: OfflineTts? = null

    /** true lorsque le modèle ONNX est chargé et prêt pour l'inférence. */
    @Volatile var isInitialized: Boolean = false
        private set

    /** true pendant l'initialisation (évite les double-init concurrents). */
    @Volatile private var isInitializing: Boolean = false

    /** Mutex pour sérialiser l'initialisation. */
    private val initMutex = Mutex()

    /** Piper VITS : modèle mono-locuteur français. */
    enum class Voice(val sid: Int, val label: String) {
        MIRO(0, "Miro (FR high)"),
    }

    // ── API publique ───────────────────────────────────────────────

    /**
     * Initialise le modèle ONNX en arrière-plan ([Dispatchers.IO]).
     *
     * Idempotente : si déjà initialisé, retourne immédiatement.
     * Thread-safe via [Mutex] : le fast-path hors-mutex évite toute
     * attente inutile après la première initialisation.
     */
    suspend fun initialize() {
        // Fast-path sans mutex : si déjà prêt, on ne bloque personne
        if (isInitialized) return
        initMutex.withLock {
            // Double-check sous le mutex
            if (isInitialized || isInitializing) return
            isInitializing = true
            try {
                withContext(Dispatchers.IO) {
                    doInitialize()
                }
                isInitialized = true
            } finally {
                isInitializing = false
            }
        }
    }

    private fun doInitialize() {
        try {
            // 1. Vérification d'intégrité du modèle ONNX
            if (!isModelAvailable()) {
                throw IllegalStateException("Le fichier modèle $ASSET_DIR/$ONNX_FILE est introuvable ou corrompu.")
            }
            
            // 2. Vérification d'intégrité du fichier de tokens
            try {
                context.assets.open("$ASSET_DIR/$TOKENS_TXT").use { input ->
                    if (input.available() <= 0) {
                        throw IllegalStateException("Le fichier de tokens $TOKENS_TXT est vide.")
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Le fichier de tokens $TOKENS_TXT est inaccessible ou corrompu : ${e.message}")
            }

            val dataDir = copyEspeakDataToInternal()

            val vitsConfig = OfflineTtsVitsModelConfig(
                model    = "$ASSET_DIR/$ONNX_FILE",
                tokens   = "$ASSET_DIR/$TOKENS_TXT",
                dataDir  = dataDir,
                lexicon  = "",
                dictDir  = "",
                noiseScale  = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig().apply {
                vits = vitsConfig
                numThreads = 4
                provider = "cpu"
                debug = true
            }
            val config = OfflineTtsConfig(modelConfig, "", "", 1, 1.0f)

            Log.i(TAG, "Tentative d'initialisation du moteur natif OfflineTts...")
            tts = OfflineTts(context.assets, config)
            Log.i(TAG, "Piper VITS OK — ${tts!!.numSpeakers()} locuteur, ${tts!!.sampleRate()} Hz")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur fatale lors de l'initialisation du moteur natif Sherpa-ONNX", t)
            tts = null
            throw RuntimeException("Erreur de moteur de synthèse vocale natif (ONNX/JNI) : ${t.message}", t)
        }
    }

    /**
     * Synthétise un texte en audio PCM sur [Dispatchers.IO].
     *
     * @throws IllegalStateException si le modèle n'est pas initialisé.
     */
    suspend fun synthesize(
        text: String,
        voice: Voice = Voice.MIRO,
        speed: Float = 1.0f
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val engine = tts
            ?: throw IllegalStateException("TTS non initialisé. Appeler initialize() d'abord.")

        val cleaned = text
            .trim()
            .replace(Regex("([.!?])\\s+\\1\\s+\\1"), "$1$1$1") // "! ! !" → "!!!"
            .replace(Regex("([.!?])\\1{2,}"), "$1")              // "......" → "."
            .replace("\u200B", "")                                // zero-width space
            .replace("\u00a0", " ")                               // non-breaking space → espace
            .replace(Regex("\\s+"), " ")                          // normalise espaces
        Log.d(TAG, "synth: \"$cleaned\"")
        val startMs = System.currentTimeMillis()
        val audio = engine.generate(cleaned, voice.sid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()
        val rtf = elapsedMs / durationMs.coerceAtLeast(1).toFloat()

        Log.i(TAG, "\"${cleaned.take(60)}\" → ${audio.samples.size} éch., " +
                "${durationMs}ms, RTF=%.2f".format(rtf))
        SynthesisResult(audio.samples, audio.sampleRate, cleaned,
            voice.label, elapsedMs, durationMs)
    }

    /**
     * Libère le modèle ONNX et les ressources natives.
     *
     * Appelée par [AudioPlaybackService.onDestroy] lors de la destruction
     * du service (pas à chaque pause de lecture). Le modèle devra être
     * ré-initialisé via [initialize] avant la prochaine utilisation.
     */
    fun release() {
        Log.i(TAG, "Libération du modèle ONNX...")
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération TTS: ${e.message}", e)
        }
        tts = null
        isInitialized = false
        Log.i(TAG, "Modèle ONNX libéré")
    }

    fun getSampleRate(): Int = tts?.sampleRate() ?: 22050

    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open("$ASSET_DIR/$ONNX_FILE").use { true }
        } catch (_: Exception) { false }
    }

    // ── Private ───────────────────────────────────────────────────

    /** Copie espeak-ng-data des assets → stockage interne (une seule fois). */
    private fun copyEspeakDataToInternal(): String {
        val target = File(context.filesDir, "espeak-ng-data")
        if (target.isDirectory && target.listFiles()?.isNotEmpty() == true)
            return target.absolutePath
        Log.i(TAG, "Copie espeak-ng-data → ${target.absolutePath}")
        target.mkdirs()
        copyAssetDir("$ASSET_DIR/espeak-ng-data", target)
        return target.absolutePath
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        context.assets.list(assetPath)?.forEach { child ->
            val childPath = "$assetPath/$child"
            val childFile = File(targetDir, child)
            try {
                context.assets.open(childPath).use { input ->
                    childFile.parentFile?.mkdirs()
                    childFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: java.io.FileNotFoundException) {
                copyAssetDir(childPath, File(targetDir, child))
            }
        }
    }
}
