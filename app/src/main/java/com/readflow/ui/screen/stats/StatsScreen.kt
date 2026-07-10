package com.readflow.ui.screen.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val AccentBlue = Color(0xFF4FC3F7)
private val AccentViolet = Color(0xFFB388FF)
private val AccentGreen = Color(0xFF81C784)
private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            // ── 1. Jauge d'objectif quotidien ──
            DailyGoalProgress(
                progress = state.todayProgressFraction,
                minutes = state.todayReadingMinutes,
                goal = state.dailyGoalMinutes
            )

            Spacer(Modifier.height(20.dp))

            // ── 2. Série (Streak) ──
            StreakCard(
                currentStreak = state.currentStreak,
                maxStreak = state.maxStreak
            )

            Spacer(Modifier.height(16.dp))

            // ── 3. Cartes stats rapides ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    title = "Vitesse",
                    value = "${state.averageWpm}",
                    unit = "WPM",
                    icon = Icons.Default.Speed,
                    color = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    title = "Temps total",
                    value = String.format("%.1f", state.totalHoursRead),
                    unit = "h",
                    icon = Icons.Default.Schedule,
                    color = AccentViolet,
                    modifier = Modifier.weight(1f)
                )
                QuickStatCard(
                    title = "Livres lus",
                    value = "${state.totalBooksRead}",
                    unit = "",
                    icon = Icons.Default.MenuBook,
                    color = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 4. Graphique WPM ──
            WpmChart(history = state.recentWpmHistory)
        }
}

// ─────────────────────────────────────────────────────
//  JAUGE CIRCULAIRE — Objectif quotidien
// ─────────────────────────────────────────────────────

@Composable
private fun DailyGoalProgress(
    progress: Float,
    minutes: Float,
    goal: Int
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "goalProgress"
    )

    val gradientColors = listOf(AccentBlue, AccentViolet)

    Box(
        modifier = Modifier
            .size(180.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

            // Fond
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progression
            drawArc(
                color = AccentBlue,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Centre
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (progress >= 1f) AccentGreen else AccentBlue,
                modifier = Modifier.size(28.dp)
            )
            Text(
                "${minutes.toInt()} / ${goal} min",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "${(progress * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────
//  CARTE SÉRIE (STREAK)
// ─────────────────────────────────────────────────────

@Composable
private fun StreakCard(currentStreak: Int, maxStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (currentStreak > 0) AccentGreen.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (currentStreak > 0) AccentGreen else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (currentStreak > 0) "$currentStreak jour${if (currentStreak > 1) "s" else ""} consécutif${if (currentStreak > 1) "s" else ""} !"
                    else "Pas de série en cours",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Record: $maxStreak jour${if (maxStreak > 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  CARTE STAT RAPIDE
// ─────────────────────────────────────────────────────

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            if (unit.isNotEmpty()) {
                Text(unit, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            }
            Text(title, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────
//  GRAPHIQUE WPM
// ─────────────────────────────────────────────────────

@Composable
private fun WpmChart(history: List<Pair<String, Int>>) {
    if (history.isEmpty()) return

    val maxWpm = (history.maxOfOrNull { it.second } ?: 1).coerceAtLeast(100)
    val primaryColor = AccentBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Vitesse de lecture (WPM)",
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val paddingLeft = 40.dp.toPx()
                val paddingBottom = 32.dp.toPx()
                val paddingTop = 8.dp.toPx()
                val chartWidth = size.width - paddingLeft - 8.dp.toPx()
                val chartHeight = size.height - paddingBottom - paddingTop

                val stepX = if (history.size > 1) chartWidth / (history.size - 1) else 0f

                // Ligne de fond
                for (i in 0..4) {
                    val y = paddingTop + chartHeight * (1 - i / 4f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(paddingLeft, y),
                        end = Offset(size.width - 8.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Label WPM
                    val labelWpm = (maxWpm * i / 4)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$labelWpm",
                        4.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(100, 255, 255, 255)
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                    )
                }

                if (history.size == 1) {
                    val pt = history[0]
                    val x = paddingLeft + chartWidth / 2
                    val y = paddingTop + chartHeight * (1 - pt.second.toFloat() / maxWpm)
                    drawCircle(
                        color = primaryColor,
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // Label
                    drawContext.canvas.nativeCanvas.drawText(
                        pt.first,
                        x,
                        size.height - 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(150, 255, 255, 255)
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                } else {
                    history.forEachIndexed { index, (label, wpm) ->
                        val x = paddingLeft + stepX * index
                        val y = paddingTop + chartHeight * (1 - wpm.toFloat() / maxWpm)

                        // Point
                        drawCircle(
                            color = primaryColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )

                        // Ligne vers le point suivant
                        if (index < history.size - 1) {
                            val nextWpm = history[index + 1].second
                            val nextX = paddingLeft + stepX * (index + 1)
                            val nextY = paddingTop + chartHeight * (1 - nextWpm.toFloat() / maxWpm)
                            drawLine(
                                color = primaryColor.copy(alpha = 0.6f),
                                start = Offset(x, y),
                                end = Offset(nextX, nextY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Label du jour
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x,
                            size.height - 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(150, 255, 255, 255)
                                textSize = 10.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )

                        // Valeur WPM
                        if (wpm > 0) {
                            drawContext.canvas.nativeCanvas.drawText(
                                "$wpm",
                                x,
                                y - 10.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(200, 255, 255, 255)
                                    textSize = 9.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
