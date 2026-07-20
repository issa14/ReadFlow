package com.inktone.ui.screen.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.inktone.ui.theme.ttsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Barre du haut du Reader — retour + titre/sous-titre uniquement. Les actions secondaires
 * (mode de lecture, recherche, signets, table des matières) vivent désormais dans
 * [UnifiedControlPanel] (visible en même temps que cette barre, jamais l'une sans l'autre —
 * pas de point d'entrée séparé nécessaire) au lieu d'icônes séparées ici, qui tronquaient le
 * titre sur écran étroit — voir PLAN_ACTION_TOP_TIER_CLAUDECODE.md §3.4.
 */
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.outlineVariant, style = MaterialTheme.typography.labelSmall)
            }
            // Espace fantôme de la même largeur que le bouton retour, pour garder le titre
            // vraiment centré sur l'écran plutôt que centré sur l'espace restant à sa droite.
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
fun ChapterPicker(
    tocEntries: List<com.inktone.domain.model.TocEntry>,
    currentChapter: Int,
    chapterTitles: List<String> = emptyList(),
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Table des matières", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
        tocEntries.forEach { entry ->
            val isCurrent = entry.index == currentChapter
            val title = entry.title.takeIf { it.isNotBlank() }
                ?: chapterTitles.getOrNull(entry.index)
                ?: "Chapitre ${entry.index + 1}"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (entry.level * 16).dp, top = 2.dp, bottom = 2.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                onClick = { onSelect(entry.index) }
            ) {
                Text(
                    title,
                    color = if (isCurrent) MaterialTheme.colorScheme.ttsActive else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
