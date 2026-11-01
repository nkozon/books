package com.ozon.books.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.ozon.books.model.Book
import com.ozon.books.model.Bookmark
import com.ozon.books.model.Chapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

class BookRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadataFile: File = File(context.filesDir, "library_metadata.json")
    private val booksDir: File = File(context.filesDir, "extracted_books").apply { mkdirs() }
    private val trashDir: File = File(context.filesDir, "trash").apply { mkdirs() }
    private val metadataMutex = Mutex()

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val recentlyReadBooks: StateFlow<List<Book>> = _books.map { list ->
        list.filter { it.isRecentlyRead }
            .sortedByDescending { it.lastReadTimestamp }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        scope.launch {
            loadBooks()
            cleanupTrash()
            _isLoaded.value = true
        }
    }

    private fun loadBooks() {
        if (!metadataFile.exists()) {
            _books.value = emptyList()
            return
        }
        try {
            val jsonArray = JSONArray(metadataFile.readText())
            val list = mutableListOf<Book>()
            for (i in 0 until jsonArray.length()) {
                val book = runCatching {
                    Book.fromJsonObject(jsonArray.getJSONObject(i))
                }.getOrNull() ?: continue
                val storedDir = File(book.extractedDirPath)
                val actualDir = when {
                    storedDir.exists() -> storedDir
                    File(booksDir, book.id).exists() -> File(booksDir, book.id)
                    else -> continue
                }
                val healedCover = book.coverImagePath?.let { coverPath ->
                    if (File(coverPath).exists()) {
                        coverPath
                    } else if (coverPath.startsWith(storedDir.path)) {
                        File(actualDir, coverPath.removePrefix(storedDir.path).trimStart('/')).absolutePath
                    } else {
                        null
                    }
                }
                list.add(
                    book.copy(
                        extractedDirPath = actualDir.absolutePath,
                        coverImagePath = healedCover
                    )
                )
            }
            _books.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            _books.value = emptyList()
        }
    }

    private suspend fun persistBooks() = withContext(Dispatchers.IO) {
        metadataMutex.withLock {
            try {
                val jsonArray = JSONArray()
                _books.value.forEach { book ->
                    jsonArray.put(book.toJsonObject())
                }
                val temp = File(metadataFile.parentFile, "${metadataFile.name}.tmp")
                temp.writeText(jsonArray.toString())
                try {
                    Files.move(
                        temp.toPath(),
                        metadataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        temp.toPath(),
                        metadataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun importBookFromUri(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val bookId = UUID.randomUUID().toString()
        val targetDir = File(booksDir, bookId)
        val tempFile = File.createTempFile("import_", ".epub", context.cacheDir)
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open stream from URI: $uri")
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    copyWithLimit(input, output, MAX_EPUB_BYTES)
                }
            }

            val hash = sha256(tempFile)
            if (_books.value.any { it.fileHash == hash }) {
                throw IllegalStateException("This EPUB is already in your library.")
            }

            val importContext = currentCoroutineContext()
            val parsed = EpubParser.parse(tempFile, targetDir) {
                importContext.ensureActive()
            }
            if (parsed.chapters.isEmpty()) {
                throw IllegalStateException("This EPUB does not contain any readable chapters.")
            }

            val book = Book(
                id = bookId,
                title = parsed.title,
                author = parsed.author,
                coverImagePath = parsed.coverImagePath,
                extractedDirPath = parsed.extractedDirPath,
                currentChapterIndex = 0,
                totalChapters = parsed.chapters.size,
                currentScrollProgress = 0f,
                currentCharOffset = 0,
                lastReadTimestamp = 0L,
                addedTimestamp = System.currentTimeMillis(),
                fileHash = hash
            )

            _books.update { listOf(book) + it }
            persistBooks()
            Result.success(book)
        } catch (e: CancellationException) {
            targetDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun updateReadingProgress(
        bookId: String,
        chapterIndex: Int,
        chapterProgress: Float,
        charOffset: Int
    ) = withContext(Dispatchers.IO) {
        var found = false
        _books.update { current ->
            current.map { book ->
                if (book.id == bookId) {
                    found = true
                    book.copy(
                        currentChapterIndex = chapterIndex.coerceIn(0, book.totalChapters - 1),
                        currentScrollProgress = chapterProgress.coerceIn(0f, 1f),
                        currentCharOffset = charOffset.coerceAtLeast(0),
                        lastReadTimestamp = System.currentTimeMillis()
                    )
                } else {
                    book
                }
            }
        }
        if (found) persistBooks()
    }

    /**
     * Fire-and-forget save used when the reader is leaving and its ViewModel is about to be cleared.
     */
    fun updateReadingProgressNow(
        bookId: String,
        chapterIndex: Int,
        chapterProgress: Float,
        charOffset: Int
    ) {
        scope.launch {
            updateReadingProgress(bookId, chapterIndex, chapterProgress, charOffset)
        }
    }

    suspend fun updateBookmarks(bookId: String, bookmarks: List<Bookmark>) = withContext(Dispatchers.IO) {
        _books.update { current ->
            current.map { book ->
                if (book.id == bookId) book.copy(bookmarks = bookmarks) else book
            }
        }
        persistBooks()
    }

    fun getBook(bookId: String): Book? {
        return _books.value.find { it.id == bookId }
    }

    suspend fun getChapters(book: Book): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            EpubParser.parseExtracted(File(book.extractedDirPath)).chapters
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun removeBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        val book = _books.value.find { it.id == bookId } ?: return@withContext null
        val source = File(book.extractedDirPath)
        val destination = File(trashDir, book.id)
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.deleteRecursively()
        try {
            if (source.exists()) {
                if (!source.renameTo(destination)) {
                    source.copyRecursively(destination, overwrite = true)
                    source.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _books.update { list -> list.filterNot { it.id == bookId } }
        persistBooks()
        book.copy(extractedDirPath = destination.absolutePath)
    }

    suspend fun restoreBook(book: Book): Boolean = withContext(Dispatchers.IO) {
        val source = File(trashDir, book.id)
        if (!source.exists()) return@withContext false
        val destination = File(booksDir, book.id)
        if (destination.exists()) destination.deleteRecursively()
        val moved = try {
            if (source.renameTo(destination)) {
                true
            } else {
                source.copyRecursively(destination, overwrite = true)
                source.deleteRecursively()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        if (!moved) return@withContext false
        val restored = book.copy(
            extractedDirPath = destination.absolutePath,
            coverImagePath = book.coverImagePath?.let { cover ->
                if (cover.startsWith(source.path)) {
                    File(destination, cover.removePrefix(source.path).trimStart('/')).absolutePath
                } else {
                    cover
                }
            }
        )
        _books.update { listOf(restored) + it }
        persistBooks()
        true
    }

    private fun cleanupTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        trashDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.deleteRecursively()
        }
    }

    private suspend fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        limit: Long
    ) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw IllegalArgumentException("EPUB file is too large.")
            output.write(buffer, 0, read)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_EPUB_BYTES = 256L * 1024L * 1024L
        private const val TRASH_RETENTION_MS = 24L * 60L * 60L * 1000L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
