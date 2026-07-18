package com.inktone.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Client WebDAV léger pour synchronisation avec Nextcloud / Owncloud.
 *
 * Utilise OkHttp pour les requêtes PUT (upload) et GET (download).
 * Authentification HTTP Basic avec identifiant + mot de passe d'application.
 */
class WebDavClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authHeader: String
        get() {
            val credentials = "$username:$password"
            return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
        }

    /**
     * Upload le fichier [fileName] avec le contenu [data] vers le serveur WebDAV.
     * @return true si l'opération réussit (HTTP 201 ou 204).
     */
    suspend fun upload(fileName: String, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${serverUrl.trimEnd('/')}/$fileName"
                val request = Request.Builder()
                    .url(url)
                    .put(data.toRequestBody("application/octet-stream".toMediaType()))
                    .header("Authorization", authHeader)
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.close()
                    Result.success(Unit)
                } else {
                    val msg = "WebDAV upload échoué: HTTP ${response.code}"
                    response.close()
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Télécharge le fichier [fileName] depuis le serveur WebDAV.
     * @return le contenu binaire brut.
     */
    suspend fun download(fileName: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${serverUrl.trimEnd('/')}/$fileName"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", authHeader)
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    response.close()
                    Result.success(bytes)
                } else {
                    val msg = "WebDAV download échoué: HTTP ${response.code}"
                    response.close()
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Vérifie la connectivité en testant l'accès au répertoire racine.
     */
    suspend fun testConnection(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val url = serverUrl.trimEnd('/')
                val request = Request.Builder()
                    .url(url)
                    .method("PROPFIND", null)
                    .header("Authorization", authHeader)
                    .header("Depth", "0")
                    .build()
                val response = client.newCall(request).execute()
                val ok = response.isSuccessful
                response.close()
                Result.success(ok)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
