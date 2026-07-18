package com.inktone.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client Google Drive REST API léger pour synchronisation via appDataFolder.
 *
 * Le scope `drive.appdata` isole les données InkTone dans un dossier
 * invisible pour l'utilisateur dans son Drive principal.
 */
class GoogleDriveClient(
    private val accessToken: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://www.googleapis.com/drive/v3/files"

    /**
     * Upload un fichier dans l'appDataFolder via l'API multipart Drive v3.
     */
    suspend fun upload(fileName: String, data: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Chercher un fichier existant avec le même nom
                val existingId = findFileId(fileName)

                if (existingId != null) {
                    // Mise à jour du fichier existant
                    val url = "$baseUrl/$existingId?uploadType=media"
                    val request = Request.Builder()
                        .url(url)
                        .patch(data.toRequestBody("application/octet-stream".toMediaType()))
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                    val response = client.newCall(request).execute()
                    val ok = response.isSuccessful
                    response.close()
                    if (ok) Result.success(Unit)
                    else Result.failure(Exception("Drive update échoué: HTTP ${response.code}"))
                } else {
                    // Création nouveau fichier
                    val metadata = JSONObject().apply {
                        put("name", fileName)
                        put("parents", listOf("appDataFolder"))
                    }
                    // Multipart upload
                    val boundary = "inktone_boundary_${System.currentTimeMillis()}"
                    val body = buildMultipartBody(boundary, metadata.toString(), data)
                    val request = Request.Builder()
                        .url("$baseUrl?uploadType=multipart")
                        .post(body)
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "multipart/related; boundary=$boundary")
                        .build()
                    val response = client.newCall(request).execute()
                    val ok = response.isSuccessful
                    response.close()
                    if (ok) Result.success(Unit)
                    else Result.failure(Exception("Drive upload échoué: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Télécharge un fichier depuis l'appDataFolder.
     */
    suspend fun download(fileName: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val fileId = findFileId(fileName)
                    ?: return@withContext Result.failure(Exception("Fichier $fileName introuvable"))
                val url = "$baseUrl/$fileId?alt=media"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    response.close()
                    Result.success(bytes)
                } else {
                    response.close()
                    Result.failure(Exception("Drive download échoué: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun findFileId(fileName: String): String? {
        val query = "name='$fileName' and 'appDataFolder' in parents and trashed=false"
        val url = "$baseUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}&spaces=appDataFolder"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val body = response.body?.string()
        response.close()
        return try {
            val json = JSONObject(body ?: "{}")
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                files.getJSONObject(0).getString("id")
            } else null
        } catch (_: Exception) { null }
    }

    private fun buildMultipartBody(
        boundary: String,
        metadata: String,
        data: ByteArray
    ): okhttp3.RequestBody {
        val sb = StringBuilder()
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        sb.append(metadata)
        sb.append("\r\n--$boundary\r\n")
        sb.append("Content-Type: application/octet-stream\r\n")
        sb.append("Content-Transfer-Encoding: base64\r\n\r\n")

        val headerBytes = sb.toString().toByteArray(Charsets.UTF_8)
        val footerBytes = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val result = ByteArray(headerBytes.size + data.size + footerBytes.size)
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.size)
        System.arraycopy(data, 0, result, headerBytes.size, data.size)
        System.arraycopy(footerBytes, 0, result, headerBytes.size + data.size, footerBytes.size)

        return result.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
    }
}
