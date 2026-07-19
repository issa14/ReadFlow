package com.inktone.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.inktone.domain.model.SynthesisResult
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
 * Modèle : vits-piper-fr_FR-upmc-medium (73 Mo, 2 locuteurs : Jessica + Pierre).
 * Architecture VITS légère → RTF ~0.33 sur Snapdragon 680.
 */
@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"
        private const val ASSET_DIR = "models/vits-piper-fr_FR-upmc-medium"
        private const val ONNX_FILE = "fr_FR-upmc-medium.onnx"
        private const val TOKENS_TXT = "tokens.txt"

        // Patterns compilés une seule fois (thread-safe, immuables)
        private val MULTIPLE_PUNCT_SPACES = Regex("([.!?])\\s+\\1\\s+\\1")
        private val REPEATED_PUNCT = Regex("([.!?])\\1{2,}")
        private val MULTIPLE_SPACES = Regex("\\s+")
    }

    @Volatile private var tts: OfflineTts? = null

    /** true lorsque le modèle ONNX est chargé et prêt pour l'inférence. */
    @Volatile var isInitialized: Boolean = false
        private set

    /** true pendant l'initialisation. */
    @Volatile private var isInitializing: Boolean = false

    /** Mutex pour sérialiser l'initialisation. */
    private val initMutex = Mutex()

    /** Voix disponibles (ordre important : JESSICA en premier pour SID 0). */
    enum class Voice(val sid: Int, val label: String) {
        JESSICA(0, "Jessica (FR)"),
        PIERRE(1, "Pierre (FR)"),
    }

    // ── Paramètres prosodiques ajustables ──────────────────────

    /** Rythme de la voix (1.0 = normal, > 1.0 ralentit). */
    @Volatile var voiceLengthScale: Float = 1.08f

    /** Variabilité prosodique (0.0–1.0). Défaut Piper VITS : 0.667. */
    @Volatile var voiceNoiseScale: Float = 0.667f

    /** Variabilité prosodique conditionnée. Défaut Piper VITS : 0.8. */
    @Volatile var voiceNoiseScaleW: Float = 0.8f

    // ── API publique ───────────────────────────────────────────────

    /**
     * Initialise le modèle ONNX en arrière-plan ([Dispatchers.IO]).
     *
     * Idempotente : si déjà initialisé, retourne immédiatement.
     * Thread-safe via [Mutex].
     */
    suspend fun initialize() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized || isInitializing) return
            isInitializing = true
            try {
                withContext(Dispatchers.IO) {
                    doInitialize()
                }
                isInitialized = true
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur fatale lors de l'initialisation du moteur natif Sherpa-ONNX", t)
                tts = null
            } finally {
                isInitializing = false
            }
        }
    }

    private fun doInitialize() {
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
            noiseScale  = voiceNoiseScale,
            noiseScaleW = voiceNoiseScaleW,
            lengthScale = voiceLengthScale
        )

        val modelConfig = OfflineTtsModelConfig().apply {
            vits = vitsConfig
            numThreads = 4
            provider = "cpu"
            debug = true
        }
        val config = OfflineTtsConfig(modelConfig, "", "", 1, 1.0f)

        Log.i(TAG, "Initialisation du moteur TTS ($ASSET_DIR)...")
        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "TTS OK — ${tts!!.numSpeakers()} locuteur(s), ${tts!!.sampleRate()} Hz")
    }

    /**
     * Synthétise un texte en audio PCM sur [Dispatchers.IO].
     *
     * @throws IllegalStateException si le modèle n'est pas initialisé.
     */
    suspend fun synthesize(
        text: String,
        voice: Voice = Voice.JESSICA,
        speed: Float = 1.0f
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val engine = tts
            ?: throw IllegalStateException("TTS non initialisé. Appeler initialize() d'abord.")

        val cleaned = text
            .trim()
            .replace(MULTIPLE_PUNCT_SPACES, "$1$1$1")
            .replace(REPEATED_PUNCT, "$1")
            .replace("\u200B", "")
            .replace("\u00a0", " ")
            .replace(MULTIPLE_SPACES, " ")
        val startMs = System.currentTimeMillis()
        // Clamper le SID au nombre de locuteurs du modèle (2 pour UPMC)
        val safeSid = voice.sid.coerceIn(0, (engine.numSpeakers() - 1).coerceAtLeast(0))

        Log.d(TAG, "synth: sid=$safeSid \"${cleaned.take(60)}\"")
        val audio = engine.generate(cleaned, safeSid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()
        val rtf = elapsedMs / durationMs.coerceAtLeast(1).toFloat()

        Log.i(TAG, "sid=$safeSid \"${cleaned.take(60)}\" → ${audio.samples.size} éch., " +
                "${durationMs}ms, RTF=%.2f".format(rtf))
        SynthesisResult(audio.samples, audio.sampleRate, cleaned,
            voice.label, elapsedMs, durationMs)
    }

    /** Libère le modèle ONNX et les ressources natives. */
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

    private fun isModelAvailable(): Boolean {
        return try {
            context.assets.open("$ASSET_DIR/$ONNX_FILE").use { true }
        } catch (_: Exception) { false }
    }

    /**
     * Ajuste les paramètres prosodiques VITS.
     * Les valeurs prennent effet après ré-initialisation du modèle
     * (appeler [release] puis [initialize] pour appliquer).
     */
    fun setVoiceParams(lengthScale: Float, noiseScale: Float, noiseScaleW: Float) {
        voiceLengthScale = lengthScale.coerceIn(0.5f, 2.0f)
        voiceNoiseScale  = noiseScale.coerceIn(0.1f, 1.5f)
        voiceNoiseScaleW = noiseScaleW.coerceIn(0.1f, 1.5f)
        Log.i(TAG, "Voice params updated: ls=%.2f ns=%.2f nsw=%.2f"
            .format(voiceLengthScale, voiceNoiseScale, voiceNoiseScaleW))
    }

    // ── Private ───────────────────────────────────────────────────

    /** Copie espeak-ng-data des assets → stockage interne (une seule fois). */
    private fun copyEspeakDataToInternal(): String {
        val target = File(context.filesDir, "espeak-ng-data/upmc-medium")
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
