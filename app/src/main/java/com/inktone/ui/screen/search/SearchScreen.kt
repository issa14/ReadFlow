package com.inktone.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.data.database.entity.SentenceFts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    onNavigate: (chapterIndex: Int, sentenceIndex: Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()

    LaunchedEffect(bookId) { viewModel.init(bookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche — $bookTitle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    if (q.length >= 2) viewModel.search(q)
                },
                placeholder = { Text("Rechercher dans le livre...", color = Color.White.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFB74D),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (query.length < 2) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tapez au moins 2 caractères", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun résultat", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                }
            } else {
                Text("${results.size} résultat(s)", color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyColumn {
                    items(results, key = { "${it.chapterIndex}-${it.sentenceIndex}" }) { result ->
                        ResultItem(result) { onNavigate(result.chapterIndex, result.sentenceIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultItem(result: SentenceFts, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Chapitre ${result.chapterIndex + 1} · Phrase ${result.sentenceIndex + 1}",
                color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(result.text, color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
