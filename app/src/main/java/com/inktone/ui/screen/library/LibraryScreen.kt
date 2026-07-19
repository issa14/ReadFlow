package com.inktone.ui.screen.library

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inktone.R
import com.inktone.domain.model.Book
import com.inktone.ui.theme.*
import com.inktone.ui.screen.library.FilterMode
import com.inktone.ui.screen.library.SortOrder
import com.inktone.ui.screen.library.FilterType
import com.inktone.ui.screen.library.LayoutMode
import com.inktone.ui.screen.library.NavigationDestination
import com.inktone.ui.screen.opds.OpdsScreen
import com.inktone.ui.theme.ttsActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onDebugClick: () -> Unit,
    onOpdsClick: () -> Unit = {},
    onNavigateToBookmark: (bookId: String, chapterIndex: Int, sentenceIndex: Int) -> Unit =
        { bookId, _, _ -> onBookClick(bookId) },
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showOverflow by remember { mutableStateOf(false) }
    var showNavPopup by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var fontSizeScale by remember { mutableStateOf(1) } // 0=small, 1=medium, 2=large

    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar de succès après premier import
    LaunchedEffect(state.importSuccessSnackbar) {
        state.importSuccessSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearImportSuccessSnackbar()
        }
    }

    val epubPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importBooks(uris)
    }

    InkToneTheme(theme = state.appTheme, dynamicColors = false) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
                NavDrawerContent(
                    currentDest = state.currentDestination,
                    onNavigate = { dest ->
                        viewModel.navigateTo(dest)
                        drawerScope.launch { drawerState.close() }
                    },
                    onThemeToggle = { viewModel.toggleTheme() },
                    onDebug = { drawerScope.launch { drawerState.close() }; onDebugClick() }
                )
            }
        }
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (showSearch) {
                        SearchBar(
                            query = searchText,
                            onQueryChange = { searchText = it; viewModel.setSearchQuery(it) },
                            onClose = { showSearch = false; searchText = ""; viewModel.setSearchQuery("") }
                        )
                    } else {
                        TopBar(
                            currentDest = state.currentDestination,
                            onMenu = { drawerScope.launch { drawerState.open() } },
                            onNavPopup = { showNavPopup = true },
                            onFilterMode = { viewModel.showFilterDialog() },
                            onSearch = { showSearch = true },
                            onOverflow = { showOverflow = true }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    state.error?.let { err ->
                        ErrorBanner(err, viewModel::clearError)
                    }

                    // Loading overlay pendant import
                    if (state.isLoading) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val progress = state.importProgress
                                val status = state.importStatus ?: "Importation en cours..."

                                if (progress != null) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = status,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                } else {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = status,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        when (state.currentDestination) {
                        NavigationDestination.LIBRARY -> {
                            when {
                                state.isLoading && state.books.isEmpty() -> LoadingView()
                                state.books.isEmpty() && !state.isLoading -> EmptyView(
                                    onImportClick = { epubPicker.launch(arrayOf("application/epub+zip")) },
                                    onBrowseClick = { viewModel.navigateTo(NavigationDestination.FILES) }
                                )
                                else -> ShelfGrid(state.books, state.bookProgress, onBookClick)
                            }
                        }
                        NavigationDestination.RECENTS -> {
                            val recent = state.allBooks.sortedByDescending { it.addedAt }
                            if (recent.isEmpty()) EmptyView(
                                onImportClick = { epubPicker.launch(arrayOf("application/epub+zip")) },
                                onBrowseClick = { viewModel.navigateTo(NavigationDestination.FILES) }
                            ) else ShelfGrid(recent, state.bookProgress, onBookClick)
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
                            com.inktone.ui.screen.bookmark.AllBookmarksPanel(
                                onNavigateToBook = { bookId, chapterIndex, sentenceIndex ->
                                    onNavigateToBookmark(bookId, chapterIndex, sentenceIndex)
                                },
                                onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) }
                            )
                        }
                        NavigationDestination.STATS -> {
                            com.inktone.ui.screen.stats.StatsScreen(
                                onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) }
                            )
                        }
                        NavigationDestination.SYNC -> {
                            com.inktone.ui.screen.sync.SyncSettingsScreen(
                                onBack = { viewModel.navigateTo(NavigationDestination.LIBRARY) }
                            )
                        }
                        NavigationDestination.SETTINGS -> {
                            com.inktone.ui.screen.settings.SettingsScreen()
                        }
                        NavigationDestination.ABOUT -> {
                            com.inktone.ui.screen.about.AboutScreen()
                        }
                    }
                    } // end else (isLoading)
                }
            }
        }

        // ── POPUP NAVIGATION (titre TopBar) ──────────
        if (showNavPopup) {
            LibraryNavigationPopup(
                navSubItems = state.navSubItems,
                onDismiss = { showNavPopup = false },
                onFilterSelect = { viewModel.setFilterMode(it) },
                onSubItemSelect = { viewModel.selectNavSubItem(it) },
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

        // ── FLOATING CONTROLS ────────────────────────
        FloatingControls(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp),
            onReadResume = {
                val latest = state.allBooks.sortedByDescending { it.addedAt }.firstOrNull()
                if (latest != null) onBookClick(latest.id)
            }
        )
    }
    }
    }
}

