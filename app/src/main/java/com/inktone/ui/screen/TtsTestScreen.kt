package com.inktone.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.SynthesisResult
import com.inktone.domain.repository.BookRepository
import com.inktone.service.audio.AudioPlaybackService
import com.inktone.service.audio.PlaybackOrchestrator
import com.inktone.service.onnx.OnnxInferenceService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran de test TTS — temporaire (Phase 0.6).
 * Permet de valider Sherpa-ONNX sur un device Android.
 */
@Composable
fun TtsTestScreen() {
    val context = LocalContext.current

    // Récupération du service Hilt
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnnxInferenceServiceEntryPoint::class.java
        )
    }
    val inferenceService = remember { entryPoint.onnxInferenceService() }
    val bookRepository = remember { entryPoint.bookRepository() }
    val orchestrator = remember { entryPoint.playbackOrchestrator() }

    var texte by remember { mutableStateOf("Bonjour, bienvenue dans InkTone.") }
    var vitesse by remember { mutableFloatStateOf(1.0f) }
    var voixIndex by remember { mutableIntStateOf(0) }
    var initialized by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SynthesisResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Permission notification (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* peu importe, on lance après */ }

    // Phrases de test pour la phonémisation française
    val phrasesTest = listOf(
        "Les amis arrivent dans une heure." to "liaison (les‿amis)",
        "Ils parlent souvent entre eux." to "muet (parlent, souvent)",
        "Un bon vin blanc." to "nasale (un, bon, vin, blanc)",
        "Je m'appelle François." to "élision (m'appelle)",
        "Le petit enfant a mangé un gâteau." to "liaison (petit‿enfant)",
        "Nous avons été très contents." to "liaison (nous‿avons, très‿contents)",
        "Il faut que j'y aille maintenant." to "élision + muet (j'y, aille)",
        "Prends-en un peu plus." to "liaison (prends-en, un)",
        "C'est incroyable ce qu'ils ont fait." to "élision (c'est, qu'ils)",
        "Les sciences et les arts sont magnifiques." to "muet final (sciences, arts, magnifiques)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎤 Test TTS — InkTone", style = MaterialTheme.typography.headlineSmall)

        // Initialisation
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        inferenceService.initialize()
                        initialized = true
                    } catch (e: Exception) {
                        error = e.message
                    }
                    loading = false
                }
            },
            enabled = !initialized && !loading
        ) {
            Text(if (loading) "Chargement..." else "1. Initialiser le moteur")
        }

        // ── EPUB import ──────────────────────────────
        var importedBook by remember { mutableStateOf<Book?>(null) }
        var chapter by remember { mutableStateOf<Chapter?>(null) }
        var importError by remember { mutableStateOf<String?>(null) }
        var importing by remember { mutableStateOf(false) }

        val epubPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                importing = true; importError = null
                try {
                    // Résoudre le nom réel du fichier (pas le path SAF)
                    val fileName = resolveFileName(uri, context) ?: "livre.epub"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val book = withContext(Dispatchers.IO) {
                            bookRepository.importEpub(stream, fileName)
                        }
                        importedBook = book
                        val chap = withContext(Dispatchers.IO) {
                            bookRepository.getChapter(book.id, 0)
                        }
                        chapter = chap
                    }
                } catch (e: Exception) {
                    importError = e.message
                    importedBook = null; chapter = null
                }
                importing = false
            }
        }

        Button(
            onClick = { epubPicker.launch(arrayOf("application/epub+zip")) },
            enabled = !importing
        ) {
            Text(if (importing) "⏳ Import..." else "📖 Importer un EPUB de test")
        }

        importedBook?.let { book ->
            var selectedChapter by remember { mutableIntStateOf(0) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📕 ${book.title}", style = MaterialTheme.typography.titleSmall)
                    Text("Auteur : ${book.author}")
                    Text("Chapitres : ${book.totalChapters}")
                    Text("Langue : ${book.language}")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Chapitre : ")
                        Button(onClick = { if (selectedChapter > 0) selectedChapter-- }) { Text("◀") }
                        Text(" ${selectedChapter + 1}/${book.totalChapters} ",
                            modifier = Modifier.padding(horizontal = 8.dp))
                        Button(onClick = { if (selectedChapter < book.totalChapters - 1) selectedChapter++ }) { Text("▶") }
                        Button(onClick = {
                            scope.launch {
                                chapter = withContext(Dispatchers.IO) {
                                    bookRepository.getChapter(book.id, selectedChapter)
                                }
                            }
                        }, modifier = Modifier.padding(start = 8.dp)) { Text("Charger") }
                    }

                    chapter?.let { ch ->
                        Text("Phrases : ${ch.sentences.size}", color = MaterialTheme.colorScheme.primary)
                        if (ch.sentences.isNotEmpty()) {
                            Text("\"${ch.sentences.first().text.take(80)}...\"",
                                style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    // Demander la permission de notification (Android 13+)
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                    }
                                    scope.launch {
                                        inferenceService.initialize()
                                        val intent = Intent(context, AudioPlaybackService::class.java)
                                        ContextCompat.startForegroundService(context, intent)
                                        orchestrator.play(
                                            ch.sentences, voixIndex, vitesse,
                                            bookTitle = book.title,
                                            chapterTitle = "Chapitre ${selectedChapter + 1}"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("▶ Lire le chapitre ${selectedChapter + 1}")
                            }
                        }
                    }
                }
            }
        }

        importError?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("❌ $it", modifier = Modifier.padding(12.dp))
            }
        }

        if (initialized) {
            Text("✅ Moteur prêt — 2 voix disponibles", color = MaterialTheme.colorScheme.primary)

            // Choix de la voix
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voix : ")
                OnnxInferenceService.Voice.entries.forEach { voice ->
                    FilterChip(
                        selected = voixIndex == voice.ordinal,
                        onClick = { voixIndex = voice.ordinal },
                        label = { Text(voice.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // Vitesse
            Text("Vitesse : ${"%.1f".format(vitesse)}x")
            Slider(
                value = vitesse,
                onValueChange = { vitesse = it },
                valueRange = 0.5f..2.0f,
                steps = 5
            )

            // Texte
            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it },
                label = { Text("Texte à synthétiser") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Synthétiser + Lire
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val r = withContext(Dispatchers.IO) {
                                inferenceService.synthesize(
                                    text = texte,
                                    voice = OnnxInferenceService.Voice.entries[voixIndex],
                                    speed = vitesse
                                )
                            }
                            result = r
                            // Jouer l'audio
                            playing = true
                            withContext(Dispatchers.IO) {
                                playPcm(r.samples, r.sampleRate)
                            }
                            playing = false
                        } catch (e: Exception) {
                            error = e.message
                        }
                        loading = false
                    }
                },
                enabled = !loading && texte.isNotBlank()
            ) {
                Text(if (playing) "🔊 Lecture..." else if (loading) "Synthèse..." else "2. Parler")
            }

            // Bouton test phonémisation (10 phrases)
            var testResults by remember { mutableStateOf<List<Pair<String, SynthesisResult>>>(emptyList()) }
            var testRunning by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    scope.launch {
                        testRunning = true
                        testResults = emptyList()
                        val results = mutableListOf<Pair<String, SynthesisResult>>()
                        for ((phrase, _) in phrasesTest) {
                            val r = withContext(Dispatchers.IO) {
                                inferenceService.synthesize(phrase, OnnxInferenceService.Voice.entries[voixIndex], vitesse)
                            }
                            results.add(phrase to r)
                            // Jouer la phrase
                            withContext(Dispatchers.IO) { playPcm(r.samples, r.sampleRate) }
                            // Petite pause entre les phrases
                            withContext(Dispatchers.IO) { Thread.sleep(300) }
                        }
                        testResults = results
                        testRunning = false
                    }
                },
                enabled = !testRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (testRunning) "⏳ Test en cours..." else "3. Test phonémisation (10 phrases)")
            }

            // Benchmark RTF (phrases courtes à longues)
            var benchResults by remember { mutableStateOf<List<Triple<String, Long, Float>>>(emptyList()) }
            var benchRunning by remember { mutableStateOf(false) }

            val benchPhrases = listOf(
                "Bonjour." to "très courte",
                "Il fait beau aujourd'hui." to "courte",
                "Les enfants jouent dans le jardin pendant que leurs parents préparent le dîner." to "moyenne",
                "La lecture est une activité merveilleuse qui permet de s'évader, d'apprendre et de découvrir des mondes inconnus sans quitter son fauteuil." to "longue",
                "L'intelligence artificielle transforme profondément notre rapport au savoir et à la connaissance, ouvrant des perspectives inédites dans des domaines aussi variés que la médecine, l'éducation ou encore la création artistique." to "très longue"
            )

            Button(
                onClick = {
                    scope.launch {
                        benchRunning = true
                        benchResults = emptyList()
                        val results = mutableListOf<Triple<String, Long, Float>>()
                        for ((phrase, label) in benchPhrases) {
                            val r = withContext(Dispatchers.IO) {
                                inferenceService.synthesize(phrase, OnnxInferenceService.Voice.entries[voixIndex], vitesse)
                            }
                            results.add(Triple(label, r.synthesisTimeMs, r.realTimeFactor))
                        }
                        benchResults = results
                        benchRunning = false
                    }
                },
                enabled = !benchRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text(if (benchRunning) "⏳ Benchmark..." else "4. Benchmark RTF (5 longueurs)")
            }

            if (benchResults.isNotEmpty()) {
                Text("📊 RTF par longueur :", style = MaterialTheme.typography.titleSmall)
                benchResults.forEach { (label, time, rtf) ->
                    Text(
                        "• $label : ${time}ms, RTF=${"%.2f".format(rtf)} ${if (rtf < 1f) "✅" else "⚠️"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Test end-to-end : texte → phrases → timestamps
            var e2eResults by remember { mutableStateOf<List<Triple<String, Long, Long>>>(emptyList()) }
            var e2eRunning by remember { mutableStateOf(false) }
            var e2eTotalMs by remember { mutableLongStateOf(0L) }

            val e2eText = "La lecture est une activité merveilleuse. " +
                    "Elle permet de s'évader dans des mondes inconnus. " +
                    "Chaque page tournée est une nouvelle aventure. " +
                    "Les mots dansent devant nos yeux émerveillés."

            Button(
                onClick = {
                    scope.launch {
                        e2eRunning = true; e2eResults = emptyList()
                        val sentences = e2eText.split(Regex("(?<=[.!?])\\s+"))
                        var timelineMs = 0L
                        val results = mutableListOf<Triple<String, Long, Long>>()
                        for (sentence in sentences) {
                            val r = withContext(Dispatchers.IO) {
                                inferenceService.synthesize(sentence,
                                    OnnxInferenceService.Voice.entries[voixIndex], vitesse)
                            }
                            val start = timelineMs; timelineMs += r.audioDurationMs
                            results.add(Triple(sentence.trim(), start, timelineMs))
                            withContext(Dispatchers.IO) { playPcm(r.samples, r.sampleRate) }
                            withContext(Dispatchers.IO) { Thread.sleep(200) }
                        }
                        e2eResults = results; e2eTotalMs = timelineMs; e2eRunning = false
                    }
                },
                enabled = !e2eRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(if (e2eRunning) "⏳ Test timestamps..." else "5. Test timestamps (4 phrases + son)")
            }

            if (e2eResults.isNotEmpty()) {
                Text("⏱️ Timeline (${e2eResults.size} phrases, ${e2eTotalMs}ms) :",
                    style = MaterialTheme.typography.titleSmall)
                e2eResults.forEachIndexed { i, (phrase, start, end) ->
                    Text("[${start / 1000}.${(start % 1000) / 100}s → ${end / 1000}.${(end % 1000) / 100}s] $phrase",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("✅ Prêt pour le surlignage synchronisé !",
                    color = MaterialTheme.colorScheme.primary)
            }
            // Résultats du test phonémisation
            if (testResults.isNotEmpty()) {
                Text("📋 Résultats (${testResults.size} phrases) :", style = MaterialTheme.typography.titleSmall)
                var totalMs = 0L
                testResults.forEachIndexed { i, (phrase, r) ->
                    totalMs += r.synthesisTimeMs
                    Text(
                        "${i + 1}. RTF=${"%.2f".format(r.realTimeFactor)} | ${r.audioDurationMs}ms — \"${phrase.take(50)}...\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.realTimeFactor < 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                val avgRtf = testResults.map { it.second.realTimeFactor }.average()
                Text(
                    "📊 RTF moyen : ${"%.2f".format(avgRtf)} (${if (avgRtf < 1f) "✅" else "⚠️"}) | Temps total : ${totalMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Résultat individuel
            result?.let { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📊 Résultat", style = MaterialTheme.typography.titleSmall)
                        Text("• Échantillons : ${r.samples.size}")
                        Text("• Fréquence : ${r.sampleRate} Hz")
                        Text("• Durée audio : ${r.audioDurationMs} ms")
                        Text("• Temps synthèse : ${r.synthesisTimeMs} ms")
                        Text("• RTF : ${"%.2f".format(r.realTimeFactor)} ${if (r.realTimeFactor < 1f) "✅" else "⚠️"}")
                        Text("• Voix : ${r.voiceLabel}")
                    }
                }
            }
        }

        // Erreurs
        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "❌ $it",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/** Point d'entrée Hilt pour injecter OnnxInferenceService dans un contexte Compose */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnnxInferenceServiceEntryPoint {
    fun onnxInferenceService(): OnnxInferenceService
    fun bookRepository(): BookRepository
    fun playbackOrchestrator(): PlaybackOrchestrator
}

/** Résout le vrai nom du fichier à partir d'une URI SAF. */
private fun resolveFileName(uri: Uri, context: android.content.Context): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        } else null
    }
}

/** Joue des échantillons PCM float via AudioTrack. */
private fun playPcm(samples: FloatArray, sampleRate: Int) {
    val bufferSize = maxOf(
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
        samples.size * 4 // float = 4 bytes
    )

    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    track.play()
    // Écriture par blocs pour éviter de dépasser le buffer
    val chunkSize = bufferSize / 4
    var offset = 0
    while (offset < samples.size) {
        val len = minOf(chunkSize, samples.size - offset)
        track.write(samples, offset, len, AudioTrack.WRITE_BLOCKING)
        offset += len
    }
    // Attendre la fin
    Thread.sleep((samples.size.toLong() * 1000 / sampleRate) + 200)
    track.stop()
    track.release()
}
