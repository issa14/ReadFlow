package com.inktone.service.edge

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Décodeur MP3 → PCM FloatArray via [MediaCodec] Android.
 *
 * Utilise le pipeline standard Android MediaExtractor + MediaCodec,
 * sans dépendance externe. Le décodage est effectué sur [Dispatchers.IO].
 */
@Singleton
class Mp3Decoder @Inject constructor() {

    companion object {
        private const val TAG = "Mp3Decoder"
        private const val TIMEOUT_US = 10_000L
    }

    /**
     * Résultat du décodage MP3.
     */
    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int
    )

    /**
     * Décode un flux MP3 binaire en échantillons PCM normalisés [-1.0, 1.0].
     *
     * @param mp3Bytes Données MP3 brutes.
     * @param cacheDir Répertoire temporaire pour le fichier intermédiaire.
     * @return [DecodedAudio] contenant les échantillons FloatArray et le sampleRate.
     */
    suspend fun decode(
        mp3Bytes: ByteArray,
        cacheDir: File
    ): DecodedAudio = withContext(Dispatchers.IO) {
        val tempFile = File(cacheDir, "edge_tts_${System.nanoTime()}.mp3")
        try {
            // 1. Écrire les bytes MP3 dans un fichier temporaire
            FileOutputStream(tempFile).use { it.write(mp3Bytes) }
            tempFile.setReadOnly()

            // 2. Extraire le format audio
            val extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            val trackIndex = findAudioTrack(extractor)
                ?: throw IllegalStateException("Aucune piste audio trouvée dans le flux MP3")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("MIME type introuvable dans le format audio")

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            // 3. Configurer le décodeur
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            extractor.selectTrack(trackIndex)

            // 4. Décoder
            val allSamples = decodeToShortArray(extractor, codec)

            // 5. Convertir ShortArray → FloatArray (normalisation)
            val floatSamples = FloatArray(allSamples.size)
            for (i in allSamples.indices) {
                floatSamples[i] = allSamples[i].toFloat() / Short.MAX_VALUE.toFloat()
            }

            Log.d(TAG, "Décodage MP3: ${mp3Bytes.size} octets → ${floatSamples.size} éch. PCM @ $sampleRate Hz")

            codec.stop()
            codec.release()
            extractor.release()

            DecodedAudio(floatSamples, sampleRate)
        } finally {
            // Nettoyage du fichier temporaire
            tempFile.delete()
        }
    }

    /**
     * Trouve l'index de la première piste audio dans l'extracteur.
     */
    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    /**
     * Décode toutes les trames audio du flux MP3 en [ShortArray].
     *
     * Pattern MediaCodec correct : boucle sur `outputDone`
     * (et non sur `outputBuffers.isNotEmpty()` qui cause une boucle infinie).
     */
    private fun decodeToShortArray(
        extractor: MediaExtractor,
        codec: MediaCodec
    ): ShortArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val outputBuffers = mutableListOf<Short>()
        var inputDone = false
        var outputDone = false
        var chunkCount = 0
        var totalBytesFed = 0

        while (!outputDone) {
            // ── Alimenter le décodeur ──
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        Log.d(TAG, "TtsDebug | queueInputBuffer: EOS (fin du flux MP3, $totalBytesFed octets injectés)")
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        totalBytesFed += sampleSize
                        Log.d(TAG, "TtsDebug | queueInputBuffer: $sampleSize octets (total=$totalBytesFed)")
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // ── Récupérer la sortie décodée ──
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    // Vérifier le flag EOS en sortie
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.d(TAG, "TtsDebug | dequeueOutputBuffer: EOS reçu, décodage terminé ($chunkCount chunks PCM)")
                        outputDone = true
                    }
                    if (bufferInfo.size > 0) {
                        chunkCount++
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val shortCount = bufferInfo.size / 2
                        val shortBuf = ShortArray(shortCount)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.asShortBuffer().get(shortBuf)
                        outputBuffers.addAll(shortBuf.toList())
                        Log.d(TAG, "TtsDebug | dequeueOutputBuffer: chunk #$chunkCount → ${shortCount} shorts PCM (${bufferInfo.size} octets)")
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "TtsDebug | dequeueOutputBuffer: INFO_OUTPUT_FORMAT_CHANGED")
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Pas de sortie dispo, on réessaie
                }
                else -> {
                    Log.w(TAG, "TtsDebug | dequeueOutputBuffer: code inattendu $outputIndex")
                }
            }
        }

        Log.i(TAG, "TtsDebug | Décodage terminé: $chunkCount chunks → ${outputBuffers.size} shorts PCM (${totalBytesFed} octets MP3)")
        return outputBuffers.toShortArray()
    }
}
