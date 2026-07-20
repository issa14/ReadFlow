package com.inktone.ui.screen.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Book
import com.inktone.domain.repository.BookRepository
import com.inktone.data.settings.AppTheme
import com.inktone.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class LibraryUiState(
    val allBooks: List<Book> = emptyList(),
    val books: List<Book> = emptyList(),
    val bookProgress: Map<String, Float> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ALL,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val filterType: FilterType = FilterType.ALL,
    val layoutMode: LayoutMode = LayoutMode.GRID_COVERS,
    val isFilterDialogVisible: Boolean = false,
    val currentDestination: NavigationDestination = NavigationDestination.LIBRARY,
    val appTheme: AppTheme = AppTheme.PAPIER_ART,
    val importProgress: Float? = null,
    val importStatus: String? = null,
    val importSuccessSnackbar: String? = null,
    val navSubItems: Map<String, List<NavSubItem>> = emptyMap(),
    val isRebuildingCovers: Boolean = false,
    val coverRebuildProgress: Pair<Int, Int>? = null,
    val libraryActionMessage: String? = null
)

data class NavSubItem(val label: String, val count: Int, val filterId: String)

enum class FilterMode { ALL, BY_AUTHOR, BY_TITLE, IN_PROGRESS, READ, UNREAD }
enum class SortOrder(val label: String) { TITLE("Nom de livre"), AUTHOR("Auteur"), DATE("Date d'import"), FOLDERS("Dossiers"), RECENT("Liste des récents") }
enum class FilterType(val label: String) { ALL("Tous"), UNREAD("Non lu"), IN_PROGRESS("En cours"), READ("Lu") }
enum class LayoutMode { LIST, GRID, GRID_COVERS }

