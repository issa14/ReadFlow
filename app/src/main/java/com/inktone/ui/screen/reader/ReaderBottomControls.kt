package com.inktone.ui.screen.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inktone.R
import com.inktone.ui.theme.AppIcons

@Composable
fun UnifiedControlPanel(
    isPlaying: Boolean,
    accentColor: Color,
    panelBg: Color,
    useOpenDyslexic: Boolean = false,
    readingMode: ReadingMode = ReadingMode.PAGED,
    onTtsClick: () -> Unit,
    onTtsSettingsClick: () -> Unit,
    onThemeCycle: () -> Unit,
    onFontToggle: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleMode: () -> Unit = {},
    onSearch: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onToc: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelBg.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                ))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── RANGÉE PRIMAIRE : Skip ← Play/Pause → Skip ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPrevChapter,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_tts_previous),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(24.dp))

                FilledIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTtsClick()
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.cd_tts_pause else R.string.cd_tts_play
                        ),
                        modifier = Modifier.size(28.dp),
                        tint = panelBg
                    )
                }

                Spacer(Modifier.width(24.dp))

                IconButton(
                    onClick = onNextChapter,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = stringResource(R.string.cd_tts_next),
                        tint = accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = accentColor.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )

            Spacer(Modifier.height(8.dp))

            // ── RANGÉE SECONDAIRE : actions avec labels ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryAction(
                    icon = Icons.Outlined.Headphones,
                    label = "Voix",
                    tint = accentColor.copy(alpha = 0.7f),
                    onClick = onTtsSettingsClick
                )
                SecondaryAction(
                    icon = Icons.Outlined.FormatSize,
                    label = "Police",
                    tint = if (useOpenDyslexic) accentColor
                           else accentColor.copy(alpha = 0.5f),
                    onClick = onFontToggle
                )
                SecondaryAction(
                    icon = Icons.Outlined.Palette,
                    label = "Thème",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onThemeCycle
                )
                SecondaryAction(
                    icon = Icons.Outlined.Timer,
                    label = "Veille",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onSleepTimerClick
                )
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = accentColor.copy(alpha = 0.08f),
                thickness = 0.5.dp
            )

            Spacer(Modifier.height(8.dp))

            // ── RANGÉE TERTIAIRE : navigation, ex-icônes de la top bar ──
            // Déplacées ici plutôt que laissées en icônes séparées dans ReaderTopBar, qui
            // tronquaient le titre du livre sur écran étroit — voir
            // PLAN_ACTION_TOP_TIER_CLAUDECODE.md §3.4. Top bar et ce panneau sont toujours
            // visibles ensemble (mêmes conditions dans ReaderScreen), donc pas besoin d'un
            // point d'entrée de menu séparé dans la top bar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryAction(
                    icon = if (readingMode == ReadingMode.PAGED) AppIcons.ReadingModePaged else AppIcons.ReadingModeScroll,
                    label = if (readingMode == ReadingMode.PAGED) "Défilement" else "Pages",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onToggleMode
                )
                SecondaryAction(
                    icon = AppIcons.Search,
                    label = "Recherche",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onSearch
                )
                SecondaryAction(
                    icon = AppIcons.Bookmark,
                    label = "Signets",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onBookmarks
                )
                SecondaryAction(
                    icon = AppIcons.Toc,
                    label = "Sommaire",
                    tint = accentColor.copy(alpha = 0.5f),
                    onClick = onToc
                )
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = tint,
            style = MaterialTheme.typography.labelSmall)
    }
}
