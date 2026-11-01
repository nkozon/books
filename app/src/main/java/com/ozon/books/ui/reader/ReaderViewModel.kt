package com.ozon.books.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ozon.books.R
import com.ozon.books.data.BookRepository
import com.ozon.books.model.Book
import com.ozon.books.model.Bookmark
import com.ozon.books.model.Chapter
import com.ozon.books.model.ReaderTypeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = true,
    val isChapterLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentChapterIndex: Int = 0,
    val currentPageIndex: Int = 0,
    val pages: List<ChapterPage> = emptyList(),
    val nextChapterPages: List<ChapterPage> = emptyList(),
    val elements: List<ChapterElement> = emptyList(),
    val currentCharOffset: Int = 0,
    val chapterProgress: Float = 0f,
    val positionRequestId: Int = 0,
    val bookmarks: List<Bookmark> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchCompleted: Boolean = false
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)

    val chapterCount: Int get() = chapters.size.coerceAtLeast(1)

    val totalBookProgress: Float
        get() = ((currentChapterIndex + chapterProgress.coerceIn(0f, 1f)) / chapterCount)
            .coerceIn(0f, 1f)

    val currentBookmark: Bookmark?
        get() = bookmarks.firstOrNull {
            it.chapterIndex == currentChapterIndex && abs(it.charOffset - currentCharOffset) <= 600
        }
}

private data class ChapterPreparationKey(
    val index: Int,
    val typography: ReaderTypography,
    val layout: ReaderLayout,
    val paginated: Boolean
)

private data class PreparedChapter(
    val key: ChapterPreparationKey,
    val elements: List<ChapterElement>,
    val pages: List<ChapterPage>
)

