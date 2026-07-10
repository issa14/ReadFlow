package com.readflow.ui.screen.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.domain.model.Book
import com.readflow.ui.theme.*
import com.readflow.ui.screen.library.FilterMode
import com.readflow.ui.screen.library.SortOrder
import com.readflow.ui.screen.library.FilterType
import com.readflow.ui.screen.library.LayoutMode
import com.readflow.ui.screen.library.NavigationDestination
import com.readflow.ui.screen.opds.OpdsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onDebugClick: () -> Unit,
    onStatsClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onOpdsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDrawer by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showNavPopup by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val epubPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importBooks(uris)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wrapper qui enregistre l'ouverture avant de naviguer
    val handleBookClick: (String) -> Unit = { bookId ->
        state.allBooks.find { it.id == bookId }?.let { viewModel.recordBookOpen(it) }
        onBookClick(bookId)
    }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppBackground
        ) {
            Scaffold(
                topBar = {
                    if (showSearch) {
                        SearchBar(
                            query = searchText,
                            onQueryChange = { searchText = it; viewModel.setSearchQuery(it) },
                            onClose = { showSearch = false; searchText = ""; viewModel.setSearchQuery("") }
                        )
                    } else {
                        TopBar(
                            onMenu = { showDrawer = true },
                            onNavPopup = { showNavPopup = true },
                            onFilterMode = { viewModel.showFilterDialog() },
                            onSearch = { showSearch = true },
                            onOverflow = { showOverflow = true }
                        )
                    }
                },
                containerColor = AppBackground
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    state.error?.let { err ->
                        ErrorBanner(err, viewModel::clearError)
                    }

                    // Loading overlay pendant import
                    if (state.isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentBlue)
                                Spacer(Modifier.height(12.dp))
                                Text("Importation en cours...", color = TextMuted, fontSize = 14.sp)
                            }
                        }
                    } else {
                        when (state.currentDestination) {
                        NavigationDestination.LIBRARY -> {
                            when {
                                state.isLoading && state.books.isEmpty() -> LoadingView()
                                state.books.isEmpty() && !state.isLoading -> EmptyView()
                                else -> ShelfGrid(state.books, handleBookClick)
                            }
                        }
                        NavigationDestination.RECENTS -> {
                            val recent = state.recentBooks.mapNotNull { entity ->
                                state.allBooks.find { it.id == entity.bookId }
                            }
                            if (recent.isEmpty()) EmptyView() else ShelfGrid(recent, handleBookClick)
                        }
                        NavigationDestination.FILES -> {
                            FilesScreen(
                                onFileSelected = { file ->
                                    file.inputStream().use { stream ->
                                        viewModel.importFile(stream, file.name)
                                    }
                                    viewModel.navigateTo(NavigationDestination.LIBRARY)
                                },
                                onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) }
                            )
                        }
                        NavigationDestination.OPDS -> {
                            OpdsScreen(onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) })
                        }
                        NavigationDestination.BOOKMARKS -> {
                            com.readflow.ui.screen.bookmark.AllBookmarksPanel(
                                onNavigateToBook = { bookId -> onBookClick(bookId) },
                                onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) }
                            )
                        }
                        NavigationDestination.STATS -> {
                            LaunchedEffect(Unit) { onStatsClick() }
                        }
                        NavigationDestination.SYNC -> {
                            LaunchedEffect(Unit) { onSyncClick() }
                        }
                    }
                    } // end else (isLoading)
                }
            }
        }

        // ── POPUP NAVIGATION (titre TopBar) ──────────
        if (showNavPopup) {
            LibraryNavigationPopup(
                onDismiss = { showNavPopup = false },
                onFilterSelect = { viewModel.setFilterMode(it) },
                currentFilter = state.filterMode
            )
        }

        // ── FILTER MODE POPUP (icône entonnoir) ──────
        if (state.isFilterDialogVisible) {
            FilterAndSortDialog(
                sortOrder = state.sortOrder,
                filterType = state.filterType,
                layoutMode = state.layoutMode,
                onSortChange = { viewModel.setSortOrder(it) },
                onFilterChange = { viewModel.setFilterType(it) },
                onLayoutChange = { viewModel.setLayoutMode(it) },
                onDismiss = { viewModel.hideFilterDialog() }
            )
        }

        // ── OVERFLOW MENU ────────────────────────────
        if (showOverflow) {
            OverflowMenu(
                onDismiss = { showOverflow = false },
                onImport = { epubPicker.launch(arrayOf("application/epub+zip")) }
            )
        }

        // ── NAV DRAWER ───────────────────────────────
        if (showDrawer) {
            DrawerOverlay(onDismiss = { showDrawer = false })
        }
        NavDrawer(
            visible = showDrawer,
            currentDest = state.currentDestination,
            onDismiss = { showDrawer = false },
            onNavigate = { dest ->
                viewModel.navigateTo(dest)
                showDrawer = false
            },
            onThemeToggle = { viewModel.toggleTheme() },
            onDebug = onDebugClick
        )

        // ── FLOATING CONTROLS ────────────────────────
        FloatingControls(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp),
            onReadResume = { /* TODO: reprendre dernier livre */ }
        )
    }
}

