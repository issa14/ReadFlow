package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"
        // Modèle Kokoro multilingue 82M (français, anglais, etc.)
        private const val ASSET_DIR = "models/kokoro-multi-lang-v1_0"
        private const val ONNX_FILE = "model.onnx"
        private const val VOICES_FILE = "voices.bin"
        private const val TOKENS_FILE = "tokens.txt"
    }

    private var tts: OfflineTts? = null

    /** Voix Kokoro disponibles (dépendent du pack voices.bin utilisé). */
    enum class Voice(val sid: Int, val label: String) {
        AF_HEART(0, "❤️ Heart (fr)"),
        AF_BELLA(1, "🔔 Bella (fr)"),
        AF_NICOLE(2, "🎙️ Nicole (fr)"),
        AF_AOEDE(3, "🎵 Aoede (en)"),
        AF_KORE(4, "🎶 Kore (en)"),
    }

    fun initialize() {
        if (tts != null) return

        val dataDir = copyEspeakDataToInternal()

        val kokoroConfig = OfflineTtsKokoroModelConfig(
            model = "$ASSET_DIR/$ONNX_FILE",
            voices = "$ASSET_DIR/$VOICES_FILE",
            tokens = "$ASSET_DIR/$TOKENS_FILE",
            dataDir = dataDir,
            lexicon = "",
            lang = "",
            dictDir = "",
            lengthScale = 1.0f
        )

        val modelConfig = OfflineTtsModelConfig().apply {
            kokoro = kokoroConfig
            numThreads = 4
            provider = "cpu"
        }
        val config = OfflineTtsConfig(modelConfig, "", "", 1, 1.0f)

        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "Kokoro OK — ${tts!!.numSpeakers()} locuteurs, ${tts!!.sampleRate()} Hz")
    }

    fun synthesize(text: String, voice: Voice = Voice.AF_HEART, speed: Float = 1.0f): SynthesisResult {
        val engine = tts ?: throw IllegalStateException("TTS non initialisé. Appeler initialize() d'abord.")
        val startMs = System.currentTimeMillis()
        val audio = engine.generate(text.trim(), voice.sid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()
        Log.i(TAG, "\"${text.take(60)}\" → ${audio.samples.size} éch., " +
                "${durationMs}ms, RTF=${"%.2f".format(elapsedMs / durationMs.coerceAtLeast(1).toFloat())}")
        return SynthesisResult(audio.samples, audio.sampleRate, text, voice.label, elapsedMs, durationMs)
    }

    /** Change la voix par défaut utilisée pour les prochaines synthèses. */
    fun setVoice(voice: Voice) {
        // La voix est passée à chaque appel synthesize(), pas besoin de reconfig
    }

    fun release() { tts?.release(); tts = null }

    /** Vérifie si le modèle Kokoro est présent dans les assets. */
    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open("$ASSET_DIR/$ONNX_FILE").close()
            true
        } catch (_: Exception) { false }
    }

    // ── Private ──────────────────────────────────────

    private fun copyEspeakDataToInternal(): String {
        val target = File(context.filesDir, "espeak-ng-data")
        if (target.exists() && target.isDirectory && target.listFiles()?.isNotEmpty() == true)
            return target.absolutePath
        Log.i(TAG, "Copie espeak-ng-data depuis les assets...")
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
            } catch (_: Exception) {
                copyAssetDir(childPath, File(targetDir, child))
            }
        }
    }
}
