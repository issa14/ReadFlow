package com.inktone.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.inktone.data.database.*
import com.inktone.data.sync.BackupPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire d'export/import des données utilisateur (backup local).
 *
 * Exporte au format JSON toutes les données de lecture :
 * - Progression de lecture
 * - Signets
 * - Règles de prononciation
 * - Sessions de lecture
 * - Surlignages
 * - Annotations
 *
 * L'import restaure UNIQUEMENT les métadonnées — les livres EPUB
 * doivent déjà être présents dans l'application.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: InkToneDatabase,
    private val progressDao: ProgressDao,
    private val readingProgressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val pronunciationRuleDao: PronunciationRuleDao,
    private val readingSessionDao: ReadingSessionDao,
    private val highlightDao: HighlightDao,
    private val annotationDao: AnnotationDao
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val APP_VERSION = "0.3.0"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Exporte toutes les données utilisateur vers un fichier JSON via SAF.
     *
     * @param uri URI de destination (fourni par SAF createDocument).
     */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Export backup vers $uri")

        val payload = BackupPayload(
            version = 1,
            appVersion = APP_VERSION,
            createdAt = System.currentTimeMillis(),
            bookmarks = bookmarkDao.getAllSync(),
            pronunciationRules = pronunciationRuleDao.getAllSync(),
            progressEntries = progressDao.getAllSync(),
            readingProgressList = readingProgressDao.getAllSync(),
            readingSessions = readingSessionDao.getAllSync()
        )

        val json = gson.toJson(payload)

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(json)
            }
            Log.i(TAG, "Export réussi — ${json.length} caractères")
        } ?: throw IllegalStateException("Impossible d'écrire le fichier de sauvegarde")
    }

    /**
     * Importe les données depuis un fichier JSON via SAF.
     *
     * @param uri URI source (fourni par SAF openDocument).
     * @return Nombre d'entités restaurées.
     */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "Import backup depuis $uri")

        val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        } ?: throw IllegalStateException("Impossible de lire le fichier de sauvegarde")

        val payload = try {
            gson.fromJson(json, BackupPayload::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Fichier de sauvegarde invalide ou corrompu : ${e.message}")
        }

        Log.i(TAG, "Backup v${payload.version}, app v${payload.appVersion}, " +
            "${payload.bookmarks.size} signets, ${payload.progressEntries.size} progrès, " +
            "${payload.readingProgressList.size} lectures, ${payload.readingSessions.size} sessions")

        var restored = 0

        // Restaurer la progression
        for (progress in payload.progressEntries) {
            progressDao.upsert(progress)
            restored++
        }

        // Restaurer les signets
        for (bookmark in payload.bookmarks) {
            bookmarkDao.insert(bookmark)
            restored++
        }

        // Restaurer les règles de prononciation
        for (rule in payload.pronunciationRules) {
            pronunciationRuleDao.insertRule(rule)
            restored++
        }

        // Restaurer la progression de lecture (ReadingProgress)
        for (rp in payload.readingProgressList) {
            readingProgressDao.saveProgress(rp)
            restored++
        }

        // Restaurer les sessions de lecture
        for (session in payload.readingSessions) {
            readingSessionDao.insertSession(session)
            restored++
        }

        Log.i(TAG, "Import réussi — $restored entités restaurées")
        restored
    }
}
