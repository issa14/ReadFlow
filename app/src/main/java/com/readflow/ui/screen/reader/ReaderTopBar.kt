package com.readflow.ui.screen.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.*
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
        color = Color(0xFF0A0A0A).copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White.copy(alpha = 0.85f))
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onToggleMode) {
                Icon(
                    if (readingMode == ReadingMode.PAGED) Icons.Default.ViewDay else Icons.Default.ImportContacts,
                    contentDescription = if (readingMode == ReadingMode.PAGED) "Mode défilement" else "Mode paginé",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Rechercher", tint = Color.White.copy(alpha = 0.6f)) }
            @Suppress("DEPRECATION")
            IconButton(onClick = onBookmarks) { Icon(Icons.Default.Bookmark, "Signets", tint = Color.White.copy(alpha = 0.6f)) }
            @Suppress("DEPRECATION")
            IconButton(onClick = onToc) { Icon(Icons.Default.List, "TOC", tint = Color.White.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
fun ChapterPicker(
    totalChapters: Int,
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Table des matières", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 12.dp))
        for (i in 0 until totalChapters) {
            val isCurrent = i == currentChapter
            Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = if (isCurrent) Color(0xFFFFB74D).copy(alpha = 0.15f) else Color.Transparent, shape = RoundedCornerShape(8.dp), onClick = { onSelect(i) }) {
                Text("Chapitre ${i + 1}", color = if (isCurrent) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