// ─────────────────────────────────────────────────────
//  TOP BAR — Style prototype (bleue, dropdown trigger)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onMenu: () -> Unit,
    onNavPopup: () -> Unit,
    onFilterMode: () -> Unit,
    onSearch: () -> Unit,
    onOverflow: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onNavPopup)
            ) {
                Text("Tous les livres", fontWeight = FontWeight.Medium, fontSize = 17.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, "Rechercher", tint = Color.White)
            }
            IconButton(onClick = onFilterMode) {
                Icon(Icons.Default.FilterList, "Filtrer", tint = Color.White)
            }
            IconButton(onClick = onOverflow) {
                Icon(Icons.Default.MoreVert, "Plus", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AccentBlue
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Rechercher un livre...", color = Color.White.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Fermer", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AccentBlue)
    )
}

// ─────────────────────────────────────────────────────
//  SHELF GRID — 3 colonnes, couvertures gradient
// ─────────────────────────────────────────────────────

@Composable
private fun ShelfGrid(books: List<Book>, onBookClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(bottom = 80.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCover(
                book = book,
                gradientIndex = book.title.hashCode().mod(CoverGradients.size),
                onClick = { onBookClick(book.id) }
            )
        }
    }
}

@Composable
private fun BookCover(
    book: Book,
    gradientIndex: Int,
    onClick: () -> Unit
) {
    val gradient = CoverGradients[gradientIndex.coerceIn(0, CoverGradients.lastIndex)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Couverture avec gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(gradient))
        ) {
            // Titre sur la couverture
            Text(
                book.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.95f),
                lineHeight = 14.sp,
                modifier = Modifier.padding(8.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            // Badge progression (%)
            // TODO: utiliser la progression réelle
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "0%",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Dots de statut
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == 0) Color(0xFF00E676)
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // Titre + auteur sous la couverture
        Spacer(Modifier.height(4.dp))
        Text(
            book.title,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            book.author,
            fontSize = 10.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────
//  POPUP NAVIGATION — Double colonne (prototype Moon+)
// ─────────────────────────────────────────────────────

@Composable
private fun LibraryNavigationPopup(
    onDismiss: () -> Unit,
    onFilterSelect: (FilterMode) -> Unit,
    currentFilter: FilterMode
) {
    var selectedCategory by remember { mutableStateOf("Séries") }

    val categories = listOf(
        "Tous les livres" to FilterMode.ALL,
        "Favoris" to FilterMode.ALL,
        "Séries" to FilterMode.ALL,
        "Auteur" to FilterMode.BY_AUTHOR,
        "Tags" to FilterMode.ALL,
        "Dossiers" to FilterMode.ALL
    )

    // Sous-éléments mockés (à terme depuis le ViewModel)
    val subItems = remember(selectedCategory) {
        when (selectedCategory) {
            "Séries" -> listOf("Black Wings" to 1, "Contes et nouvelles" to 5, "Epub commercial" to 1)
            "Auteur" -> listOf("Tous" to 12)
            else -> emptyList()
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(8, 55),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── COLONNE GAUCHE : Catégories ────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.2f))
                ) {
                    categories.forEach { (label, mode) ->
                        val isActive = label == selectedCategory

                        Surface(
                            color = if (isActive) SurfaceDark else Color.Transparent,
                            onClick = {
                                selectedCategory = label
                                onFilterSelect(mode)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isActive) AccentBlue else TextMain,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isActive) {
                                    Icon(
                                        Icons.Default.ChevronRight, null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── COLONNE DROITE : Sous-éléments ─────
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (subItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Aucun élément",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                    } else {
                        subItems.forEach { (name, count) ->
                            Surface(
                                color = SurfaceRaised,
                                onClick = onDismiss
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 11.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        name,
                                        fontSize = 13.sp,
                                        color = TextMain,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextMuted,
                                        modifier = Modifier
                                            .background(
                                                ShelfOverlay,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  FILTER & SORT DIALOG — Material 3 AlertDialog
// ─────────────────────────────────────────────────────

@Composable
private fun FilterAndSortDialog(
    sortOrder: SortOrder,
    filterType: FilterType,
    layoutMode: LayoutMode,
    onSortChange: (SortOrder) -> Unit,
    onFilterChange: (FilterType) -> Unit,
    onLayoutChange: (LayoutMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = TextMain,
        textContentColor = TextMain,
        icon = {
            Icon(Icons.Default.Tune, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trier par", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Filtrage", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(end = 12.dp))
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // ── Colonne TRI (RadioButton) ─────
                    Column(modifier = Modifier.weight(1f)) {
                        SortOrder.entries.forEach { order ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSortChange(order) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sortOrder == order,
                                    onClick = { onSortChange(order) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentBlue,
                                        unselectedColor = TextMuted
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(order.label, fontSize = 13.sp,
                                    color = if (sortOrder == order) AccentBlue else TextMain)
                            }
                        }
                    }

                    // ── Colonne FILTRE (Checkbox) ──────
                    Column(modifier = Modifier.weight(1.2f)) {
                        FilterType.entries.forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFilterChange(type) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = filterType == type,
                                    onCheckedChange = { onFilterChange(type) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AccentBlue,
                                        uncheckedColor = TextMuted
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(type.label, fontSize = 13.sp,
                                    color = if (filterType == type) AccentBlue else TextMain)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = BorderDark
                )

                // ── MISE EN PAGE ──────────────────────
                Text("Mise en page", fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = TextMain)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        LayoutMode.LIST to Icons.Default.List,
                        LayoutMode.GRID to Icons.Default.GridView,
                        LayoutMode.GRID_COVERS to Icons.Default.ViewModule
                    ).forEach { (mode, icon) ->
                        FilterChip(
                            selected = layoutMode == mode,
                            onClick = { onLayoutChange(mode) },
                            label = { Text(
                                when (mode) {
                                    LayoutMode.LIST -> "Liste"
                                    LayoutMode.GRID -> "Grille"
                                    LayoutMode.GRID_COVERS -> "Couv."
                                },
                                fontSize = 12.sp
                            ) },
                            leadingIcon = {
                                Icon(icon, null, modifier = Modifier.size(16.dp),
                                    tint = if (layoutMode == mode) AccentBlue else TextMuted)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                                selectedLabelColor = AccentBlue
                            )
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = BorderDark
                )

                // ── BAS : Type de fichier + Fermer ────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type de fichier : Tous", fontSize = 12.sp, color = TextMuted)
                    TextButton(onClick = onDismiss) {
                        Text("Fermer", color = AccentBlue)
                    }
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(16.dp)
    )
}
// ─────────────────────────────────────────────────────

@Composable
private fun OverflowMenu(onDismiss: () -> Unit, onImport: () -> Unit) {
    // Overlay pour fermer au tap extérieur
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 55.dp, end = 8.dp)
                .width(260.dp),
            color = Color.White,
            shape = RoundedCornerShape(4.dp),
            tonalElevation = 8.dp
        ) {
            Column {
                OverflowMenuItem("Importer des livres", Icons.Default.FileUpload) {
                    onDismiss(); onImport()
                }
                OverflowMenuItem("Couverture par défaut", Icons.Default.Image) { onDismiss() }
                OverflowMenuItem("Reconstruire les couvertures", Icons.Default.Refresh) { onDismiss() }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                OverflowMenuItem("Synchroniser avec le cloud", Icons.Default.CloudUpload,
                    color = AccentBlue) { onDismiss() }
            }
        }
    }
}

@Composable
private fun OverflowMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                              color: Color = Color(0xFF333333), onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF757575), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = color)
    }
}

// ─────────────────────────────────────────────────────
//  NAV DRAWER — Tiroir latéral gauche
// ─────────────────────────────────────────────────────

@Composable
private fun DrawerOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
    )
}

@Composable
private fun NavDrawer(
    visible: Boolean,
    currentDest: NavigationDestination,
    onDismiss: () -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    onThemeToggle: () -> Unit,
    onDebug: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(310.dp),
            color = Color.White
        ) {
            Column {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF3F51B5))))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text("ReadFlow", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }

                // Menu items dynamiques
                Column(modifier = Modifier.weight(1f)) {
                    NavigationDestination.entries.forEach { dest ->
                        val isActive = dest == currentDest
                        Surface(
                            color = if (isActive) Color(0xFFE8F0FE) else Color.Transparent,
                            onClick = { onNavigate(dest) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    dest.icon, null,
                                    tint = if (isActive) AccentBlue else Color(0xFF757575),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(24.dp))
                                Text(
                                    dest.label, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isActive) AccentBlue else Color(0xFF444444)
                                )
                            }
                        }
                    }
                }

                // Footer
                Surface(color = Color(0xFFF5F5F5)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DrawerFooterBtn("Options", Icons.Default.Settings) { onDismiss() }
                        DrawerFooterBtn("À propos", Icons.Default.Info) { onDismiss() }
                        DrawerFooterBtn("Thème", Icons.Default.DarkMode) { onThemeToggle() }
                        DrawerFooterBtn("Debug", Icons.Default.Build) { onDismiss(); onDebug() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerFooterBtn(text: String,
                             icon: androidx.compose.ui.graphics.vector.ImageVector,
                             onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Color(0xFF616161), modifier = Modifier.size(16.dp))
        Text(text, fontSize = 9.sp, color = Color(0xFF616161))
    }
}

