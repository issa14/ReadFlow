package com.readflow.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.data.database.ReadingSessionDao
import com.readflow.domain.usecase.StreakCalculator
import com.readflow.domain.usecase.StreakResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class StatsUiState(
    val todayReadingMinutes: Float = 0f,
    val todayProgressFraction: Float = 0f,
    val dailyGoalMinutes: Int = 20,
    val averageWpm: Int = 150,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val recentWpmHistory: List<Pair<String, Int>> = emptyList(),
    val totalHoursRead: Float = 0f,
    val totalBooksRead: Int = 0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionDao: ReadingSessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observer les sessions de lecture
            sessionDao.getAllSessions().collect { sessions ->
                if (sessions.isEmpty()) {
                    _uiState.value = StatsUiState()
                    return@collect
                }
                computeStats(sessions)
            }
        }

        viewModelScope.launch {
            sessionDao.getReadingDates().collect { dates ->
                val streak = StreakCalculator.calculate(dates)
                _uiState.update {
                    it.copy(
                        currentStreak = streak.currentStreak,
                        maxStreak = streak.maxStreak
                    )
                }
            }
        }
    }

    private suspend fun computeStats(sessions: List<com.readflow.data.database.entity.ReadingSessionEntity>) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todaySeconds = sessionDao.getReadingSecondsForDate(today) ?: 0L
        val todayMinutes = todaySeconds / 60f
        val progressFraction = (todayMinutes / _uiState.value.dailyGoalMinutes).coerceIn(0f, 1f)

        // WPM global
        val totalWords = sessions.sumOf { it.wordsRead.toLong() }
        val totalSeconds = sessions.sumOf { it.durationSeconds }
        val wpm = if (totalSeconds > 60) {
            ((totalWords.toDouble() / totalSeconds.toDouble()) * 60.0).toInt()
        } else 150

        // Historique WPM sur les 7 derniers jours
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val recentWpm = mutableListOf<Pair<String, Int>>()
        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)
            val daySessions = sessions.filter { it.date == dateStr }
            val dayWords = daySessions.sumOf { it.wordsRead.toLong() }
            val daySeconds = daySessions.sumOf { it.durationSeconds }
            val dayWpm = if (daySeconds > 60) {
                ((dayWords.toDouble() / daySeconds.toDouble()) * 60.0).toInt()
            } else 0
            val dayLabel = SimpleDateFormat("EEE", Locale.FRENCH).format(calendar.time)
            recentWpm.add(dayLabel to dayWpm)
        }

        // Total heures lues
        val totalHours = totalSeconds / 3600f

        // Nombre de livres distincts lus
        val distinctBooks = sessions.map { it.bookId }.distinct().size

        _uiState.update {
            it.copy(
                todayReadingMinutes = todayMinutes,
                todayProgressFraction = progressFraction,
                averageWpm = wpm,
                recentWpmHistory = recentWpm,
                totalHoursRead = totalHours,
                totalBooksRead = distinctBooks
            )
        }
    }
}
