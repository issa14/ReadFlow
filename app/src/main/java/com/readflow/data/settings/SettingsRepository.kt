package com.readflow.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "readflow_settings")

enum class AppTheme { LIGHT, DARK, SYSTEM }

/**
 * Repository global des préférences utilisateur via Jetpack DataStore.
 *
 * Toutes les lectures sont exposées en [Flow] pour une réactivité UI.
 * Toutes les écritures sont suspendues et thread-safe.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    // ── Clés ────────────────────────────────────────

    private object Keys {
        val VOICE = stringPreferencesKey("tts_voice")
        val SPEED = floatPreferencesKey("tts_speed")
        val GAIN = floatPreferencesKey("tts_gain")
        val THEME = stringPreferencesKey("app_theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val MODEL_PATH = stringPreferencesKey("model_path")

        // Clés de lecture (ReaderScreen) — anciennement SharedPreferences
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val HORIZONTAL_MARGIN = intPreferencesKey("horizontal_margin")
    }

    // ── Lectures (Flow) ──────────────────────────────

    val voice: Flow<String> = dataStore.data.map { it[Keys.VOICE] ?: "Miro" }
    val speed: Flow<Float> = dataStore.data.map { it[Keys.SPEED] ?: 1.0f }
    val gain: Flow<Float> = dataStore.data.map { it[Keys.GAIN] ?: 3.0f }
    val theme: Flow<AppTheme> = dataStore.data.map {
        when (it[Keys.THEME]) {
            "LIGHT" -> AppTheme.LIGHT
            "DARK" -> AppTheme.DARK
            else -> AppTheme.SYSTEM
        }
    }
    val dynamicColors: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_COLORS] ?: false }
    val modelPath: Flow<String> = dataStore.data.map { it[Keys.MODEL_PATH] ?: "" }

    // ── Écritures (suspend) ──────────────────────────

    suspend fun setVoice(voice: String) {
        dataStore.edit { it[Keys.VOICE] = voice }
    }

    suspend fun setSpeed(speed: Float) {
        dataStore.edit { it[Keys.SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setGain(gain: Float) {
        dataStore.edit { it[Keys.GAIN] = gain.coerceIn(1.0f, 4.0f) }
    }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
    }

    suspend fun setModelPath(path: String) {
        dataStore.edit { it[Keys.MODEL_PATH] = path }
    }

    // ── Réglages de lecture (ReaderScreen) ───────────

    val readerTheme: Flow<String> = dataStore.data.map { it[Keys.READER_THEME] ?: "NIGHT" }
    val readerFont: Flow<String> = dataStore.data.map { it[Keys.READER_FONT] ?: "SERIF" }
    val fontSize: Flow<Float> = dataStore.data.map { it[Keys.FONT_SIZE] ?: 18f }
    val lineHeight: Flow<Float> = dataStore.data.map { it[Keys.LINE_HEIGHT] ?: 1.8f }
    val horizontalMargin: Flow<Int> = dataStore.data.map { it[Keys.HORIZONTAL_MARGIN] ?: 24 }

    suspend fun setReaderTheme(theme: String) {
        dataStore.edit { it[Keys.READER_THEME] = theme }
    }

    suspend fun setReaderFont(font: String) {
        dataStore.edit { it[Keys.READER_FONT] = font }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { it[Keys.FONT_SIZE] = size.coerceIn(12f, 32f) }
    }

    suspend fun setLineHeight(height: Float) {
        dataStore.edit { it[Keys.LINE_HEIGHT] = height.coerceIn(1.2f, 2.4f) }
    }

    suspend fun setHorizontalMargin(margin: Int) {
        dataStore.edit { it[Keys.HORIZONTAL_MARGIN] = margin.coerceIn(8, 48) }
    }
}
