package com.readflow.ui.screen.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.readflow.data.database.entity.BookmarkEntity

/**
 * Panneau "Marque-pages & Notes" affiché dans le drawer de la bibliothèque.
 *
 * Affiche TOUS les marque-pages (tous livres confondus) avec recherche
 * et navigation vers le livre/chapitre correspondant.
 */
@Composable
fun AllBookmarksPanel(
    onNavigateToBook: (bookId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: BookmarkViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        viewModel.loadAll(searchQuery)
    }

    val bookmarks by viewModel.bookmarks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher...", color = Color.White.copy(alpha = 0.3f)) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.7f),
                cursorColor = Color(0xFFFFB74D),
                focusedBorderColor = Color(0xFFFFB74D),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (bookmarks.isEmpty()) {
            Text(
                if (searchQuery.isNotBlank()) "Aucun résultat pour « $searchQuery »"
                else "Aucun marque-page. Ajoutez-en depuis le lecteur (icône 🔖).",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkDrawerItem(
                        bookmark = bookmark,
                        onTap = { onNavigateToBook(bookmark.bookId) },
                        onDelete = { viewModel.delete(bookmark) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BookmarkDrawerItem(
    bookmark: BookmarkEntity,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onTap() },
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Bookmark, null,
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.text,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ch. ${bookmark.chapterIndex + 1} · Phrase ${bookmark.sentenceIndex + 1}",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Supprimer",
                    tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
