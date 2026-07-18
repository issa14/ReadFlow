package com.inktone.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "OnnxInference"
        private const val ASSET_DIR_UPMC = "models/vits-piper-fr_FR-upmc"
        private const val ONNX_FILE_UPMC  = "fr_FR-upmc.onnx"
        private const val ASSET_DIR_MIRO = "models/vits-piper-fr_FR-miro-high"
        private const val ONNX_FILE_MIRO  = "fr_FR-miro-high.onnx"
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

    /** Modèle actuellement chargé (pour adapter les SID). */
    @Volatile private var loadedModelDir: String? = null

    /** Mutex pour sérialiser l'initialisation. */
    private val initMutex = Mutex()

    /** Voix disponibles. */
    enum class Voice(val sid: Int, val label: String) {
        MIRO(0, "Miro (FR high)"),
        JESSICA(0, "Jessica (FR) — UPMC"),
        PIERRE(1, "Pierre (FR) — UPMC"),
    }

    // ── Paramètres prosodiques ajustables ──────────────────────

    /**
     * Rythme de la voix (1.0 = normal, > 1.0 ralentit).
     * Pour Miro, 1.08 est recommandé pour un débit naturel en français.
     */
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
     * Thread-safe via [Mutex] : le fast-path hors-mutex évite toute
     * attente inutile après la première initialisation.
     *
     * Crash guard UPMC : si une initialisation UPMC précédente a
     * crashé le process natif, on bascule automatiquement sur Miro.
     */
    suspend fun initialize() {
        // Fast-path sans mutex : si déjà prêt, on ne bloque personne
        if (isInitialized) return
        initMutex.withLock {
            // Double-check sous le mutex
            if (isInitialized || isInitializing) return
            isInitializing = true
            try {
                // Crash guard : si le flag est levé, UPMC a crashé le process
                // au lancement précédent → on le saute pour éviter une boucle
                val skipUpmc = settingsRepository.upmcInitFailed.first()
                if (skipUpmc) {
                    Log.w(TAG, "Crash UPMC détecté au lancement précédent, fallback Miro forcé")
                }

                // Si on va tenter UPMC, écrire le flag AVANT l'appel natif
                // pour que le prochain lancement sache que ça a crashé
                if (!skipUpmc && isModelAvailable(ASSET_DIR_UPMC, ONNX_FILE_UPMC)) {
                    settingsRepository.setUpmcInitFlag()
                }

                val success = withContext(Dispatchers.IO) {
                    doInitialize(skipUpmc)
                }

                // Succès → effacer le flag crash
                if (success && !skipUpmc) {
                    settingsRepository.clearUpmcInitFlag()
                }

                isInitialized = success
            } finally {
                isInitializing = false
            }
        }
    }

    /**
     * Initialise le modèle ONNX sur le thread courant (doit être appelé
     * depuis [Dispatchers.IO]).
     *
     * @param skipUpmc si true, ignore le modèle UPMC et utilise Miro directement
     *                 (utilisé quand un crash natif a été détecté au lancement précédent)
     * @return true si l'initialisation a réussi
     */
    private fun doInitialize(skipUpmc: Boolean = false): Boolean {
        // UPMC (Jessica + Pierre) — modèle principal
        // Miro — fallback automatique si UPMC absent ou crashé
        val (assetDir, onnxFile) = when {
            skipUpmc -> {
                Log.w(TAG, "UPMC désactivé (crash guard), utilisation Miro")
                ASSET_DIR_MIRO to ONNX_FILE_MIRO
            }
            isModelAvailable(ASSET_DIR_UPMC, ONNX_FILE_UPMC) -> {
                ASSET_DIR_UPMC to ONNX_FILE_UPMC
            }
            else -> {
                Log.w(TAG, "Modèle UPMC introuvable, fallback Miro")
                ASSET_DIR_MIRO to ONNX_FILE_MIRO
            }
        }

        try {
            // 1. Vérification d'intégrité du modèle ONNX
            if (!isModelAvailable(assetDir, onnxFile)) {
                throw IllegalStateException("Le fichier modèle $assetDir/$onnxFile est introuvable ou corrompu.")
            }
            
            // 2. Vérification d'intégrité du fichier de tokens
            try {
                context.assets.open("$assetDir/$TOKENS_TXT").use { input ->
                    if (input.available() <= 0) {
                        throw IllegalStateException("Le fichier de tokens $TOKENS_TXT est vide.")
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Le fichier de tokens $TOKENS_TXT est inaccessible ou corrompu : ${e.message}")
            }

            val dataDir = copyEspeakDataToInternal()

            val vitsConfig = OfflineTtsVitsModelConfig(
                model    = "$assetDir/$onnxFile",
                tokens   = "$assetDir/$TOKENS_TXT",
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

            Log.i(TAG, "Initialisation du moteur TTS ($assetDir)...")
            tts = OfflineTts(context.assets, config)
            loadedModelDir = assetDir
            Log.i(TAG, "TTS OK — ${tts!!.numSpeakers()} locuteur(s), ${tts!!.sampleRate()} Hz, modèle: $assetDir")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur fatale lors de l'initialisation du moteur natif Sherpa-ONNX", t)
            tts = null
            loadedModelDir = null
            return false
        }
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
        Log.d(TAG, "synth: \"$cleaned\"")
        val startMs = System.currentTimeMillis()
        // Clamper le SID au nombre de locuteurs du modèle chargé
        // (Miro n'a qu'1 locuteur, UPMC en a 2)
        val safeSid = if (loadedModelDir == ASSET_DIR_MIRO) 0
            else voice.sid.coerceIn(0, (tts?.numSpeakers() ?: 1) - 1)
        val audio = engine.generate(cleaned, safeSid, speed.coerceIn(0.5f, 2.0f))
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
        loadedModelDir = null
        Log.i(TAG, "Modèle ONNX libéré")
    }

    fun getSampleRate(): Int = tts?.sampleRate() ?: 22050

    private fun isModelAvailable(dir: String, file: String): Boolean {
        return try {
            context.assets.open("$dir/$file").use { true }
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
        val target = File(context.filesDir, "espeak-ng-data")
        if (target.isDirectory && target.listFiles()?.isNotEmpty() == true)
            return target.absolutePath
        Log.i(TAG, "Copie espeak-ng-data → ${target.absolutePath}")
        target.mkdirs()
        // Utiliser les données eSpeak du modèle Miro (compatibles UPMC, même langue)
        copyAssetDir("$ASSET_DIR_MIRO/espeak-ng-data", target)
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