enum class NavigationDestination(
    val label: String,
    val icon: ImageVector
) {
    RECENTS("Liste des récents", Icons.Default.Schedule),
    LIBRARY("Bibliothèque", Icons.Default.Book),
    FILES("Fichiers", Icons.Default.Folder),
    OPDS("Catalogues OPDS", Icons.Default.Language),
    BOOKMARKS("Marque-pages et notes", Icons.Default.Bookmark),
    STATS("Statistiques de lecture", Icons.Default.BarChart),
    SYNC("Synchronisation & Sauvegarde", Icons.Default.Sync),
    SETTINGS("Options", Icons.Default.Settings),
    ABOUT("À propos", Icons.Default.Info)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { loadBooks() }

    private fun loadBooks() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                val progressMap = mutableMapOf<String, Float>()
                books.forEach { book ->
                    try {
                        val progress = bookRepository.getProgress(book.id)
                        progressMap[book.id] = progress?.totalProgressFraction ?: 0f
                    } catch (e: Exception) {
                        Log.e("LibraryVM", "Error loading progress for book ${book.id}", e)
                        progressMap[book.id] = 0f
                    }
                }
                _uiState.update { it.copy(allBooks = books, bookProgress = progressMap, isLoading = false) }
                applyFilters()
                loadNavSubItems(books)
                com.inktone.PerfLogger.logMemorySnapshot("Library loaded")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setFilterMode(mode: FilterMode) {
        _uiState.update { it.copy(filterMode = mode) }
        applyFilters()
    }

    fun showFilterDialog() { _uiState.update { it.copy(isFilterDialogVisible = true) } }
    fun hideFilterDialog() { _uiState.update { it.copy(isFilterDialogVisible = false) } }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        applyFilters()
    }

    fun setFilterType(type: FilterType) {
        _uiState.update { it.copy(filterType = type) }
        applyFilters()
    }

    fun setLayoutMode(mode: LayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun navigateTo(dest: NavigationDestination) {
        _uiState.update { it.copy(currentDestination = dest) }
    }

    private fun loadNavSubItems(books: List<Book>) {
        val byAuthor = books
            .groupBy { it.author }
            .map { (author, authorBooks) -> NavSubItem(author, authorBooks.size, "author:$author") }
            .sortedBy { it.label.lowercase() }

        val allItem = NavSubItem("Tous les livres", books.size, "all")

        _uiState.update {
            it.copy(navSubItems = mapOf(
                "Tous les livres" to listOf(allItem),
                "Auteur" to byAuthor,
                "Favoris" to emptyList(),
                "Séries" to emptyList(),
                "Tags" to emptyList(),
                "Dossiers" to emptyList()
            ))
        }
    }

    /** Sélection d'un sous-élément du popup de navigation (auteur, "tous les livres"...). */
    fun selectNavSubItem(filterId: String) {
        when {
            filterId == "all" -> setSearchQuery("")
            filterId.startsWith("author:") -> setSearchQuery(filterId.removePrefix("author:"))
        }
    }

    fun toggleTheme() {
        val next = when (_uiState.value.appTheme) {
            AppTheme.PAPIER_ART -> AppTheme.OBSIDIAN
            AppTheme.OBSIDIAN   -> AppTheme.NORDIC_FOG
            AppTheme.NORDIC_FOG -> AppTheme.SIGNATURE
            AppTheme.SIGNATURE  -> AppTheme.PAPIER_ART
            AppTheme.SYSTEM     -> AppTheme.PAPIER_ART
        }
        _uiState.update { it.copy(appTheme = next) }
    }

    private fun applyFilters() {
        val s = _uiState.value
        val progressMap = s.bookProgress

        val filtered = s.allBooks.asSequence()
            .filter { book ->
                s.searchQuery.isBlank() ||
                book.title.contains(s.searchQuery, ignoreCase = true) ||
                book.author.contains(s.searchQuery, ignoreCase = true)
            }
            .let { seq ->
                when (s.sortOrder) {
                    SortOrder.TITLE -> seq.sortedBy { it.title.lowercase() }
                    SortOrder.AUTHOR -> seq.sortedBy { it.author.lowercase() }
                    SortOrder.DATE, SortOrder.RECENT -> seq.sortedByDescending { it.addedAt }
                    SortOrder.FOLDERS -> seq
                }
            }
            .filter { book ->
                when (s.filterType) {
                    FilterType.ALL -> true
                    FilterType.UNREAD -> (progressMap[book.id] ?: 0f) <= 0.01f
                    FilterType.IN_PROGRESS -> {
                        val p = progressMap[book.id] ?: 0f
                        p > 0.01f && p < 0.99f
                    }
                    FilterType.READ -> (progressMap[book.id] ?: 0f) >= 0.99f
                }
            }
            .toList()

        _uiState.update { it.copy(books = filtered) }
    }

    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, importProgress = 0f, importStatus = "Préparation de l'import...") }
            try {
                // Persister la permission SAF pour les réimports futurs
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Permission non persistable (ex: URI temporaire) — on continue
                }

                val fileName = resolveFileName(uri) ?: "inconnu.epub"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bookRepository.importEpub(stream, fileName) { progress, status ->
                        _uiState.update { it.copy(importProgress = progress, importStatus = status) }
                    }
                } ?: throw IllegalStateException("Impossible de lire le fichier")
                _uiState.update { it.copy(importProgress = null, importStatus = null) }
                loadBooks()
                // Toast de succès pour le premier import
                if (!settingsRepository.hasImportedFirstBook.first()) {
                    settingsRepository.markFirstBookImported()
                    _uiState.update { it.copy(importSuccessSnackbar = "Livre importé — appuyez pour commencer la lecture") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false, importProgress = null, importStatus = null) }
            }
        }
    }

    /** Import depuis un fichier local (explorateur FilesScreen). */
    fun importFile(inputStream: java.io.InputStream, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, importProgress = 0f, importStatus = "Préparation de l'import...") }
            try {
                bookRepository.importEpub(inputStream, fileName) { progress, status ->
                    _uiState.update { it.copy(importProgress = progress, importStatus = status) }
                }
                _uiState.update { it.copy(importProgress = null, importStatus = null) }
                loadBooks()
                if (!settingsRepository.hasImportedFirstBook.first()) {
                    settingsRepository.markFirstBookImported()
                    _uiState.update { it.copy(importSuccessSnackbar = "Livre importé — appuyez pour commencer la lecture") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false, importProgress = null, importStatus = null) }
            }
        }
    }

    /** Import par lot depuis des URIs (multi-sélection SAF). */
    fun importBooks(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, importProgress = 0f, importStatus = "Préparation de l'import...") }
            try {
                val total = uris.size
                uris.forEachIndexed { index, uri ->
                    val fileName = resolveFileName(uri) ?: "inconnu.epub"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bookRepository.importEpub(stream, fileName) { progress, status ->
                            val batchProgress = (index.toFloat() + progress) / total
                            _uiState.update {
                                it.copy(
                                    importProgress = batchProgress,
                                    importStatus = "[Livre ${index + 1}/$total] $status"
                                )
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importProgress = null, importStatus = null) }
                    loadBooks()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = e.message, isLoading = false, importProgress = null, importStatus = null) }
                }
            }
        }
    }

    fun refresh() = loadBooks()
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearImportSuccessSnackbar() { _uiState.update { it.copy(importSuccessSnackbar = null) } }
    fun clearLibraryActionMessage() { _uiState.update { it.copy(libraryActionMessage = null) } }

    /** Ré-extrait la couverture de chaque livre depuis son EPUB source (menu Bibliothèque). */
    fun regenerateAllCovers() {
        if (_uiState.value.isRebuildingCovers) return
        viewModelScope.launch {
            val books = _uiState.value.allBooks
            _uiState.update { it.copy(isRebuildingCovers = true, coverRebuildProgress = 0 to books.size) }
            books.forEachIndexed { index, book ->
                bookRepository.regenerateCover(book.id)
                _uiState.update { it.copy(coverRebuildProgress = (index + 1) to books.size) }
            }
            _uiState.update {
                it.copy(
                    isRebuildingCovers = false,
                    coverRebuildProgress = null,
                    libraryActionMessage = "Couvertures reconstruites"
                )
            }
            loadBooks()
        }
    }

    /** Retire les couvertures extraites de tous les livres (retour au dégradé par défaut). */
    fun resetCoversToDefault() {
        viewModelScope.launch {
            bookRepository.clearAllCovers()
            loadBooks()
            _uiState.update { it.copy(libraryActionMessage = "Couvertures réinitialisées") }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }
}
