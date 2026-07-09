package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.readflow.domain.model.SynthesisResult
import dagger.hilt.android.qualifiers.ApplicationContext
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

    /** Piper VITS : modèle mono-locuteur français. */
    enum class Voice(val sid: Int, val label: String) {
        MIRO(0, "Miro (FR high)"),
    }

    // ── API publique ───────────────────────────────────────────────

    fun initialize() {
        if (tts != null) return

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

        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "Piper VITS OK — ${tts!!.numSpeakers()} locuteur, ${tts!!.sampleRate()} Hz")
    }

    fun synthesize(
        text: String,
        voice: Voice = Voice.MIRO,
        speed: Float = 1.0f
    ): SynthesisResult {
        val engine = tts
            ?: throw IllegalStateException("TTS non initialisé.")

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
        return SynthesisResult(audio.samples, audio.sampleRate, cleaned,
            voice.label, elapsedMs, durationMs)
    }

    fun release() { tts?.release(); tts = null }

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
