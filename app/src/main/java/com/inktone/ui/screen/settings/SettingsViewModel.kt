package com.inktone.ui.screen.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.backup.BackupManager
import com.inktone.data.settings.AppTheme
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.repository.TtsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val voice: String = "Miro",
    val speed: Float = 1.0f,
    val gain: Float = 3.0f,
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColors: Boolean = false,
    val modelPath: String = "",
    val availableVoices: List<String> = listOf("Miro", "Gilles"),
    // Moteur TTS
    val selectedEngine: String = "piper",
    val selectedEdgeVoice: String = "fr-FR-VivienneNeural",
    val availableEngines: List<EngineInfo> = listOf(
        EngineInfo("piper", "Piper ONNX (local)", true),
        EngineInfo("edge", "Microsoft Edge (cloud)", false)
    ),
    val availableEdgeVoices: List<String> = listOf("fr-FR-VivienneNeural", "fr-FR-HenriNeural"),
    // Backup
    val backupMessage: String? = null
)

data class EngineInfo(
    val id: String,
    val label: String,
    val isAvailable: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val ttsRepository: TtsRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsVM"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { repository.voice.collect { _uiState.update { s -> s.copy(voice = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.speed.collect { _uiState.update { s -> s.copy(speed = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.gain.collect { _uiState.update { s -> s.copy(gain = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.theme.collect { _uiState.update { s -> s.copy(theme = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.dynamicColors.collect { _uiState.update { s -> s.copy(dynamicColors = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.modelPath.collect { _uiState.update { s -> s.copy(modelPath = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.ttsEngine.collect { _uiState.update { s -> s.copy(selectedEngine = it) } } }
        viewModelScope.launch(Dispatchers.IO) { repository.edgeVoice.collect { _uiState.update { s -> s.copy(selectedEdgeVoice = it) } } }

        // Charger les moteurs disponibles
        viewModelScope.launch(Dispatchers.IO) {
            val engines = ttsRepository.getAvailableEngines().map { provider ->
                EngineInfo(
                    id = provider.engineId,
                    label = provider.engineLabel,
                    isAvailable = provider.isAvailable
                )
            }
            _uiState.update { it.copy(availableEngines = engines) }
        }
    }

    fun setVoice(voice: String) { viewModelScope.launch { repository.setVoice(voice) } }
    fun setSpeed(speed: Float) { viewModelScope.launch { repository.setSpeed(speed) } }
    fun setGain(gain: Float) { viewModelScope.launch { repository.setGain(gain) } }
    fun setTheme(theme: AppTheme) { viewModelScope.launch { repository.setTheme(theme) } }
    fun setDynamicColors(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColors(enabled) } }
    fun setModelPath(path: String) { viewModelScope.launch { repository.setModelPath(path) } }

    fun setEngine(engineId: String) {
        viewModelScope.launch {
            repository.setTtsEngine(engineId)
        }
    }

    fun setEdgeVoice(voiceId: String) {
        viewModelScope.launch {
            repository.setEdgeVoice(voiceId)
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                backupManager.exportTo(uri)
                _uiState.update { it.copy(backupMessage = "✅ Sauvegarde exportée avec succès") }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update { it.copy(backupMessage = "❌ Échec : ${e.message}") }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = backupManager.importFrom(uri)
                _uiState.update { it.copy(backupMessage = "✅ $count éléments restaurés") }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _uiState.update { it.copy(backupMessage = "❌ Échec : ${e.message}") }
            }
        }
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }
}
