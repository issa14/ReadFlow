package com.inktone.ui.screen.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.inktone.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SyncSettingsUiState(
    val webdavUrl: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    val webdavConnected: Boolean = false,
    val driveConnected: Boolean = false,
    val encryptionPassword: String = "",
    val isLoading: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val syncManager: SyncManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    fun updateWebdavUrl(url: String) = _uiState.update { it.copy(webdavUrl = url) }
    fun updateWebdavUser(user: String) = _uiState.update { it.copy(webdavUsername = user) }
    fun updateWebdavPass(pass: String) = _uiState.update { it.copy(webdavPassword = pass) }
    fun updatePassword(pass: String) = _uiState.update { it.copy(encryptionPassword = pass) }

    fun connectGoogleDrive() {
        _uiState.update { it.copy(statusMessage = "Lancement connexion Google...", isError = false) }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).result
                    ?: throw Exception("Compte Google non récupéré")

                val token = withContext(Dispatchers.IO) {
                    com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        appContext,
                        account.account!!,
                        "oauth2:https://www.googleapis.com/auth/drive.appdata"
                    )
                }

                syncManager.configure(
                    SyncManager.SyncConfig(
                        provider = SyncManager.Provider.GOOGLE_DRIVE,
                        driveAccessToken = token
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        driveConnected = true,
                        statusMessage = "✓ Connecté : ${account.email}",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        driveConnected = false,
                        statusMessage = "Échec connexion Drive: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    suspend fun testWebdav() {
        val state = _uiState.value
        if (state.webdavUrl.isBlank() || state.webdavUsername.isBlank()) {
            _uiState.update { it.copy(statusMessage = "URL et identifiant requis", isError = true) }
            return
        }
        _uiState.update { it.copy(isLoading = true, webdavConnected = false) }

        syncManager.configure(
            SyncManager.SyncConfig(
                provider = SyncManager.Provider.WEBDAV,
                webdavUrl = state.webdavUrl,
                webdavUsername = state.webdavUsername,
                webdavPassword = state.webdavPassword
            )
        )

        // Tester via le client WebDAV
        val client = com.inktone.data.sync.WebDavClient(
            state.webdavUrl, state.webdavUsername, state.webdavPassword
        )
        val result = client.testConnection()
        result.fold(
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, webdavConnected = true, statusMessage = "Connexion WebDAV réussie", isError = false) }
            },
            onFailure = { e ->
                _uiState.update { it.copy(isLoading = false, statusMessage = "Échec: ${e.message}", isError = true) }
            }
        )
    }

    suspend fun syncNow() {
        val state = _uiState.value
        val password = state.encryptionPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Définissez un mot de passe de chiffrement", isError = true) }
            return
        }

        _uiState.update { it.copy(isLoading = true, statusMessage = "Sauvegarde en cours...", isError = false) }

        syncManager.configure(
            SyncManager.SyncConfig(
                provider = SyncManager.Provider.WEBDAV,
                webdavUrl = state.webdavUrl,
                webdavUsername = state.webdavUsername,
                webdavPassword = state.webdavPassword
            )
        )

        val result = syncManager.backup(password.toCharArray())
        result.fold(
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        statusMessage = "Sauvegarde réussie",
                        isError = false
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = "Échec: ${e.message}", isError = true)
                }
            }
        )
    }

    fun exportToUri(context: Context, uri: Uri) {
        val password = _uiState.value.encryptionPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Mot de passe requis", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = syncManager.exportEncrypted(password.toCharArray())
                result.fold(
                    onSuccess = { base64 ->
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(base64.toByteArray())
                            }
                        }
                        _uiState.update { it.copy(isLoading = false, statusMessage = "Export réussi", isError = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, statusMessage = "Erreur: ${e.message}", isError = true) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Erreur export: ${e.message}", isError = true) }
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        val password = _uiState.value.encryptionPassword
        if (password.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Mot de passe requis", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val base64 = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw Exception("Impossible de lire le fichier")
                }
                val result = syncManager.importFile(base64, password.toCharArray())
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, statusMessage = "Import et fusion réussis", isError = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, statusMessage = "Erreur: ${e.message}", isError = true) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Erreur import: ${e.message}", isError = true) }
            }
        }
    }
}
