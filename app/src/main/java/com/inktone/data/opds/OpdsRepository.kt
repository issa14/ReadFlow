package com.inktone.data.opds

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour les catalogues OPDS.
 *
 * Gère les requêtes HTTP, l'authentification Basic et le parsing
 * XML/Atom → [OpdsFeed]. Émet via [Flow] pour la réactivité UI.
 */
@Singleton
class OpdsRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Charge un flux OPDS depuis l'URL d'un catalogue.
     *
     * @param catalog Configuration du serveur (URL + creds optionnels)
     * @param pageUrl URL spécifique (pour pagination) — si null, utilise catalog.url
     */
    fun loadFeed(catalog: OpdsCatalog, pageUrl: String? = null): Flow<Resource<OpdsFeed>> = flow {
        emit(Resource.Loading)
        try {
            val url = pageUrl ?: catalog.url
            val requestBuilder = Request.Builder().url(url).get()

            // Authentification Basic si configurée
            if (!catalog.username.isNullOrBlank()) {
                val credentials = "${catalog.username}:${catalog.password ?: ""}"
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                requestBuilder.header("Authorization", "Basic $encoded")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                emit(Resource.Error(
                    RuntimeException("HTTP ${response.code}"),
                    "Erreur serveur: ${response.code}"
                ))
                response.close()
                return@flow
            }

            val body = response.body?.string() ?: ""
            response.close()

            if (body.isBlank()) {
                emit(Resource.Error(RuntimeException("Réponse vide"), "Le serveur a renvoyé un flux vide"))
                return@flow
            }

            val feed = OpdsParser.parseAtomFeed(body)
            emit(Resource.Success(feed))
        } catch (e: java.net.SocketTimeoutException) {
            emit(Resource.Error(e, "Délai de connexion dépassé"))
        } catch (e: java.net.UnknownHostException) {
            emit(Resource.Error(e, "Serveur introuvable. Vérifiez l'URL et votre connexion"))
        } catch (e: java.io.IOException) {
            emit(Resource.Error(e, "Erreur réseau: ${e.message}"))
        } catch (e: Exception) {
            emit(Resource.Error(e, "Erreur inattendue: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
