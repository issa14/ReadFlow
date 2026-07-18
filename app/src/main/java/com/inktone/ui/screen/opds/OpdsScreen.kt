package com.inktone.ui.screen.opds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inktone.domain.model.OpdsEntry
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.model.OpdsLink

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val AccentBlue = Color(0xFF4FC3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    onBack: () -> Unit,
    viewModel: OpdsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Barre d'outils OPDS interne ──
            Surface(color = DarkBg, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.navigationStack.isNotEmpty()) {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                        }
                    }
                    Text(
                        state.feed?.title ?: "Catalogues OPDS",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.toggleAddCatalog() }) {
                        Icon(Icons.Default.Add, "Ajouter", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                state.error != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error!!, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCatalog() }) {
                            Text("Réessayer")
                        }
                    }
                }
                state.feed != null -> {
                    OpdsFeedContent(
                        feed = state.feed!!,
                        canGoBack = state.navigationStack.isNotEmpty(),
                        onEntryClick = { entry ->
                            // Naviguer vers le sous-catalogue ou télécharger
                            val navLink = entry.links.firstOrNull {
                                it.type?.contains("opds") == true ||
                                it.rel.contains("subsection")
                            }
                            if (navLink != null) {
                                viewModel.navigateToPage(navLink.href)
                            } else if (entry.epubDownloadUrl != null) {
                                viewModel.downloadEntry(entry)
                            }
                        },
                        onNavLinkClick = { viewModel.navigateToPage(it.href) },
                        onNextPage = { state.feed?.nextPageUrl?.let { viewModel.navigateToPage(it) } },
                        onSearch = { query -> viewModel.searchOpds(query) }
                    )
                }
            }
        } // Box.weight(1f)
    } // Column
    } // Box externe

    // ── Dialogue ajout catalogue ──
    if (state.showAddCatalog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleAddCatalog() },
            containerColor = Color(0xFF252525),
            title = { Text("Ajouter un catalogue", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = { viewModel.updateUrl(it) },
                        label = { Text("URL du catalogue OPDS") },
                        singleLine = true,
                        colors = darkFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.usernameInput,
                        onValueChange = { viewModel.updateUsername(it) },
                        label = { Text("Identifiant (optionnel)") },
                        singleLine = true,
                        colors = darkFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.passwordInput,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Mot de passe (optionnel)") },
                        singleLine = true,
                        colors = darkFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.connectToCatalog() }) {
                    Text("Se connecter", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleAddCatalog() }) {
                    Text("Annuler", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}

@Composable
private fun OpdsFeedContent(
    feed: OpdsFeed,
    canGoBack: Boolean,
    onEntryClick: (OpdsEntry) -> Unit,
    onNavLinkClick: (OpdsLink) -> Unit,
    onNextPage: () -> Unit,
    onSearch: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Description du catalogue
        if (!feed.description.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        feed.description,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Liens de navigation (sous-catalogues)
        if (feed.navigationLinks.isNotEmpty()) {
            item {
                Text("Catégories", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            feed.navigationLinks.forEach { link ->
                item {
                    NavigationLinkCard(link, onClick = { onNavLinkClick(link) })
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // Entrées (livres)
        if (feed.entries.isNotEmpty()) {
            item {
                Text("Livres (${feed.entries.size})", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(feed.entries, key = { it.id.ifBlank { it.title } }) { entry ->
                OpdsEntryCard(entry, onClick = { onEntryClick(entry) })
            }
        }

        // Pagination
        if (feed.nextPageUrl != null) {
            item {
                OutlinedButton(
                    onClick = onNextPage,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("Charger plus...")
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun NavigationLinkCard(link: OpdsLink, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                link.title ?: link.href,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OpdsEntryCard(entry: OpdsEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Couverture
            if (!entry.thumbnailUrl.isNullOrBlank() || !entry.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.thumbnailUrl ?: entry.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, color = Color.White, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (entry.author.isNotBlank()) {
                    Text(entry.author, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                if (!entry.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(entry.summary, color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                // Badge téléchargeable
                if (entry.epubDownloadUrl != null) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = onClick,
                        label = { Text("EPUB disponible", fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF4FC3F7).copy(alpha = 0.15f),
                            labelColor = AccentBlue
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.7f),
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
    cursorColor = AccentBlue,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
)
