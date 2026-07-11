package com.readflow.ui.screen.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.domain.model.Book
import com.readflow.domain.repository.BookRepository
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
    val isDarkTheme: Boolean = true
)

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
    BOOKMARKS("Marque-pages et notes", Icons.Default.Bookmark)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { loadBooks() }

    private fun loadBooks() {
        viewModelScope.launch {
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

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun applyFilters() {
        val s = _uiState.value
        var filtered = s.allBooks

        // Filtre texte
        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.author.lowercase().contains(q)
            }
        }

        // Tri
        filtered = when (s.sortOrder) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.AUTHOR -> filtered.sortedBy { it.author.lowercase() }
            SortOrder.DATE -> filtered.sortedByDescending { it.addedAt }
            SortOrder.FOLDERS -> filtered  // TODO
            SortOrder.RECENT -> filtered.sortedByDescending { it.addedAt }
        }

        // Filtre type (progression réelle depuis Room)
        filtered = when (s.filterType) {
            FilterType.ALL -> filtered
            FilterType.UNREAD -> filtered.filter { (s.bookProgress[it.id] ?: 0f) <= 0.01f }
            FilterType.IN_PROGRESS -> filtered.filter {
                val p = s.bookProgress[it.id] ?: 0f
                p > 0.01f && p < 0.99f
            }
            FilterType.READ -> filtered.filter { (s.bookProgress[it.id] ?: 0f) >= 0.99f }
        }

        _uiState.update { it.copy(books = filtered) }
    }

    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val fileName = resolveFileName(uri) ?: "inconnu.epub"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bookRepository.importEpub(stream, fileName)
                } ?: throw IllegalStateException("Impossible de lire le fichier")
                loadBooks()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    /** Import depuis un fichier local (explorateur FilesScreen). */
    fun importFile(inputStream: java.io.InputStream, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                bookRepository.importEpub(inputStream, fileName)
                loadBooks()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    /** Import par lot depuis des URIs (multi-sélection SAF). */
    fun importBooks(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                uris.forEach { uri ->
                    val fileName = resolveFileName(uri) ?: "inconnu.epub"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bookRepository.importEpub(stream, fileName)
                    }
                }
                withContext(Dispatchers.Main) { loadBooks() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
            }
        }
    }

    fun refresh() = loadBooks()
    fun clearError() { _uiState.update { it.copy(error = null) } }

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