// ─────────────────────────────────────────────────────
//  FLOATING CONTROLS — Pill TTS + FAB
// ─────────────────────────────────────────────────────

@Composable
private fun FloatingControls(
    modifier: Modifier = Modifier,
    onReadResume: () -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Pill audio discret
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceRaised,
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            tonalElevation = 4.dp
        ) {
            IconButton(onClick = { /* TODO: quick TTS */ }) {
                Icon(Icons.Outlined.Headphones, "Audio",
                    tint = AccentTts, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(10.dp))

        // FAB
        FloatingActionButton(
            onClick = onReadResume,
            containerColor = AccentBlue,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.MenuBook, "Lire",
                tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : vide, chargement, erreur
// ─────────────────────────────────────────────────────

@Composable
private fun EmptyView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(64.dp),
                tint = TextMuted.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text("Bibliothèque vide", color = TextMuted, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue)
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(color = Color(0x33FF6B6B)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❌ $error", color = Color(0xFFFF6B6B), modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun ComingSoonPlaceholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Construction, null, Modifier.size(48.dp), tint = TextMuted.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text(label, color = TextMuted, fontSize = 16.sp)
            Text("Bientôt disponible", color = TextMuted.copy(alpha = 0.5f), fontSize = 13.sp)
        }
    }
}

