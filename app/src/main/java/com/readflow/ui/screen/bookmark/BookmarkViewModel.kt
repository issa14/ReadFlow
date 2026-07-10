package com.readflow.ui.screen.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.data.database.BookmarkDao
import com.readflow.data.database.entity.BookmarkEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks

    private var currentBookId: String = ""

    fun load(bookId: String) {
        if (currentBookId == bookId) return
        currentBookId = bookId
        viewModelScope.launch {
            bookmarkDao.getBookmarks(bookId).collect { _bookmarks.value = it }
        }
    }

    fun add(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.insert(bookmark) }
    }

    fun delete(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.delete(bookmark) }
    }

    fun toggle(bookId: String, chapterIndex: Int, sentenceIndex: Int, text: String) {
        viewModelScope.launch {
            val existing = bookmarkDao.findByPosition(bookId, chapterIndex, sentenceIndex)
            if (existing != null) {
                bookmarkDao.delete(existing)
            } else {
                bookmarkDao.insert(BookmarkEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    sentenceIndex = sentenceIndex,
                    text = text.take(120)
                ))
            }
        }
    }

    // ── Mode "Tous les livres" pour le drawer ──

    fun loadAll(searchQuery: String = "") {
        viewModelScope.launch {
            val flow = if (searchQuery.isBlank()) {
                bookmarkDao.getAllBookmarks()
            } else {
                bookmarkDao.searchBookmarks(searchQuery)
            }
            flow.collect { _bookmarks.value = it }
        }
    }
}