// ─────────────────────────────────────────────────────
//  TOP BAR — Style prototype (bleue, dropdown trigger)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    currentDest: NavigationDestination,
    onMenu: () -> Unit,
    onNavPopup: () -> Unit,
    onFilterMode: () -> Unit,
    onSearch: () -> Unit,
    onOverflow: () -> Unit
) {
    val isLibrary = currentDest == NavigationDestination.LIBRARY

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onNavPopup)
            ) {
                Text(currentDest.label, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onPrimary)
                if (isLibrary) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, "Menu déroulant", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, stringResource(R.string.cd_menu_open), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        actions = {
            if (isLibrary) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, stringResource(R.string.cd_search), tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = onFilterMode) {
                    Icon(Icons.Default.FilterList, stringResource(R.string.cd_filter), tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(onClick = onOverflow) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more_options), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
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
                placeholder = { Text("Rechercher un livre...", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                    unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                    cursorColor = MaterialTheme.colorScheme.onPrimary,
                    focusedIndicatorColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.cd_close_search), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
    )
}

// ─────────────────────────────────────────────────────
//  SHELF GRID — 3 colonnes, couvertures gradient
// ─────────────────────────────────────────────────────

@Composable
private fun ShelfGrid(books: List<Book>, progressMap: Map<String, Float>, onBookClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(bottom = 80.dp)
    ) {
        items(books, key = { it.id }) { book ->
            val progress = progressMap[book.id] ?: 0f
            BookCover(
                book = book,
                progress = progress,
                gradientIndex = book.title.hashCode().mod(CoverGradients.size),
                onClick = { onBookClick(book.id) }
            )
        }
    }
}

