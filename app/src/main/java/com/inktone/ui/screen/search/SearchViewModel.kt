package com.inktone.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.database.SearchDao
import com.inktone.data.database.entity.SentenceFts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchDao: SearchDao
) : ViewModel() {

    private val _results = MutableStateFlow<List<SentenceFts>>(emptyList())
    val results: StateFlow<List<SentenceFts>> = _results

    private var bookId: String = ""

    fun init(bookId: String) { this.bookId = bookId }

    fun search(query: String) {
        viewModelScope.launch {
            val sanitized = query.replace("\"", "").trim()
            if (sanitized.length < 2) { _results.value = emptyList(); return@launch }
            _results.value = searchDao.search(bookId, "$sanitized*")
        }
    }
}
