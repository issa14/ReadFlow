package com.inktone.ui.screen.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.opds.OpdsRepository
import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.model.OpdsEntry
import com.inktone.domain.model.OpdsFeed
import com.inktone.domain.model.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpdsUiState(
    val feed: OpdsFeed? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val catalog: OpdsCatalog = OpdsCatalog(
        name = "Gallica (BNF)",
        url = "https://gallica.bnf.fr/opds"
    ),
    val urlInput: String = "https://gallica.bnf.fr/opds",
    val usernameInput: String = "",
    val passwordInput: String = "",
    val showAddCatalog: Boolean = false,
    val navigationStack: List<String> = emptyList() // historique de navigation
)

@HiltViewModel
class OpdsViewModel @Inject constructor(
    private val repository: OpdsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsUiState())
    val uiState: StateFlow<OpdsUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            val catalog = _uiState.value.catalog
            repository.loadFeed(catalog).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Resource.Success -> _uiState.update {
                        it.copy(feed = resource.data, isLoading = false, error = null)
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(isLoading = false, error = resource.message ?: resource.throwable.message)
                    }
                }
            }
        }
    }

    fun navigateToPage(url: String) {
        viewModelScope.launch {
            val catalog = _uiState.value.catalog
            val stack = _uiState.value.navigationStack + (_uiState.value.catalog.url)
            _uiState.update { it.copy(navigationStack = stack) }
            repository.loadFeed(catalog, url).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true, error = null) }
                    is Resource.Success -> _uiState.update {
                        it.copy(feed = resource.data, isLoading = false)
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(isLoading = false, error = resource.message)
                    }
                }
            }
        }
    }

    fun searchOpds(query: String) {
        val searchUrl = _uiState.value.feed?.searchUrl ?: return
        // L'URL OpenSearch nécessite de remplacer {searchTerms}
        val url = searchUrl.replace("{searchTerms}", query)
            .replace("{searchTerms?}", query)
            .replace("{count?}", "20")
        navigateToPage(url)
    }

    fun goBack() {
        val stack = _uiState.value.navigationStack
        if (stack.isEmpty()) return
        val previousUrl = stack.last()
        val newStack = stack.dropLast(1)
        _uiState.update { it.copy(navigationStack = newStack) }
        viewModelScope.launch {
            val catalog = _uiState.value.catalog
            repository.loadFeed(catalog, previousUrl).collect { resource ->
                when (resource) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoading = true) }
                    is Resource.Success -> _uiState.update {
                        it.copy(feed = resource.data, isLoading = false)
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(isLoading = false, error = resource.message)
                    }
                }
            }
        }
    }

    fun updateUrl(url: String) = _uiState.update { it.copy(urlInput = url) }
    fun updateUsername(u: String) = _uiState.update { it.copy(usernameInput = u) }
    fun updatePassword(p: String) = _uiState.update { it.copy(passwordInput = p) }
    fun toggleAddCatalog() = _uiState.update { it.copy(showAddCatalog = !it.showAddCatalog) }

    fun connectToCatalog() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                catalog = OpdsCatalog(
                    url = state.urlInput,
                    username = state.usernameInput.ifBlank { null },
                    password = state.passwordInput.ifBlank { null }
                ),
                showAddCatalog = false
            )
        }
        loadCatalog()
    }

    fun downloadEntry(entry: OpdsEntry) {
        // TODO: Télécharger l'EPUB et l'importer dans la bibliothèque
    }
}