@Composable
private fun BookCover(
    book: Book,
    progress: Float,
    gradientIndex: Int,
    onClick: () -> Unit
) {
    val gradient = CoverGradients[gradientIndex.coerceIn(0, CoverGradients.lastIndex)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Couverture avec gradient ou image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(gradient))
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_book_cover, book.title),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Titre sur la couverture si pas d'image
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
            }

            // Badge progression (%)
            val progressPct = (progress * 100).toInt().coerceIn(0, 100)
            val progressDescription = stringResource(R.string.cd_book_progress, progressPct)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .semantics {
                        contentDescription = progressDescription
                    }
            ) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$progressPct%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
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
                                if (i == 0) MaterialTheme.colorScheme.tertiary
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
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            book.author,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    navSubItems: Map<String, List<NavSubItem>>,
    onDismiss: () -> Unit,
    onFilterSelect: (FilterMode) -> Unit,
    onSubItemSelect: (String) -> Unit,
    currentFilter: FilterMode
) {
    var selectedCategory by remember { mutableStateOf("Tous les livres") }

    val categories = listOf(
        "Tous les livres" to FilterMode.ALL,
        "Favoris" to FilterMode.ALL,
        "Séries" to FilterMode.ALL,
        "Auteur" to FilterMode.BY_AUTHOR,
        "Tags" to FilterMode.ALL,
        "Dossiers" to FilterMode.ALL
    )

    val subItems = navSubItems[selectedCategory] ?: emptyList()

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── COLONNE GAUCHE : Catégories ────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                ) {
                    categories.forEach { (label, mode) ->
                        val isActive = label == selectedCategory

                        Surface(
                            color = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
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
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isActive) {
                                    Icon(
                                        Icons.Default.ChevronRight, "Ouvrir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        subItems.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    onSubItemSelect(item.filterId)
                                    onDismiss()
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 11.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.label,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${item.count}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.07f),
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
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        icon = {
            Icon(Icons.Default.Tune, "Options de tri", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(order.label, fontSize = 13.sp,
                                    color = if (sortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
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
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(type.label, fontSize = 13.sp,
                                    color = if (filterType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                // ── MISE EN PAGE ──────────────────────
                Text("Mise en page", fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
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
                                    tint = if (layoutMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                // ── BAS : Type de fichier + Fermer ────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type de fichier : Tous", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onDismiss) {
                        Text("Fermer", color = MaterialTheme.colorScheme.primary)
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
            color = MaterialTheme.colorScheme.surface,
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
                    color = MaterialTheme.colorScheme.primary) { onDismiss() }
            }
        }
    }
}

@Composable
private fun OverflowMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                              color: Color = MaterialTheme.colorScheme.surfaceVariant, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = color)
    }
}

// ─────────────────────────────────────────────────────
//  NAV DRAWER — Tiroir latéral gauche
// ─────────────────────────────────────────────────────

@Composable
private fun NavDrawerContent(
    currentDest: NavigationDestination,
    onNavigate: (NavigationDestination) -> Unit,
    onThemeToggle: () -> Unit,
    onDebug: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)))
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text("InkTone", color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        // Menu items dynamiques
        Column(modifier = Modifier.weight(1f)) {
            NavigationDestination.entries
                .filter { it != NavigationDestination.SETTINGS && it != NavigationDestination.ABOUT }
                .forEach { dest ->
                val isActive = dest == currentDest
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            dest.label, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Footer
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawerFooterBtn("Options", Icons.Default.Settings) {
                    onNavigate(NavigationDestination.SETTINGS)
                }
                DrawerFooterBtn("À propos", Icons.Default.Info) {
                    onNavigate(NavigationDestination.ABOUT)
                }
                DrawerFooterBtn("Thème", Icons.Default.Palette) { onThemeToggle() }
                if (com.inktone.BuildConfig.DEBUG) {
                    DrawerFooterBtn("Debug", Icons.Default.Build) { onDebug() }
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // Pill audio — reprend la lecture TTS du dernier livre
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            tonalElevation = 4.dp
        ) {
            IconButton(onClick = onReadResume) {
                Icon(Icons.Outlined.Headphones, "Audio",
                    tint = MaterialTheme.colorScheme.ttsActive, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(10.dp))

        // FAB
        FloatingActionButton(
            onClick = onReadResume,
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.MenuBook, stringResource(R.string.cd_resume_reading),
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : vide, chargement, erreur
// ─────────────────────────────────────────────────────

@Composable
private fun EmptyView(onImportClick: (() -> Unit)? = null, onBrowseClick: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Commencez votre bibliothèque",
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Importez un fichier .epub depuis vos fichiers ou parcourez un catalogue OPDS.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onImportClick != null) {
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importer un livre")
                }
            }
            if (onBrowseClick != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Parcourir mes fichiers")
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    val message = error.trimStart('⚠', '❌', '✅', ' ', '️')
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Erreur",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Réessayer", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            TextButton(onClick = onDismiss) {
                Text("OK", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ComingSoonPlaceholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Construction, "En construction", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Text("Bientôt disponible", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 13.sp)
        }
    }
}

