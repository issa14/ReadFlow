package com.readflow.service.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service d'inférence TTS via Sherpa-ONNX (moteur VITS Piper).
 *
 * Modèle : vits-piper-fr_FR-upmc-medium
 * - jessica (sid=0, féminine)
 * - pierre  (sid=1, masculine)
 */
@Singleton
class OnnxInferenceService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OnnxInference"
        private const val MODEL_NAME = "vits-piper-fr_FR-upmc-medium"
    }

    private var tts: OfflineTts? = null

    enum class Voice(val sid: Int, val label: String) {
        JESSICA(0, "Jessica"),
        PIERRE(1, "Pierre")
    }

    /**
     * Initialise le moteur en copiant le modèle depuis les assets.
     */
    fun initialize() {
        if (tts != null) return

        val modelDir = copyAssetsToInternal()
        val modelPath = "$modelDir/$MODEL_NAME.onnx"
        val dataDir = "$modelDir/espeak-ng-data"

        require(File(modelPath).exists()) { "Modèle introuvable : $modelPath" }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelPath,
            lexicon = "",
            tokens = "",
            dataDir = dataDir,
            dictDir = "",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )

        val modelConfig = OfflineTtsModelConfig().apply { vits = vitsConfig }

        val config = OfflineTtsConfig(
            model = modelConfig,
            ruleFsts = "",
            ruleFars = "",
            maxNumSentences = 1,
            silenceScale = 1.0f
        )

        tts = OfflineTts(context.assets, config)
        Log.i(TAG, "Sherpa-ONNX initialisé — ${tts!!.numSpeakers()} locuteurs, " +
                "${tts!!.sampleRate()} Hz")
    }

    fun synthesize(
        text: String,
        voice: Voice = Voice.JESSICA,
        speed: Float = 1.0f
    ): SynthesisResult {
        val engine = tts ?: throw IllegalStateException("Non initialisé.")

        val startMs = System.currentTimeMillis()
        val audio = engine.generate(text.trim(), voice.sid, speed.coerceIn(0.5f, 2.0f))
        val elapsedMs = System.currentTimeMillis() - startMs
        val durationMs = ((audio.samples.size.toFloat() / audio.sampleRate) * 1000).toLong()

        Log.i(TAG, "\"${text.take(60)}\" → ${audio.samples.size} éch., " +
                "${durationMs}ms, RTF=${"%.2f".format(elapsedMs.toFloat() / durationMs.coerceAtLeast(1))}")

        return SynthesisResult(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            text = text,
            voiceLabel = voice.label,
            synthesisTimeMs = elapsedMs,
            audioDurationMs = durationMs
        )
    }

    fun release() {
        tts?.release()
        tts = null
        Log.i(TAG, "Sherpa-ONNX libéré")
    }

    // ── Assets → stockage interne ──────────────────────────

    private fun copyAssetsToInternal(): String {
        val targetDir = File(context.filesDir, "models/$MODEL_NAME")
        val modelFile = File(targetDir, "$MODEL_NAME.onnx")
        if (modelFile.exists()) return targetDir.absolutePath

        Log.i(TAG, "Copie du modèle depuis les assets...")
        targetDir.mkdirs()
        copyAssetDir("models/$MODEL_NAME", targetDir)
        return targetDir.absolutePath
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        context.assets.list(assetPath)?.forEach { child ->
            val childPath = "$assetPath/$child"
            val childFile = File(context.filesDir, childPath.removePrefix("models/"))
            try {
                context.assets.open(childPath).use { input ->
                    childFile.parentFile?.mkdirs()
                    childFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                copyAssetDir(childPath, targetDir)
            }
        }
    }
}

data class SynthesisResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val text: String,
    val voiceLabel: String,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long
) {
    val realTimeFactor: Float
        get() = synthesisTimeMs.toFloat() / audioDurationMs.coerceAtLeast(1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SynthesisResult
        return samples.contentEquals(other.samples) && sampleRate == other.sampleRate && text == other.text
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + text.hashCode()
        return result
    }
}
