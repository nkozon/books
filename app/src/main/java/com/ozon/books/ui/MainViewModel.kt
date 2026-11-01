package com.ozon.books.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ozon.books.data.BookRepository
import com.ozon.books.model.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MainEvent {
    data class Message(val text: String) : MainEvent
    data class Imported(val book: Book) : MainEvent
    data class Deleted(val book: Book) : MainEvent
}

class MainViewModel(private val repository: BookRepository) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.books
    val recentlyReadBooks: StateFlow<List<Book>> = repository.recentlyReadBooks
    val isLoaded: StateFlow<Boolean> = repository.isLoaded

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private var importJob: Job? = null

    fun importBook(uri: Uri) {
        if (_isImporting.value) return
        _isImporting.value = true
        importJob = viewModelScope.launch {
            try {
                val result = repository.importBookFromUri(uri)
                result.onSuccess { book ->
                    _events.tryEmit(MainEvent.Imported(book))
                }.onFailure { error ->
                    _events.tryEmit(
                        MainEvent.Message(
                            "Failed to import EPUB: ${error.localizedMessage ?: "Unknown error"}"
                        )
                    )
                }
            } catch (e: CancellationException) {
                _events.tryEmit(MainEvent.Message("Import cancelled"))
                throw e
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            val removed = repository.removeBook(bookId)
            if (removed != null) {
                _events.tryEmit(MainEvent.Deleted(removed))
            } else {
                _events.tryEmit(MainEvent.Message("Could not delete this book."))
            }
        }
    }

    fun undoDelete(book: Book) {
        viewModelScope.launch {
            val restored = repository.restoreBook(book)
            _events.tryEmit(
                MainEvent.Message(
                    if (restored) "\"${book.title}\" restored" else "Could not restore this book."
                )
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(BookRepository.getInstance(context))
            }
        }
    }
}
