package com.inktone.domain.usecase

import java.text.SimpleDateFormat
import java.util.*

data class StreakResult(
    val currentStreak: Int,
    val maxStreak: Int
)

object StreakCalculator {

    /**
     * Calcule la série de jours consécutifs de lecture (streak)
     * à partir d'une liste de dates au format "yyyy-MM-dd".
     *
     * @return [StreakResult] avec currentStreak et maxStreak.
     */
    fun calculate(dates: List<String>): StreakResult {
        if (dates.isEmpty()) return StreakResult(0, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val calendar = Calendar.getInstance()

        // Convertir les dates en objets Calendar triés par ordre décroissant
        val sortedDates = dates
            .mapNotNull { dateStr ->
                try {
                    val parsed = dateFormat.parse(dateStr)
                    if (parsed != null) {
                        Calendar.getInstance().apply { time = parsed }
                    } else null
                } catch (_: Exception) { null }
            }
            .distinctBy { dateFormat.format(it.time) }
            .sortedByDescending { it.timeInMillis }

        if (sortedDates.isEmpty()) return StreakResult(0, 0)

        // Calcul du currentStreak
        val todayCal = Calendar.getInstance().apply { time = dateFormat.parse(today)!! }
        val yesterdayCal = Calendar.getInstance().apply {
            time = todayCal.time
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val mostRecentDate = sortedDates.first()
        val mostRecentStr = dateFormat.format(mostRecentDate.time)
        val todayStr = dateFormat.format(todayCal.time)
        val yesterdayStr = dateFormat.format(yesterdayCal.time)

        // La série est active seulement si la dernière lecture est aujourd'hui ou hier
        val isStreakActive = mostRecentStr == todayStr || mostRecentStr == yesterdayStr

        val currentStreak = if (isStreakActive) {
            var streak = 1
            val startFrom = sortedDates.first()
            var expected = Calendar.getInstance().apply { time = startFrom.time }

            for (i in 1 until sortedDates.size) {
                expected.add(Calendar.DAY_OF_YEAR, -1)
                val current = sortedDates[i]
                if (dateFormat.format(current.time) == dateFormat.format(expected.time)) {
                    streak++
                } else {
                    break
                }
            }
            streak
        } else {
            0
        }

        // Calcul du maxStreak
        var maxStreak = 0
        var i = 0
        while (i < sortedDates.size) {
            var run = 1
            var expected = Calendar.getInstance().apply { time = sortedDates[i].time }
            var j = i + 1
            while (j < sortedDates.size) {
                expected.add(Calendar.DAY_OF_YEAR, -1)
                if (dateFormat.format(sortedDates[j].time) == dateFormat.format(expected.time)) {
                    run++
                    j++
                } else {
                    break
                }
            }
            if (run > maxStreak) maxStreak = run
            i = j
        }

        return StreakResult(
            currentStreak = currentStreak,
            maxStreak = maxOf(maxStreak, currentStreak)
        )
    }
}
