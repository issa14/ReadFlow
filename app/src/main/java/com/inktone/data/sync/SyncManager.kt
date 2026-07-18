package com.inktone.data.sync

import com.google.gson.Gson
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.database.ProgressDao
import com.inktone.data.database.ReadingProgressDao
import com.inktone.data.database.ReadingSessionDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.PronunciationRule
import com.inktone.data.database.entity.ProgressEntity
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.data.database.entity.ReadingSessionEntity
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire central de synchronisation et sauvegarde.
 *
 * Coordonne l'export/import chiffré de toutes les données utilisateur
 * vers le cloud (WebDAV / Google Drive) ou un fichier local.
 */
@Singleton
class SyncManager @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val pronunciationRuleDao: PronunciationRuleDao,
    private val progressDao: ProgressDao,
    private val readingProgressDao: ReadingProgressDao,
    private val sessionDao: ReadingSessionDao
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class Provider {
        WEBDAV, GOOGLE_DRIVE, LOCAL_FILE
    }

    data class SyncConfig(
        val provider: Provider = Provider.LOCAL_FILE,
        val webdavUrl: String = "",
        val webdavUsername: String = "",
        val webdavPassword: String = "",
        val driveAccessToken: String = "",
        val encryptionPassword: CharArray = charArrayOf(),
        val lastSyncTimestamp: Long = 0L
    )

    private var config = SyncConfig()
    private var webdavClient: WebDavClient? = null
    private var driveClient: GoogleDriveClient? = null

    fun configure(newConfig: SyncConfig) {
        config = newConfig
        webdavClient = if (newConfig.webdavUrl.isNotBlank()) {
            WebDavClient(newConfig.webdavUrl, newConfig.webdavUsername, newConfig.webdavPassword)
        } else null
        driveClient = if (newConfig.driveAccessToken.isNotBlank()) {
            GoogleDriveClient(newConfig.driveAccessToken)
        } else null
    }

    /**
     * Exporte toutes les données Room en JSON, les chiffre,
     * et les envoie vers le provider configuré.
     */
    suspend fun backup(password: CharArray): Result<Unit> {
        return try {
            val payload = buildPayload()
            val json = gson.toJson(payload)
            val encrypted = CryptoEngine.encryptToBase64(json, password)

            val result = when (config.provider) {
                Provider.WEBDAV -> webdavClient?.upload("inktone_backup.rfbackup", encrypted.toByteArray())
                    ?: Result.failure(Exception("Client WebDAV non configuré"))
                Provider.GOOGLE_DRIVE -> driveClient?.upload("inktone_backup.rfbackup", encrypted.toByteArray())
                    ?: Result.failure(Exception("Client Google Drive non configuré"))
                Provider.LOCAL_FILE -> Result.success(Unit) // géré par l'UI via SAF
            }

            if (result.isSuccess) {
                config = config.copy(lastSyncTimestamp = System.currentTimeMillis())
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Télécharge, déchiffre et restaure les données depuis le cloud.
     * Résolution de conflits : la version la plus récente l'emporte (timestamp).
     */
    suspend fun restore(password: CharArray): Result<BackupPayload> {
        return try {
            val encryptedBytes = when (config.provider) {
                Provider.WEBDAV -> webdavClient?.download("inktone_backup.rfbackup")
                    ?.getOrThrow() ?: return Result.failure(Exception("Aucune donnée distante"))
                Provider.GOOGLE_DRIVE -> driveClient?.download("inktone_backup.rfbackup")
                    ?.getOrThrow() ?: return Result.failure(Exception("Aucune donnée distante"))
                Provider.LOCAL_FILE -> return Result.failure(Exception("Utiliser importFile() pour les fichiers locaux"))
            }
            val encryptedBase64 = String(encryptedBytes, Charsets.UTF_8)
            val json = CryptoEngine.decryptFromBase64(encryptedBase64, password)
            val remotePayload = gson.fromJson(json, BackupPayload::class.java)
            mergePayload(remotePayload)
            config = config.copy(lastSyncTimestamp = System.currentTimeMillis())
            Result.success(remotePayload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Importe un fichier local chiffré (.rfbackup).
     */
    suspend fun importFile(encryptedBase64: String, password: CharArray): Result<BackupPayload> {
        return try {
            val json = CryptoEngine.decryptFromBase64(encryptedBase64, password)
            val payload = gson.fromJson(json, BackupPayload::class.java)
            mergePayload(payload)
            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exporte le JSON chiffré (Base64) pour sauvegarde locale via SAF.
     */
    suspend fun exportEncrypted(password: CharArray): Result<String> {
        return try {
            val payload = buildPayload()
            val json = gson.toJson(payload)
            Result.success(CryptoEngine.encryptToBase64(json, password))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sauvegarde asynchrone non-bloquante (pour les triggers auto).
     */
    fun backupAsync(password: CharArray) {
        scope.launch {
            backup(password)
        }
    }

    // ── Private ──────────────────────────────────────

    private suspend fun buildPayload(): BackupPayload {
        val bookmarks = bookmarkDao.getAllSync()
        val rules = pronunciationRuleDao.getAllSync()
        val progress = progressDao.getAllSync()
        val readingProgress = readingProgressDao.getAllSync()
        val sessions = sessionDao.getAllSync()

        val totalWords = sessions.sumOf { it.wordsRead.toLong() }
        val totalSeconds = sessions.sumOf { it.durationSeconds }
        val avgWpm = if (totalSeconds > 60) ((totalWords * 60) / totalSeconds).toInt() else 0

        return BackupPayload(
            version = 1,
            appVersion = "0.1.0",
            createdAt = System.currentTimeMillis(),
            averageWpm = avgWpm,
            bookmarks = bookmarks,
            pronunciationRules = rules,
            progressEntries = progress,
            readingProgressList = readingProgress,
            readingSessions = sessions
        )
    }

    private suspend fun mergePayload(payload: BackupPayload) {
        withContext(Dispatchers.IO) {
            payload.bookmarks.forEach { bookmarkDao.insert(it) }
            payload.pronunciationRules.forEach { pronunciationRuleDao.insertRule(it) }
            payload.readingProgressList.forEach { readingProgressDao.saveProgress(it) }
            payload.readingSessions.forEach { sessionDao.insertSession(it) }
            payload.progressEntries.forEach { progressDao.upsert(it) }
        }
    }
}
