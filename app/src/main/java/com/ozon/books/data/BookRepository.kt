package com.ozon.books.data

import android.content.Context
import android.net.Uri
import com.ozon.books.model.Book
import com.ozon.books.model.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

class BookRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val metadataFile: File = File(context.filesDir, "library_metadata.json")
    private val booksDir: File = File(context.filesDir, "extracted_books").apply { mkdirs() }

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    val recentlyReadBooks: StateFlow<List<Book>> = _books.map { list ->
        list.filter { it.isRecentlyRead }
            .sortedByDescending { it.lastReadTimestamp }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        loadBooks()
    }

    private fun loadBooks() {
        if (!metadataFile.exists()) {
            _books.value = emptyList()
            return
        }
        try {
            val jsonStr = metadataFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Book>()
            for (i in 0 until jsonArray.length()) {
                val bookObj = jsonArray.getJSONObject(i)
                val book = Book.fromJsonObject(bookObj)
                // Verify extracted folder still exists
                if (File(book.extractedDirPath).exists()) {
                    list.add(book)
                }
            }
            _books.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _books.value = emptyList()
        }
    }

    private fun persistBooks() {
        try {
            val jsonArray = JSONArray()
            _books.value.forEach { book ->
                jsonArray.put(book.toJsonObject())
            }
            metadataFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importBookFromUri(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open stream from URI: $uri")

            val bookId = UUID.randomUUID().toString()
            val targetDir = File(booksDir, bookId)

            val parsed = EpubParser.parse(inputStream, targetDir)

            val book = Book(
                id = bookId,
                title = parsed.title,
                author = parsed.author,
                coverImagePath = parsed.coverImagePath,
                extractedDirPath = parsed.extractedDirPath,
                currentChapterIndex = 0,
                totalChapters = parsed.chapters.size.coerceAtLeast(1),
                currentScrollProgress = 0f,
                lastReadTimestamp = 0L,
                addedTimestamp = System.currentTimeMillis()
            )

            val updatedList = _books.value.toMutableList().apply { add(0, book) }
            _books.value = updatedList
            persistBooks()
            book
        }
    }


    suspend fun updateReadingProgress(
        bookId: String,
        chapterIndex: Int,
        scrollProgress: Float
    ) = withContext(Dispatchers.IO) {
        val currentList = _books.value
        val index = currentList.indexOfFirst { it.id == bookId }
        if (index != -1) {
            val oldBook = currentList[index]
            val updatedBook = oldBook.copy(
                currentChapterIndex = chapterIndex.coerceIn(0, oldBook.totalChapters - 1),
                currentScrollProgress = scrollProgress.coerceIn(0f, 1f),
                lastReadTimestamp = System.currentTimeMillis()
            )
            val newList = currentList.toMutableList().apply {
                set(index, updatedBook)
            }
            _books.value = newList
            persistBooks()
        }
    }

    fun getBook(bookId: String): Book? {
        return _books.value.find { it.id == bookId }
    }

    suspend fun getChapters(book: Book): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val parsed = EpubParser.parseExtracted(File(book.extractedDirPath))
            parsed.chapters
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        val currentList = _books.value
        val book = currentList.find { it.id == bookId }
        if (book != null) {
            // Delete files
            try {
                File(book.extractedDirPath).deleteRecursively()
            } catch (_: Exception) {
            }
            val newList = currentList.filterNot { it.id == bookId }
            _books.value = newList
            persistBooks()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