class ReaderViewModel(
    private val repository: BookRepository,
    private val bookId: String,
    appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val elementsCache = ConcurrentHashMap<Int, List<ChapterElement>>()
    private val elementOffsetsCache = ConcurrentHashMap<Int, List<Int>>()
    private val chapterLengthCache = ConcurrentHashMap<Int, Int>()

    private var typography = ReaderTypography()
    private var layout = ReaderLayout()
    private var isPaginated = true

    private val literataTypeface: Typeface? = runCatching {
        ResourcesCompat.getFont(appContext, R.font.literata)
    }.getOrNull()
    private val literataItalicTypeface: Typeface? = runCatching {
        ResourcesCompat.getFont(appContext, R.font.literata_italic)
    }.getOrNull()

    private var preloadTask: Deferred<PreparedChapter>? = null
    private var preloadKey: ChapterPreparationKey? = null
    private var preparedNextChapter: PreparedChapter? = null

    private var loadChapterJob: Job? = null
    private var paginateJob: Job? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var loadGeneration = 0
    private var paginationGeneration = 0

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            repository.isLoaded.first { it }
            val book = repository.getBook(bookId)

            if (book == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "This book could not be found in your library."
                    )
                }
                return@launch
            }

            val chapters = withContext(Dispatchers.IO) { repository.getChapters(book) }
            if (chapters.isEmpty()) {
                _uiState.update {
                    it.copy(
                        book = book,
                        isLoading = false,
                        errorMessage = "This book does not contain any readable chapters."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    book = book,
                    chapters = chapters,
                    bookmarks = book.bookmarks,
                    currentChapterIndex = book.currentChapterIndex.coerceIn(0, chapters.lastIndex),
                    currentCharOffset = book.currentCharOffset.coerceAtLeast(0),
                    isLoading = false
                )
            }
            loadChapter(book.currentChapterIndex, book.currentCharOffset)
        }
    }

    fun setTypography(newTypography: ReaderTypography) {
        if (typography == newTypography) return
        typography = newTypography
        preloadNextChapter()
        if (isPaginated) {
            repaginateCurrentChapter()
        } else {
            requestPositionSync()
        }
    }

    fun setLayout(newLayout: ReaderLayout) {
        if (!newLayout.isValid) return
        if (layout.widthDp == newLayout.widthDp &&
            layout.imageWidthDp == newLayout.imageWidthDp &&
            layout.heightDp == newLayout.heightDp &&
            layout.density == newLayout.density &&
            layout.fontScale == newLayout.fontScale
        ) {
            return
        }
        layout = newLayout
        preloadNextChapter()
        if (isPaginated) {
            repaginateCurrentChapter()
        } else {
            requestPositionSync()
        }
    }

    private fun requestPositionSync() {
        _uiState.update { it.copy(positionRequestId = it.positionRequestId + 1) }
    }

    fun setPaginated(paginated: Boolean) {
        if (isPaginated == paginated) return
        isPaginated = paginated
        preloadNextChapter()
        if (!paginated) {
            // A repagination from the previous page mode must not move the scroll position.
            paginateJob?.cancel()
            paginationGeneration++
            _uiState.update { it.copy(positionRequestId = it.positionRequestId + 1) }
        } else {
            repaginateCurrentChapter()
            // If there is nothing to paginate yet, still ask the pager to sync its position.
            if (_uiState.value.elements.isEmpty() || !layout.isValid) {
                _uiState.update { it.copy(positionRequestId = it.positionRequestId + 1) }
            }
        }
    }

    private fun preparationKey(index: Int) = ChapterPreparationKey(index, typography, layout, isPaginated)

    private suspend fun prepareChapter(key: ChapterPreparationKey): PreparedChapter {
        val chapter = _uiState.value.chapters[key.index]
        val elements = getOrLoadElements(chapter)
        val pages = if (key.paginated) {
            paginateIfNeeded(elements, key.typography, key.layout)
        } else emptyList()
        return PreparedChapter(key, elements, pages)
    }

    private fun preloadNextChapter() {
        val state = _uiState.value
        val nextIndex = state.currentChapterIndex + 1
        val key = nextIndex.takeIf { it in state.chapters.indices }?.let(::preparationKey)
        if (key == preloadKey && preloadTask?.isCancelled == false) return
        preloadTask?.cancel()
        preloadKey = key
        preparedNextChapter = null
        _uiState.update { it.copy(nextChapterPages = emptyList()) }
        if (key == null) return
        preloadTask = viewModelScope.async {
            val prepared = prepareChapter(key)
            if (preloadKey == key && preparationKey(key.index) == key &&
                _uiState.value.currentChapterIndex + 1 == key.index
            ) {
                preparedNextChapter = prepared
                _uiState.update { it.copy(nextChapterPages = prepared.pages) }
            }
            prepared
        }
    }

    fun loadChapter(chapterIndex: Int, charOffset: Int = 0, openAtEnd: Boolean = false) {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        val index = chapterIndex.coerceIn(0, state.chapters.lastIndex)
        loadChapterJob?.cancel()
        paginateJob?.cancel()
        paginationGeneration++
        val generation = ++loadGeneration
        val key = preparationKey(index)

        fun publish(prepared: PreparedChapter) {
            val totalLength = chapterLength(index)
            val safeOffset = if (openAtEnd) totalLength else charOffset.coerceIn(0, totalLength)
            val pageIndex = if (prepared.pages.isEmpty()) 0
                else NativeChapterParser.pageIndexForOffset(prepared.pages, safeOffset)
            _uiState.update {
                it.copy(
                    currentChapterIndex = index,
                    elements = prepared.elements,
                    pages = prepared.pages,
                    nextChapterPages = emptyList(),
                    currentPageIndex = pageIndex,
                    currentCharOffset = safeOffset,
                    chapterProgress = if (totalLength > 0) safeOffset.toFloat() / totalLength else 0f,
                    isChapterLoading = false,
                    errorMessage = null,
                    positionRequestId = it.positionRequestId + 1
                )
            }
            preloadNextChapter()
        }

        // Completed preloads transition in one update, with no intermediate loading frame.
        preparedNextChapter?.takeIf { it.key == key }?.let {
            publish(it)
            return
        }
        val pending = preloadTask?.takeIf { preloadKey == key && !it.isCancelled }
        _uiState.update {
            it.copy(
                currentChapterIndex = index,
                currentCharOffset = charOffset.coerceAtLeast(0),
                chapterProgress = 0f,
                currentPageIndex = 0,
                pages = emptyList(),
                nextChapterPages = emptyList(),
                elements = emptyList(),
                isChapterLoading = true,
                errorMessage = null
            )
        }
        loadChapterJob = viewModelScope.launch {
            var prepared = try {
                pending?.await() ?: prepareChapter(key)
            } catch (cancelled: CancellationException) {
                // A settings change may cancel only the speculative job we were awaiting.
                // Continue the foreground load unless this load itself was cancelled.
                currentCoroutineContext().ensureActive()
                prepareChapter(preparationKey(index))
            }
            // Layout/font changes while loading must never publish outdated pagination.
            while (prepared.key != preparationKey(index)) {
                prepared = prepareChapter(preparationKey(index))
            }
            if (generation == loadGeneration) publish(prepared)
        }
    }

    fun goToChapter(chapterIndex: Int) {
        saveNow()
        loadChapter(chapterIndex, 0)
    }

    fun goToNextChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex < state.chapters.lastIndex) {
            saveNow()
            loadChapter(state.currentChapterIndex + 1, 0)
        }
    }

    fun goToPreviousChapter(openAtEnd: Boolean = false) {
        val state = _uiState.value
        if (state.currentChapterIndex > 0) {
            saveNow()
            loadChapter(state.currentChapterIndex - 1, openAtEnd = openAtEnd)
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val state = _uiState.value
        val page = state.pages.getOrNull(pageIndex) ?: return
        val totalLength = chapterLength(state.currentChapterIndex)
        val progress = if (totalLength > 0) {
            page.startCharOffset.toFloat() / totalLength
        } else 0f
        if (state.currentPageIndex == pageIndex &&
            state.currentCharOffset == page.startCharOffset
        ) {
            return
        }
        _uiState.update {
            it.copy(
                currentPageIndex = pageIndex,
                currentCharOffset = page.startCharOffset,
                chapterProgress = progress.coerceIn(0f, 1f)
            )
        }
        scheduleSave()
    }

    fun onScrollPositionChanged(charOffset: Int, chapterProgress: Float) {
        val state = _uiState.value
        if (abs(state.currentCharOffset - charOffset) < 8) return
        _uiState.update {
            it.copy(
                currentCharOffset = charOffset.coerceAtLeast(0),
                chapterProgress = chapterProgress.coerceIn(0f, 1f)
            )
        }
        scheduleSave()
    }

    fun currentElementOffsets(): List<Int> =
        elementOffsetsCache[_uiState.value.currentChapterIndex] ?: emptyList()

    fun currentChapterLength(): Int =
        chapterLengthCache[_uiState.value.currentChapterIndex] ?: 0

    fun saveNow() {
        saveJob?.cancel()
        val state = _uiState.value
        val book = state.book ?: return
        val chapterIndex = state.currentChapterIndex
        val progress = state.chapterProgress
        val offset = state.currentCharOffset
        // Repository scope keeps the write alive even while this ViewModel is being cleared.
        repository.updateReadingProgressNow(bookId, chapterIndex, progress, offset)
        _uiState.update {
            if (it.book?.id == book.id) {
                it.copy(
                    book = it.book.copy(
                        currentChapterIndex = chapterIndex,
                        currentScrollProgress = progress,
                        currentCharOffset = offset,
                        lastReadTimestamp = System.currentTimeMillis()
                    )
                )
            } else it
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            val state = _uiState.value
            if (state.book != null) {
                repository.updateReadingProgress(
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    chapterProgress = state.chapterProgress,
                    charOffset = state.currentCharOffset
                )
                _uiState.update {
                    if (it.book != null) {
                        it.copy(
                            book = it.book.copy(
                                currentChapterIndex = it.currentChapterIndex,
                                currentScrollProgress = it.chapterProgress,
                                currentCharOffset = it.currentCharOffset,
                                lastReadTimestamp = System.currentTimeMillis()
                            )
                        )
                    } else it
                }
            }
        }
    }

    private suspend fun getOrLoadElements(chapter: Chapter): List<ChapterElement> {
        elementsCache[chapter.index]?.let { return it }
        return withContext(Dispatchers.IO) {
            val html = runCatching { readChapterText(File(chapter.absolutePath)) }
                .getOrElse { "<p>Could not load this chapter.</p>" }
            val bookRoot = _uiState.value.book?.extractedDirPath
                ?.let { File(it) }
                ?: File(chapter.absolutePath).parentFile
                ?: File("/")
            val elements = NativeChapterParser.parse(html, File(chapter.absolutePath), bookRoot)
            elementsCache[chapter.index] = elements
            elementOffsetsCache[chapter.index] = NativeChapterParser.elementOffsets(elements)
            chapterLengthCache[chapter.index] = NativeChapterParser.totalTextLength(elements)
            elements
        }
    }

    private fun chapterLength(chapterIndex: Int): Int {
        return chapterLengthCache[chapterIndex]
            ?: elementsCache[chapterIndex]?.let { NativeChapterParser.totalTextLength(it) }
            ?: 0
    }

    private fun readChapterText(file: File): String {
        if (!file.exists()) return ""
        if (file.length() <= MAX_CHAPTER_BYTES) return file.readText()
        return file.inputStream().use { input ->
            val limit = minOf(MAX_CHAPTER_BYTES.toLong(), file.length()).toInt()
            val buffer = ByteArray(limit)
            var total = 0
            while (total < limit) {
                val read = input.read(buffer, total, limit - total)
                if (read <= 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
    }

    private fun repaginateCurrentChapter() {
        if (!isPaginated) return
        val state = _uiState.value
        if (state.elements.isEmpty() || !layout.isValid) return
        paginateJob?.cancel()
        val generation = ++paginationGeneration
        paginateJob = viewModelScope.launch {
            val chapterIndex = state.currentChapterIndex
            val elements = state.elements
            val pages = withContext(Dispatchers.IO) {
                NativeChapterParser.paginate(
                    elements = elements,
                    typography = typography,
                    layout = layout,
                    customTypeface = if (typography.typeface == ReaderTypeface.LITERATA) literataTypeface else null,
                    customItalicTypeface = if (typography.typeface == ReaderTypeface.LITERATA) literataItalicTypeface else null
                )
            }
            if (generation != paginationGeneration) return@launch
            val latest = _uiState.value
            if (latest.currentChapterIndex != chapterIndex) return@launch
            val safeOffset = latest.currentCharOffset.coerceAtLeast(0)
            val pageIndex = NativeChapterParser.pageIndexForOffset(pages, safeOffset)
            _uiState.update {
                if (it.currentChapterIndex != chapterIndex) {
                    it
                } else {
                    it.copy(
                        pages = pages,
                        currentPageIndex = pageIndex,
                        positionRequestId = it.positionRequestId + 1
                    )
                }
            }
        }
    }

    private suspend fun paginateIfNeeded(
        elements: List<ChapterElement>,
        chapterTypography: ReaderTypography = typography,
        chapterLayout: ReaderLayout = layout
    ): List<ChapterPage> {
        if (!chapterLayout.isValid) {
            return listOf(
                ChapterPage(
                    elements = elements,
                    startCharOffset = 0,
                    endCharOffset = NativeChapterParser.totalTextLength(elements)
                )
            )
        }
        return withContext(Dispatchers.IO) {
            NativeChapterParser.paginate(
                elements = elements,
                typography = chapterTypography,
                layout = chapterLayout,
                customTypeface = if (chapterTypography.typeface == ReaderTypeface.LITERATA) literataTypeface else null,
                customItalicTypeface = if (chapterTypography.typeface == ReaderTypeface.LITERATA) literataItalicTypeface else null
            )
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update {
                it.copy(searchQuery = query, searchResults = emptyList(), isSearching = false, searchCompleted = false)
            }
            return
        }
        _uiState.update {
            it.copy(searchQuery = query, isSearching = true, searchCompleted = false)
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            val results = mutableListOf<ReaderSearchResult>()
            withContext(Dispatchers.IO) {
                for (chapter in state.chapters) {
                    val elements = getOrLoadElements(chapter)
                    val offsets = elementOffsetsCache[chapter.index] ?: NativeChapterParser.elementOffsets(elements)
                    elements.forEachIndexed { elementIndex, element ->
                        val text = when (element) {
                            is ChapterElement.Paragraph -> element.text.text
                            is ChapterElement.Heading -> element.text.text
                            is ChapterElement.Image -> null
                        } ?: return@forEachIndexed
                        var from = 0
                        while (true) {
                            val match = text.indexOf(trimmed, from, ignoreCase = true)
                            if (match < 0) break
                            val snippetStart = (match - 45).coerceAtLeast(0)
                            val snippetEnd = (match + trimmed.length + 55).coerceAtMost(text.length)
                            val snippet = buildString {
                                if (snippetStart > 0) append("…")
                                append(text.substring(snippetStart, snippetEnd).replace('\n', ' ').trim())
                                if (snippetEnd < text.length) append("…")
                            }
                            results.add(
                                ReaderSearchResult(
                                    chapterIndex = chapter.index,
                                    chapterTitle = chapter.title,
                                    charOffset = (offsets.getOrNull(elementIndex) ?: 0) + match,
                                    snippet = snippet
                                )
                            )
                            if (results.size >= MAX_SEARCH_RESULTS) break
                            from = match + trimmed.length
                        }
                    }
                    if (results.size >= MAX_SEARCH_RESULTS) break
                }
            }
            if (!isActive) return@launch
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false,
                    searchCompleted = true
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false, searchCompleted = false)
        }
    }

    fun openSearchResult(result: ReaderSearchResult) {
        clearSearch()
        loadChapter(result.chapterIndex, result.charOffset)
    }

    fun toggleBookmark(preview: String) {
        val state = _uiState.value
        val book = state.book ?: return
        val existing = state.currentBookmark
        val updated = if (existing != null) {
            state.bookmarks.filterNot { it.id == existing.id }
        } else {
            state.bookmarks + Bookmark(
                chapterIndex = state.currentChapterIndex,
                charOffset = state.currentCharOffset,
                chapterProgress = state.chapterProgress,
                preview = preview.take(140)
            )
        }
        _uiState.update { it.copy(bookmarks = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookmarks(book.id, updated)
        }
    }

    fun removeBookmark(bookmarkId: String) {
        val state = _uiState.value
        val book = state.book ?: return
        val updated = state.bookmarks.filterNot { it.id == bookmarkId }
        _uiState.update { it.copy(bookmarks = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookmarks(book.id, updated)
        }
    }

    fun openBookmark(bookmark: Bookmark) {
        loadChapter(bookmark.chapterIndex, bookmark.charOffset)
    }

    override fun onCleared() {
        val state = _uiState.value
        if (state.book != null) {
            repository.updateReadingProgressNow(
                bookId = bookId,
                chapterIndex = state.currentChapterIndex,
                chapterProgress = state.chapterProgress,
                charOffset = state.currentCharOffset
            )
        }
        super.onCleared()
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 200
        private const val MAX_CHAPTER_BYTES = 8 * 1024 * 1024

        fun factory(context: Context, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    repository = BookRepository.getInstance(context),
                    bookId = bookId,
                    appContext = context.applicationContext
                )
            }
        }
    }
}
