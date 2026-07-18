package com.inktone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "inktone_settings")

enum class AppTheme(val label: String) {
    PAPIER_ART("Papier d'Art"),
    OBSIDIAN("Obsidian Noir"),
    NORDIC_FOG("Brouillard Nordique"),
    SIGNATURE("Signature"),
    SYSTEM("Système");

    companion object {
        fun fromName(name: String?): AppTheme =
            entries.find { it.name == name } ?: PAPIER_ART
    }
}

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

        // Moteur TTS
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val EDGE_VOICE = stringPreferencesKey("edge_voice")

        // Clés de lecture (ReaderScreen) — anciennement SharedPreferences
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val HORIZONTAL_MARGIN = intPreferencesKey("horizontal_margin")

        // Onboarding & accessibilité
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val RESPECT_SYSTEM_FONT_SCALE = booleanPreferencesKey("respect_system_font_scale")
        val HAS_IMPORTED_FIRST_BOOK = booleanPreferencesKey("has_imported_first_book")
        val HAS_SEEN_READER_TOOLTIP = booleanPreferencesKey("has_seen_reader_tooltip")
        val HAS_SEEN_PLAY_TOOLTIP = booleanPreferencesKey("has_seen_play_tooltip")
    }

    // ── Lectures (Flow) ──────────────────────────────

    val voice: Flow<String> = dataStore.data.map { it[Keys.VOICE] ?: "Miro" }
    val speed: Flow<Float> = dataStore.data.map { it[Keys.SPEED] ?: 1.0f }
    val gain: Flow<Float> = dataStore.data.map { it[Keys.GAIN] ?: 3.0f }
    val theme: Flow<AppTheme> = dataStore.data.map {
        AppTheme.fromName(it[Keys.THEME])
    }
    val dynamicColors: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_COLORS] ?: false }
    val modelPath: Flow<String> = dataStore.data.map { it[Keys.MODEL_PATH] ?: "" }

    // ── Moteur TTS ──────────────────────────────────

    val ttsEngine: Flow<String> = dataStore.data.map { it[Keys.TTS_ENGINE] ?: "piper" }
    val edgeVoice: Flow<String> = dataStore.data.map { it[Keys.EDGE_VOICE] ?: "fr-FR-VivienneNeural" }

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

    suspend fun setTtsEngine(engine: String) {
        dataStore.edit { it[Keys.TTS_ENGINE] = engine }
    }

    suspend fun setEdgeVoice(voice: String) {
        dataStore.edit { it[Keys.EDGE_VOICE] = voice }
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

    // ── Onboarding ──────────────────────────────────

    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { it[Keys.IS_FIRST_LAUNCH] ?: true }

    suspend fun setOnboardingComplete() {
        dataStore.edit { it[Keys.IS_FIRST_LAUNCH] = false }
    }

    // ── Accessibilité ───────────────────────────────

    val reduceMotion: Flow<Boolean> = dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    val respectSystemFontScale: Flow<Boolean> = dataStore.data.map { it[Keys.RESPECT_SYSTEM_FONT_SCALE] ?: true }

    suspend fun setRespectSystemFontScale(enabled: Boolean) {
        dataStore.edit { it[Keys.RESPECT_SYSTEM_FONT_SCALE] = enabled }
    }

    // ── Tooltips premier livre ──────────────────────

    val hasImportedFirstBook: Flow<Boolean> = dataStore.data.map { it[Keys.HAS_IMPORTED_FIRST_BOOK] ?: false }

    suspend fun markFirstBookImported() {
        dataStore.edit { it[Keys.HAS_IMPORTED_FIRST_BOOK] = true }
    }

    val hasSeenReaderTooltip: Flow<Boolean> = dataStore.data.map { it[Keys.HAS_SEEN_READER_TOOLTIP] ?: false }

    suspend fun markReaderTooltipSeen() {
        dataStore.edit { it[Keys.HAS_SEEN_READER_TOOLTIP] = true }
    }

    val hasSeenPlayTooltip: Flow<Boolean> = dataStore.data.map { it[Keys.HAS_SEEN_PLAY_TOOLTIP] ?: false }

    suspend fun markPlayTooltipSeen() {
        dataStore.edit { it[Keys.HAS_SEEN_PLAY_TOOLTIP] = true }
    }
}
