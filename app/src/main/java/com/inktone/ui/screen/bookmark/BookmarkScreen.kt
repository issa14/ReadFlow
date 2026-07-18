package com.inktone.ui.screen.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.data.database.entity.BookmarkEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    onNavigate: (chapterIndex: Int, sentenceIndex: Int) -> Unit,
    viewModel: BookmarkViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val bookmarks by viewModel.bookmarks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signets — $bookTitle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0E15),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D0E15)
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aucun signet", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        onTap = { onNavigate(bookmark.chapterIndex, bookmark.sentenceIndex) },
                        onDelete = { viewModel.delete(bookmark) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkItem(
    bookmark: BookmarkEntity,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bookmark, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Chapitre ${bookmark.chapterIndex + 1} · Phrase ${bookmark.sentenceIndex + 1}",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(bookmark.text, color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Supprimer", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
