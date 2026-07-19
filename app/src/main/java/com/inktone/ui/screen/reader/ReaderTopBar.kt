package com.inktone.ui.screen.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.*
import com.inktone.ui.theme.ttsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String,
    readingMode: ReadingMode = ReadingMode.PAGED,
    onToggleMode: () -> Unit = {},
    onBack: () -> Unit,
    onToc: () -> Unit,
    onBookmarks: () -> Unit = {},
    onSearch: () -> Unit = {}
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
            IconButton(onClick = onToggleMode) {
                Icon(
                    if (readingMode == ReadingMode.PAGED) Icons.Default.ViewDay else Icons.Default.ImportContacts,
                    contentDescription = if (readingMode == ReadingMode.PAGED) "Mode défilement" else "Mode paginé",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Rechercher", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            @Suppress("DEPRECATION")
            IconButton(onClick = onBookmarks) { Icon(Icons.Default.Bookmark, "Signets", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            @Suppress("DEPRECATION")
            IconButton(onClick = onToc) { Icon(Icons.Default.List, "TOC", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun ChapterPicker(
    tocEntries: List<com.inktone.domain.model.TocEntry>,
    currentChapter: Int,
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (entry.level * 16).dp, top = 2.dp, bottom = 2.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.ttsActive.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                onClick = { onSelect(entry.index) }
            ) {
                Text(
                    entry.title,
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
