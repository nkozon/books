package com.ozon.books.ui.reader

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import com.ozon.books.R
import com.ozon.books.data.BookRepository
import com.ozon.books.data.PreferencesManager
import com.ozon.books.model.Book
import com.ozon.books.model.Chapter
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.theme.LocalThemeIsDark
import com.ozon.books.ui.theme.LocalThemeIsOled
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ChapterElement {
    data class Paragraph(
        val text: AnnotatedString,
        val isFirstLineIndented: Boolean = true
    ) : ChapterElement()
    data class Heading(val text: AnnotatedString, val level: Int) : ChapterElement()
    data class Image(val absolutePath: String) : ChapterElement()
}

data class ChapterPage(val elements: List<ChapterElement>)

data class BookPage(
    val chapterIndex: Int,
    val pageIndexInChapter: Int,
    val totalPagesInChapter: Int,
    val elements: List<ChapterElement>
)

enum class ReaderBgTheme(val id: String, val label: String) {
    SYSTEM("system", "Auto"),
    WHITE("white", "White"),
    SEPIA("sepia", "Sepia"),
    DARK("dark", "Black");

    companion object {
        fun fromId(id: String): ReaderBgTheme = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

// Pre-allocated static shapes for fast TOC rendering without per-item animation overhead
private val TocSingleShape = RoundedCornerShape(16.dp)
private val TocTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val TocBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val TocMiddleShape = RoundedCornerShape(4.dp)

val LiterataFontFamily = FontFamily(
    Font(resId = R.font.literata, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resId = R.font.literata, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(resId = R.font.literata_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resId = R.font.literata_italic, weight = FontWeight.Bold, style = FontStyle.Italic)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repository = remember { BookRepository.getInstance(context) }
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val readerPrefs = remember { context.getSharedPreferences("reader_extra_prefs", Context.MODE_PRIVATE) }

    val isDark = LocalThemeIsDark.current
    val isOled = LocalThemeIsOled.current
    val fontFamily by preferencesManager.fontFamily.collectAsState()
    val useLiterata by preferencesManager.useLiterata.collectAsState()
    val savedFontSize by preferencesManager.fontSizePx.collectAsState()
    val lineHeightMultiplier by preferencesManager.lineHeightMultiplier.collectAsState()
    val isJustified by preferencesManager.isJustified.collectAsState()
    val isPaginated by preferencesManager.isPaginated.collectAsState()

    val literataTypeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.literata)
        } catch (_: Exception) {
            null
        }
    }

    var readerBgTheme by remember {
        mutableStateOf(
            ReaderBgTheme.fromId(readerPrefs.getString("reader_bg_theme", ReaderBgTheme.SYSTEM.id) ?: ReaderBgTheme.SYSTEM.id)
        )
    }

    fun updateReaderBgTheme(theme: ReaderBgTheme) {
        readerBgTheme = theme
        readerPrefs.edit().putString("reader_bg_theme", theme.id).apply()
    }

    // Determine whether the active reader background is light (White or Sepia) or dark (Black)
    val isLightReaderBg = when (readerBgTheme) {
        ReaderBgTheme.WHITE, ReaderBgTheme.SEPIA -> true
        ReaderBgTheme.DARK -> false
        ReaderBgTheme.SYSTEM -> !isDark
    }

    // Dynamically update status bar & navigation bar icon colors (black icons on White/Sepia, white icons on Black)
    DisposableEffect(isLightReaderBg, isDark) {
        val window = (context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.isAppearanceLightStatusBars = isLightReaderBg
        insetsController?.isAppearanceLightNavigationBars = isLightReaderBg

        onDispose {
            insetsController?.isAppearanceLightStatusBars = !isDark
            insetsController?.isAppearanceLightNavigationBars = !isDark
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val densityScale = density.density
    val fontScale = density.fontScale

    val topStatusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Page text starts consistently below the status bar and stops above the bottom counter
    val pageTopPadding = topStatusPadding + 12.dp
    val pageBottomPadding = bottomNavPadding + 22.dp
    val pageHorizontalPadding = 24.dp

    var contentWidthDp by remember { mutableIntStateOf((configuration.screenWidthDp - 48).coerceAtLeast(200)) }
    var contentHeightDp by remember { mutableIntStateOf((configuration.screenHeightDp - 60).coerceAtLeast(300)) }

    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var fontSizePx by remember { mutableIntStateOf(savedFontSize) }

    var showControls by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val rawElementsMap = remember { mutableMapOf<Int, List<ChapterElement>>() }
    var allBookPages by remember { mutableStateOf<List<BookPage>>(emptyList()) }
    var hasInitializedScroll by remember { mutableStateOf(false) }

    var targetPageIndex by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(savedFontSize) {
        fontSizePx = savedFontSize
    }

    LaunchedEffect(bookId) {
        val loadedBook = repository.getBook(bookId)
        if (loadedBook != null) {
            book = loadedBook
            currentChapterIndex = loadedBook.currentChapterIndex
            chapters = repository.getChapters(loadedBook)
        }
    }

    // Parse and paginate all chapters into a unified page list across the entire book
    LaunchedEffect(chapters, fontSizePx, lineHeightMultiplier, fontFamily, useLiterata, fontScale, densityScale, contentWidthDp, contentHeightDp) {
        if (chapters.isEmpty()) return@LaunchedEffect
        val pagesList = withContext(Dispatchers.IO) {
            for (ch in chapters) {
                if (!rawElementsMap.containsKey(ch.index)) {
                    val html = try {
                        File(ch.absolutePath).readText()
                    } catch (_: Exception) {
                        "<p>Error loading chapter content.</p>"
                    }
                    val elems = NativeChapterParser.parse(html, File(ch.absolutePath))
                    rawElementsMap[ch.index] = elems
                }
            }
            val list = mutableListOf<BookPage>()
            val isSerif = fontFamily.equals("Serif", ignoreCase = true) || fontFamily.equals("Literata", ignoreCase = true)
            val customTypeface = if (useLiterata && isSerif) literataTypeface else null
            for (ch in chapters) {
                val elems = rawElementsMap[ch.index] ?: emptyList()
                val chPages = NativeChapterParser.paginate(
                    elements = elems,
                    fontSizePx = fontSizePx,
                    lineHeightMultiplier = lineHeightMultiplier,
                    fontFamilyName = fontFamily,
                    density = densityScale,
                    fontScale = fontScale,
                    contentWidthDp = contentWidthDp,
                    contentHeightDp = contentHeightDp,
                    customTypeface = customTypeface
                )
                val totalInCh = chPages.size
                for ((pIdx, page) in chPages.withIndex()) {
                    list.add(
                        BookPage(
                            chapterIndex = ch.index,
                            pageIndexInChapter = pIdx,
                            totalPagesInChapter = totalInCh,
                            elements = page.elements
                        )
                    )
                }
            }
            list
        }
        allBookPages = pagesList
        isLoading = false
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { allBookPages.size.coerceAtLeast(1) }
    )

    val listState = rememberLazyListState()

    // Initialize scroll position to book's saved chapter on first load
    LaunchedEffect(allBookPages.size) {
        if (!hasInitializedScroll && allBookPages.isNotEmpty() && book != null) {
            val initialCh = book!!.currentChapterIndex
            val target = allBookPages.indexOfFirst { it.chapterIndex == initialCh }.coerceAtLeast(0)
            pagerState.scrollToPage(target)
            targetPageIndex = target
            hasInitializedScroll = true
        } else if (hasInitializedScroll && allBookPages.isNotEmpty() && pagerState.currentPage >= allBookPages.size) {
            val target = allBookPages.lastIndex
            pagerState.scrollToPage(target)
            targetPageIndex = target
        }
    }

    // Synchronize targetPageIndex when user is not spam-tapping
    LaunchedEffect(pagerState.currentPage) {
        if (System.currentTimeMillis() - lastTapTime >= 400L) {
            targetPageIndex = pagerState.currentPage
        }
    }

    val currentPage = allBookPages.getOrNull(pagerState.currentPage)
    val activeChapterIndex = currentPage?.chapterIndex ?: currentChapterIndex
    val currentChapter = chapters.getOrNull(activeChapterIndex)

    // Save reading progress when currentPage changes
    LaunchedEffect(pagerState.currentPage, allBookPages.size) {
        val page = currentPage ?: return@LaunchedEffect
        currentChapterIndex = page.chapterIndex
        val progress = if (allBookPages.isNotEmpty()) {
            pagerState.currentPage.toFloat() / allBookPages.size.toFloat()
        } else 0f
        repository.updateReadingProgress(bookId, page.chapterIndex, progress)
    }

    fun nextPage() {
        if (allBookPages.isEmpty()) return
        val now = System.currentTimeMillis()
        val isSpamming = (now - lastTapTime) < 400L
        lastTapTime = now

        val base = maxOf(pagerState.currentPage, targetPageIndex)
        val target = (base + 1).coerceAtMost(allBookPages.lastIndex)

        if (target != pagerState.currentPage || target != targetPageIndex) {
            targetPageIndex = target
            scrollJob?.cancel()
            scrollJob = coroutineScope.launch {
                if (isSpamming) {
                    pagerState.scrollToPage(target)
                } else {
                    pagerState.animateScrollToPage(
                        page = target,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    fun prevPage() {
        if (allBookPages.isEmpty()) return
        val now = System.currentTimeMillis()
        val isSpamming = (now - lastTapTime) < 400L
        lastTapTime = now

        val base = minOf(pagerState.currentPage, targetPageIndex)
        val target = (base - 1).coerceAtLeast(0)

        if (target != pagerState.currentPage || target != targetPageIndex) {
            targetPageIndex = target
            scrollJob?.cancel()
            scrollJob = coroutineScope.launch {
                if (isSpamming) {
                    pagerState.scrollToPage(target)
                } else {
                    pagerState.animateScrollToPage(
                        page = target,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    fun changeChapter(newIndex: Int, startAtLastPage: Boolean = false) {
        if (newIndex !in chapters.indices) return
        currentChapterIndex = newIndex
        if (isPaginated) {
            val target = if (startAtLastPage) {
                allBookPages.indexOfLast { it.chapterIndex == newIndex }
            } else {
                allBookPages.indexOfFirst { it.chapterIndex == newIndex }
            }
            if (target != -1) {
                targetPageIndex = target
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    pagerState.scrollToPage(target)
                }
            }
        } else {
            coroutineScope.launch {
                listState.scrollToItem(0)
            }
            val progress = if (chapters.isNotEmpty()) {
                newIndex.toFloat() / chapters.size.toFloat()
            } else 0f
            coroutineScope.launch {
                repository.updateReadingProgress(bookId, newIndex, progress)
            }
        }
    }

    BackHandler {
        onNavigateBack()
    }

    if (isLoading || book == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val isSerif = fontFamily.equals("Serif", ignoreCase = true) || fontFamily.equals("Literata", ignoreCase = true)
    val resolvedFontFamily = when {
        isSerif -> if (useLiterata) LiterataFontFamily else FontFamily.Serif
        fontFamily.equals("Monospace", ignoreCase = true) || fontFamily.equals("Mono", ignoreCase = true) -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    // Dynamic Auto background: uses MaterialTheme.colorScheme.background darkened slightly to minimize eye strain
    val dynamicAutoBgColor = if (isDark) {
        if (isOled) Color.Black else lerp(MaterialTheme.colorScheme.background, Color.Black, 0.22f)
    } else {
        lerp(MaterialTheme.colorScheme.background, Color.Black, 0.06f)
    }

    val dynamicAutoTextColor = if (isDark) {
        lerp(MaterialTheme.colorScheme.onBackground, Color.Black, 0.08f)
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val backgroundColor = when (readerBgTheme) {
        ReaderBgTheme.WHITE -> Color(0xFFFFFFFF)
        ReaderBgTheme.SEPIA -> Color(0xFFF4ECD8)
        ReaderBgTheme.DARK -> Color(0xFF000000)
        ReaderBgTheme.SYSTEM -> dynamicAutoBgColor
    }

    val textColor = when (readerBgTheme) {
        ReaderBgTheme.WHITE -> Color(0xFF1D1B20)
        ReaderBgTheme.SEPIA -> Color(0xFF3E3224)
        ReaderBgTheme.DARK -> Color(0xFFE6E0E9)
        ReaderBgTheme.SYSTEM -> dynamicAutoTextColor
    }

    val controlSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    val controlContentColor = MaterialTheme.colorScheme.onSurface

    val overallBookProgress = if (isPaginated) {
        if (allBookPages.size > 1) {
            (pagerState.currentPage.toFloat() / (allBookPages.size - 1).toFloat()).coerceIn(0f, 1f)
        } else 1f
    } else {
        if (chapters.isNotEmpty()) {
            ((activeChapterIndex + 1).toFloat() / chapters.size.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val measuredWidthDp = maxWidth.value.toInt()
        val measuredHeightDp = maxHeight.value.toInt()
        val actualContentWidth = (measuredWidthDp - (pageHorizontalPadding * 2).value.toInt()).coerceAtLeast(200)
        val actualContentHeight = (measuredHeightDp - (pageTopPadding + pageBottomPadding).value.toInt()).coerceAtLeast(300)

        LaunchedEffect(actualContentWidth, actualContentHeight) {
            if (contentWidthDp != actualContentWidth || contentHeightDp != actualContentHeight) {
                contentWidthDp = actualContentWidth
                contentHeightDp = actualContentHeight
            }
        }

        // Native Content: Paged view or Continuous Scroll view
        if (allBookPages.isEmpty() && isPaginated) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No content available in this book.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        } else if (isPaginated) {
            // Paginated View: Native HorizontalPager across all book pages
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allBookPages.size) {
                        val width = size.width
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            var isDrag = false
                            var upChange: PointerInputChange? = null

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    upChange = change
                                    break
                                }

                                val totalDistance = (change.position - downPos).getDistance()
                                if (totalDistance > touchSlop) {
                                    isDrag = true
                                }
                            }

                            if (!isDrag && upChange != null) {
                                upChange.consume()
                                val ratio = downPos.x / width.toFloat()
                                when {
                                    ratio < 0.22f -> prevPage()
                                    ratio > 0.78f -> nextPage()
                                    else -> showControls = !showControls
                                }
                            }
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top
                ) { pageIndex ->
                    val page = allBookPages.getOrNull(pageIndex)
                    if (page != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = pageHorizontalPadding)
                                .padding(top = pageTopPadding, bottom = pageBottomPadding),
                            // Alignment.Top guarantees the first line of every page starts at the exact same height
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                            horizontalAlignment = Alignment.Start
                        ) {
                            page.elements.forEach { element ->
                                ChapterElementItem(
                                    element = element,
                                    fontSizePx = fontSizePx,
                                    lineHeightMultiplier = lineHeightMultiplier,
                                    isJustified = isJustified,
                                    fontFamily = resolvedFontFamily,
                                    textColor = textColor
                                )
                            }
                        }
                    }
                }

                // Subtle page counter when controls are hidden
                if (!showControls) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomNavPadding + 2.dp)
                    ) {
                        Text(
                            text = "${(currentPage?.pageIndexInChapter ?: 0) + 1} / ${currentPage?.totalPagesInChapter ?: 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = textColor.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        } else {
            // Scroll View: Native LazyColumn with edge tapping and smooth scrolling
            val currentChapterElements = rawElementsMap[activeChapterIndex] ?: emptyList()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val width = size.width
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            var isDrag = false
                            var upChange: PointerInputChange? = null

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    upChange = change
                                    break
                                }
                                if ((change.position - downPos).getDistance() > touchSlop) {
                                    isDrag = true
                                }
                            }

                            if (!isDrag && upChange != null) {
                                upChange.consume()
                                val ratio = downPos.x / width.toFloat()
                                when {
                                    ratio < 0.22f -> {
                                        coroutineScope.launch {
                                            if (listState.isScrollInProgress) {
                                                listState.scrollBy(-550f)
                                            } else {
                                                listState.animateScrollBy(-550f)
                                            }
                                        }
                                    }
                                    ratio > 0.78f -> {
                                        coroutineScope.launch {
                                            if (listState.isScrollInProgress) {
                                                listState.scrollBy(550f)
                                            } else {
                                                listState.animateScrollBy(550f)
                                            }
                                        }
                                    }
                                    else -> {
                                        showControls = !showControls
                                    }
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start,
                    contentPadding = PaddingValues(
                        start = pageHorizontalPadding,
                        end = pageHorizontalPadding,
                        top = pageTopPadding,
                        bottom = pageBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentChapterElements) { element ->
                        ChapterElementItem(
                            element = element,
                            fontSizePx = fontSizePx,
                            lineHeightMultiplier = lineHeightMultiplier,
                            isJustified = isJustified,
                            fontFamily = resolvedFontFamily,
                            textColor = textColor
                        )
                    }
                }
            }
        }

        // Top App Bar Overlay with smooth gradient fading into the book background color
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to backgroundColor,
                            0.62f to backgroundColor.copy(alpha = 0.92f),
                            0.85f to backgroundColor.copy(alpha = 0.55f),
                            1.0f to backgroundColor.copy(alpha = 0f)
                        )
                    )
                    .padding(bottom = 24.dp)
            ) {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = book!!.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChapter?.title ?: "Chapter ${activeChapterIndex + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = textColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            CircleIconButton(
                                onClick = onNavigateBack,
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                containerColor = controlSurfaceColor,
                                contentColor = controlContentColor
                            )
                        }
                    },
                    actions = {
                        // Pill-shaped background surface grouping Table of Contents + Settings buttons
                        Surface(
                            shape = CircleShape,
                            color = controlSurfaceColor,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                CircleIconButton(
                                    onClick = { showTocSheet = true },
                                    icon = Icons.Rounded.FormatListNumbered,
                                    contentDescription = "Table of Contents",
                                    containerColor = Color.Transparent,
                                    contentColor = controlContentColor
                                )

                                // Settings Button with Anchored Top-Right Popup Menu
                                Box {
                                    CircleIconButton(
                                        onClick = { showSettingsPopup = true },
                                        icon = Icons.Rounded.Settings,
                                        contentDescription = "Reader Settings",
                                        containerColor = Color.Transparent,
                                        contentColor = controlContentColor
                                    )

                                    DropdownMenu(
                                        expanded = showSettingsPopup,
                                        onDismissRequest = { showSettingsPopup = false },
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.width(320.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(
                                                text = "Reader Settings",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                            // 1. Reading Mode (Pages / Scroll)
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Layout",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    DynamicSegmentedButton(
                                                        text = "Pages",
                                                        isSelected = isPaginated,
                                                        index = 0,
                                                        count = 2,
                                                        onClick = { preferencesManager.setIsPaginated(true) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    DynamicSegmentedButton(
                                                        text = "Scroll",
                                                        isSelected = !isPaginated,
                                                        index = 1,
                                                        count = 2,
                                                        onClick = { preferencesManager.setIsPaginated(false) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }

                                            // 2. Background Theme (Auto / White / Sepia / Black)
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Background",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    ReaderThemeSwatch(
                                                        label = "Auto",
                                                        swatchColor = dynamicAutoBgColor,
                                                        contentColor = dynamicAutoTextColor,
                                                        isSelected = readerBgTheme == ReaderBgTheme.SYSTEM,
                                                        onClick = { updateReaderBgTheme(ReaderBgTheme.SYSTEM) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    ReaderThemeSwatch(
                                                        label = "White",
                                                        swatchColor = Color(0xFFFFFFFF),
                                                        contentColor = Color(0xFF1D1B20),
                                                        isSelected = readerBgTheme == ReaderBgTheme.WHITE,
                                                        onClick = { updateReaderBgTheme(ReaderBgTheme.WHITE) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    ReaderThemeSwatch(
                                                        label = "Sepia",
                                                        swatchColor = Color(0xFFF4ECD8),
                                                        contentColor = Color(0xFF3E3224),
                                                        isSelected = readerBgTheme == ReaderBgTheme.SEPIA,
                                                        onClick = { updateReaderBgTheme(ReaderBgTheme.SEPIA) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    ReaderThemeSwatch(
                                                        label = "Black",
                                                        swatchColor = Color(0xFF000000),
                                                        contentColor = Color(0xFFE6E0E9),
                                                        isSelected = readerBgTheme == ReaderBgTheme.DARK,
                                                        onClick = { updateReaderBgTheme(ReaderBgTheme.DARK) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }

                                            // 3. Font Size & Line Height Side-by-Side
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Font Size
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Font Size",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    DynamicStepper(
                                                        valueText = "$fontSizePx px",
                                                        onDecrease = {
                                                            fontSizePx = (fontSizePx - 1).coerceAtLeast(10)
                                                            preferencesManager.setFontSizePx(fontSizePx)
                                                        },
                                                        onIncrease = {
                                                            fontSizePx = (fontSizePx + 1).coerceAtMost(22)
                                                            preferencesManager.setFontSizePx(fontSizePx)
                                                        },
                                                        canDecrease = fontSizePx > 10,
                                                        canIncrease = fontSizePx < 22
                                                    )
                                                }

                                                // Line Height
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Line Height",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    DynamicStepper(
                                                        valueText = String.format(Locale.US, "%.1fx", lineHeightMultiplier),
                                                        onDecrease = {
                                                            preferencesManager.setLineHeightMultiplier(lineHeightMultiplier - 0.1f)
                                                        },
                                                        onIncrease = {
                                                            preferencesManager.setLineHeightMultiplier(lineHeightMultiplier + 0.1f)
                                                        },
                                                        canDecrease = lineHeightMultiplier > 1.25f,
                                                        canIncrease = lineHeightMultiplier < 1.95f
                                                    )
                                                }
                                            }

                                            // 4. Justify Text Toggle
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { preferencesManager.setIsJustified(!isJustified) }
                                                    .background(
                                                        if (isJustified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isJustified) Icons.Rounded.FormatAlignJustify else Icons.AutoMirrored.Rounded.FormatAlignLeft,
                                                        contentDescription = null,
                                                        tint = if (isJustified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Justify text",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Switch(
                                                    checked = isJustified,
                                                    onCheckedChange = { preferencesManager.setIsJustified(it) },
                                                    thumbContent = if (isJustified) {
                                                        {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                                            )
                                                        }
                                                    } else null
                                                )
                                            }

                                            // 5. Use Literata Toggle
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { preferencesManager.setUseLiterata(!useLiterata) }
                                                    .background(
                                                        if (useLiterata) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Book,
                                                        contentDescription = null,
                                                        tint = if (useLiterata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Use Literata",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Switch(
                                                    checked = useLiterata,
                                                    onCheckedChange = { preferencesManager.setUseLiterata(it) },
                                                    thumbContent = if (useLiterata) {
                                                        {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                                            )
                                                        }
                                                    } else null
                                                )
                                            }

                                            // 6. Typeface
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Typeface",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val serifLabel = if (useLiterata) "Literata" else "Serif"
                                                    val families = listOf(serifLabel, "Sans", "Mono")
                                                    families.forEachIndexed { idx, familyName ->
                                                        val target = when (familyName) {
                                                            "Sans" -> "Sans-Serif"
                                                            "Mono" -> "Monospace"
                                                            else -> "Serif"
                                                        }
                                                        val isSelected = when (target) {
                                                            "Sans-Serif" -> fontFamily.equals("Sans-Serif", ignoreCase = true) || fontFamily.equals("Sans", ignoreCase = true)
                                                            "Monospace" -> fontFamily.equals("Monospace", ignoreCase = true) || fontFamily.equals("Mono", ignoreCase = true)
                                                            else -> isSerif
                                                        }

                                                        DynamicSegmentedButton(
                                                            text = familyName,
                                                            isSelected = isSelected,
                                                            index = idx,
                                                            count = families.size,
                                                            onClick = { preferencesManager.setFontFamily(target) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }

        // Bottom Controls Overlay with smooth gradient fading into the book background color
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to backgroundColor.copy(alpha = 0f),
                            0.18f to backgroundColor.copy(alpha = 0.55f),
                            0.42f to backgroundColor.copy(alpha = 0.92f),
                            1.0f to backgroundColor
                        )
                    )
                    .padding(
                        top = 24.dp,
                        bottom = bottomNavPadding + 12.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        onClick = {
                            if (isPaginated) {
                                prevPage()
                            } else {
                                changeChapter(activeChapterIndex - 1)
                            }
                        },
                        enabled = if (isPaginated) pagerState.currentPage > 0 else activeChapterIndex > 0,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = if (isPaginated) "Previous Page" else "Previous Chapter",
                        containerColor = controlSurfaceColor,
                        contentColor = controlContentColor
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        val chNum = activeChapterIndex + 1
                        val statusLabel = if (isPaginated) {
                            val pageInCh = (currentPage?.pageIndexInChapter ?: 0) + 1
                            val totalInCh = currentPage?.totalPagesInChapter ?: 1
                            "Chapter $chNum · Page $pageInCh of $totalInCh"
                        } else {
                            val pct = (overallBookProgress * 100).toInt()
                            "Chapter $chNum of ${chapters.size} · $pct%"
                        }

                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        LinearProgressIndicator(
                            progress = { overallBookProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = textColor.copy(alpha = 0.20f),
                            strokeCap = StrokeCap.Round
                        )
                    }

                    CircleIconButton(
                        onClick = {
                            if (isPaginated) {
                                nextPage()
                            } else {
                                changeChapter(activeChapterIndex + 1)
                            }
                        },
                        enabled = if (isPaginated) pagerState.currentPage < allBookPages.lastIndex else activeChapterIndex < chapters.lastIndex,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = if (isPaginated) "Next Page" else "Next Chapter",
                        containerColor = controlSurfaceColor,
                        contentColor = controlContentColor
                    )
                }
            }
        }
    }

    // Table of contents Bottom Sheet (Fast & Tall)
    if (showTocSheet) {
        val tocSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val tocListState = rememberLazyListState(
            initialFirstVisibleItemIndex = (activeChapterIndex - 2).coerceAtLeast(0)
        )

        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = tocSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Table of Contents",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                LazyColumn(
                    state = tocListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    val totalChapters = chapters.size
                    itemsIndexed(
                        items = chapters,
                        key = { index, _ -> index }
                    ) { index, ch ->
                        val isSelected = index == activeChapterIndex
                        val itemShape = when {
                            totalChapters == 1 -> TocSingleShape
                            index == 0 -> TocTopShape
                            index == totalChapters - 1 -> TocBottomShape
                            else -> TocMiddleShape
                        }

                        Surface(
                            onClick = {
                                changeChapter(index)
                                showTocSheet = false
                            },
                            shape = itemShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = ch.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Current chapter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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

@Composable
private fun ReaderThemeSwatch(
    label: String,
    swatchColor: Color,
    contentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = swatchColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor
            )
        }
    }
}

@Composable
private fun DynamicSegmentedButton(
    text: String,
    isSelected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outerRadius = 18.dp
    val innerRadius = 6.dp
    val selectedRadius = 16.dp

    val topStart by animateDpAsState(
        targetValue = if (isSelected) selectedRadius else if (index == 0) outerRadius else innerRadius,
        label = "topStart"
    )
    val bottomStart by animateDpAsState(
        targetValue = if (isSelected) selectedRadius else if (index == 0) outerRadius else innerRadius,
        label = "bottomStart"
    )
    val topEnd by animateDpAsState(
        targetValue = if (isSelected) selectedRadius else if (index == count - 1) outerRadius else innerRadius,
        label = "topEnd"
    )
    val bottomEnd by animateDpAsState(
        targetValue = if (isSelected) selectedRadius else if (index == count - 1) outerRadius else innerRadius,
        label = "bottomEnd"
    )

    val shape = RoundedCornerShape(
        topStart = topStart,
        bottomStart = bottomStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DynamicStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onDecrease,
            enabled = canDecrease,
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            color = if (canDecrease) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (canDecrease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        Surface(
            onClick = onIncrease,
            enabled = canIncrease,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            color = if (canIncrease) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (canIncrease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChapterElementItem(
    element: ChapterElement,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    isJustified: Boolean,
    fontFamily: FontFamily,
    textColor: Color
) {
    when (element) {
        is ChapterElement.Heading -> {
            val headingFontSize = when (element.level) {
                1 -> (fontSizePx * 1.55f).sp   // Main Chapter Title (h1)
                2 -> (fontSizePx * 1.30f).sp   // Subtitle / Section Heading (h2)
                else -> (fontSizePx * 1.15f).sp // Sub-heading (h3+)
            }
            val headingLineHeight = headingFontSize * 1.3f

            Text(
                text = element.text,
                style = TextStyle(
                    fontSize = headingFontSize,
                    lineHeight = headingLineHeight,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (element.level == 1) 12.dp else 6.dp,
                        bottom = 4.dp
                    )
            )
        }
        is ChapterElement.Paragraph -> {
            Text(
                text = element.text,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = fontSizePx.sp,
                    lineHeight = (fontSizePx * lineHeightMultiplier).sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = textColor,
                    textAlign = if (isJustified) TextAlign.Justify else TextAlign.Start,
                    textIndent = if (element.isFirstLineIndented) {
                        TextIndent(firstLine = (fontSizePx * 1.5f).sp)
                    } else {
                        null
                    },
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                )
            )
        }
        is ChapterElement.Image -> {
            NativeBookImage(
                imagePath = element.absolutePath,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NativeBookImage(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imagePath) {
        try {
            val file = File(imagePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

object NativeChapterParser {

    private val HEAD_STYLE_SCRIPT_REGEX = "(?is)<(head|style|script)[^>]*>.*?</\\1>".toRegex()
    private val IMG_REGEX = "(?i)<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>".toRegex()
    private val HEADING_OPEN_REGEX = "(?i)<h([1-6])[^>]*>".toRegex()
    private val HEADING_CLOSE_REGEX = "(?i)</h[1-6]>".toRegex()
    private val BLOCK_OPEN_REGEX = "(?i)<(p|div|blockquote|li|section|article)[^>]*>".toRegex()
    private val BLOCK_CLOSE_REGEX = "(?i)</(p|div|blockquote|li|section|article)>".toRegex()
    private val BR_REGEX = "(?i)<br\\s*/?>".toRegex()

    private val BLOCK_SPLIT_REGEX = Regex("\\s*###BLOCK_BREAK###\\s*")
    private val IMG_MARKER_REGEX = "###IMG_MARKER###(.*?)###END_MARKER###".toRegex()
    private val H_MARKER_REGEX = "^###H([1-6])_START###([\\s\\S]*)$".toRegex()

    fun parse(rawHtml: String, chapterFile: File): List<ChapterElement> {
        if (rawHtml.isBlank()) return emptyList()

        val elements = mutableListOf<ChapterElement>()
        val baseDir = chapterFile.parentFile ?: File("/")

        // 1. Strip <head>, <style>, and <script> so metadata/CSS doesn't render as body text
        var processedHtml = rawHtml.replace(HEAD_STYLE_SCRIPT_REGEX, "")

        // 2. Inject explicit block & heading markers so we don't rely on HtmlCompat's newline quirks
        processedHtml = processedHtml
            .replace(IMG_REGEX) { match ->
                val src = match.groupValues[1]
                "###BLOCK_BREAK######IMG_MARKER###$src###END_MARKER######BLOCK_BREAK###"
            }
            .replace(HEADING_OPEN_REGEX) { match ->
                val level = match.groupValues[1]
                "###BLOCK_BREAK######H${level}_START###"
            }
            .replace(HEADING_CLOSE_REGEX, "###BLOCK_BREAK###")
            .replace(BLOCK_OPEN_REGEX, "###BLOCK_BREAK###")
            .replace(BLOCK_CLOSE_REGEX, "###BLOCK_BREAK###")
            .replace(BR_REGEX, "\n")

        val spanned: Spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val fullText = spanned.toString()

        var searchIndex = 0
        val rawBlocks = fullText.split(BLOCK_SPLIT_REGEX)

        for (rawBlock in rawBlocks) {
            if (rawBlock.isEmpty()) continue

            val blockStart = fullText.indexOf(rawBlock, searchIndex).takeIf { it != -1 } ?: searchIndex
            val blockEnd = blockStart + rawBlock.length
            searchIndex = blockEnd

            val trimmed = rawBlock.trim()
            if (trimmed.isEmpty()) continue

            // Check for Image marker
            val imgMatch = IMG_MARKER_REGEX.find(trimmed)
            if (imgMatch != null) {
                val relPath = imgMatch.groupValues[1]
                val decodedPath = try { URLDecoder.decode(relPath, "UTF-8") } catch (_: Exception) { relPath }
                val imageFile = File(baseDir, decodedPath).canonicalFile
                if (imageFile.exists()) {
                    elements.add(ChapterElement.Image(imageFile.absolutePath))
                }
                continue
            }

            // Check for explicit Heading marker (###H1_START### ... ###H6_START###)
            val hMatch = H_MARKER_REGEX.find(trimmed)
            if (hMatch != null) {
                val level = hMatch.groupValues[1].toIntOrNull() ?: 2
                val markerPrefix = "###H${level}_START###"
                val contentStartInRaw = rawBlock.indexOf(markerPrefix) + markerPrefix.length
                val actualStart = blockStart + contentStartInRaw

                val annotated = sliceSpannedToAnnotated(spanned, actualStart, blockEnd)
                if (annotated.text.isNotBlank()) {
                    elements.add(ChapterElement.Heading(annotated, level = level.coerceIn(1, 3)))
                }
                continue
            }

            // Standard paragraph or fallback styled heading (e.g., <p class="title"><b>Title</b></p>)
            val annotated = sliceSpannedToAnnotated(spanned, blockStart, blockEnd)
            if (annotated.text.isBlank()) continue

            val fallbackHeadingLevel = detectFallbackHeadingLevel(spanned, blockStart, blockEnd, annotated.text)
            if (fallbackHeadingLevel != null) {
                elements.add(ChapterElement.Heading(annotated, level = fallbackHeadingLevel))
            } else {
                elements.add(ChapterElement.Paragraph(annotated))
            }
        }

        return elements
    }

    /**
     * Accurately slices [spanned] between [start] and [end], trimming leading/trailing
     * whitespace WITHOUT desynchronizing span indices.
     */
    private fun sliceSpannedToAnnotated(spanned: Spanned, start: Int, end: Int): AnnotatedString {
        var trimmedStart = start
        var trimmedEnd = end

        while (trimmedStart < trimmedEnd && spanned[trimmedStart].isWhitespace()) {
            trimmedStart++
        }
        while (trimmedEnd > trimmedStart && spanned[trimmedEnd - 1].isWhitespace()) {
            trimmedEnd--
        }

        if (trimmedStart >= trimmedEnd) return AnnotatedString("")

        val rawSlice = spanned.subSequence(trimmedStart, trimmedEnd).toString()
        val builder = AnnotatedString.Builder(rawSlice)

        val spans = spanned.getSpans(trimmedStart, trimmedEnd, Any::class.java)
        for (span in spans) {
            val sStart = (spanned.getSpanStart(span) - trimmedStart).coerceIn(0, rawSlice.length)
            val sEnd = (spanned.getSpanEnd(span) - trimmedStart).coerceIn(0, rawSlice.length)
            if (sStart < sEnd) {
                when (span) {
                    is android.text.style.StyleSpan -> {
                        when (span.style) {
                            Typeface.BOLD ->
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), sStart, sEnd)
                            Typeface.ITALIC ->
                                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), sStart, sEnd)
                            Typeface.BOLD_ITALIC ->
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), sStart, sEnd)
                        }
                    }
                    is android.text.style.UnderlineSpan -> {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), sStart, sEnd)
                    }
                    is android.text.style.StrikethroughSpan -> {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), sStart, sEnd)
                    }
                }
            }
        }
        return builder.toAnnotatedString()
    }

    /**
     * Catches EPUBs that use <p><b>Chapter 1</b></p> instead of <h1>/<h2> tags.
     */
    private fun detectFallbackHeadingLevel(spanned: Spanned, start: Int, end: Int, text: String): Int? {
        if (text.length > 90 || text.contains('\n')) return null

        val relSpans = spanned.getSpans(start, end, android.text.style.RelativeSizeSpan::class.java)
        val maxRel = relSpans.maxOfOrNull { it.sizeChange }
        if (maxRel != null) {
            if (maxRel >= 1.4f) return 1
            if (maxRel >= 1.15f) return 2
        }

        val styleSpans = spanned.getSpans(start, end, android.text.style.StyleSpan::class.java)
        val isAllBold = styleSpans.any {
            (it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC) &&
                    spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end
        }
        return if (isAllBold && text.length < 70 && !text.endsWith(".")) 3 else null
    }

    /**
     * Paginates [elements] using Android's native [StaticLayout] with a safety buffer
     * so that bottom lines never overlap or get cut off.
     */
    fun paginate(
        elements: List<ChapterElement>,
        fontSizePx: Int,
        lineHeightMultiplier: Float,
        fontFamilyName: String,
        density: Float,
        fontScale: Float,
        contentWidthDp: Int,
        contentHeightDp: Int,
        customTypeface: Typeface? = null
    ): List<ChapterPage> {
        if (elements.isEmpty()) return listOf(ChapterPage(emptyList()))

        // Subtract 16dp safety buffer so bottom lines never touch or overflow the screen boundary
        val availableHeightPx = ((contentHeightDp - 16).coerceAtLeast(280) * density)
        val availableWidthPx = (contentWidthDp.coerceAtLeast(200) * density).toInt().coerceAtLeast(100)

        val bodyTextSizePx = fontSizePx * density * fontScale
        val bodyLineHeightPx = bodyTextSizePx * lineHeightMultiplier
        val paragraphSpacingPx = 8f * density
        val paragraphIndentPx = (fontSizePx * 1.5f) * density * fontScale

        val baseTypeface = when {
            customTypeface != null -> customTypeface
            fontFamilyName.equals("Monospace", ignoreCase = true) || fontFamilyName.equals("Mono", ignoreCase = true) -> Typeface.MONOSPACE
            fontFamilyName.equals("Serif", ignoreCase = true) -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }

        val bodyPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = bodyTextSizePx
            typeface = baseTypeface
            letterSpacing = 0f
        }

        val pages = mutableListOf<ChapterPage>()
        var currentPageElements = mutableListOf<ChapterElement>()
        var currentHeightPx = 0f

        fun commitPage() {
            if (currentPageElements.isNotEmpty()) {
                pages.add(ChapterPage(currentPageElements))
                currentPageElements = mutableListOf()
                currentHeightPx = 0f
            }
        }

        for (element in elements) {
            when (element) {
                is ChapterElement.Heading -> {
                    val headingScale = when (element.level) {
                        1 -> 1.55f
                        2 -> 1.30f
                        else -> 1.15f
                    }
                    val headingPaint = TextPaint(bodyPaint).apply {
                        textSize = bodyTextSizePx * headingScale
                        typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                    }
                    val layout = buildStaticLayout(element.text.text, headingPaint, availableWidthPx)
                    val headingLineHeightPx = bodyTextSizePx * headingScale * 1.3f
                    val topPadPx = (if (element.level == 1) 12f else 6f) * density
                    val bottomPadPx = 4f * density
                    val spacingBefore = if (currentPageElements.isNotEmpty()) paragraphSpacingPx else 0f
                    val totalHeadingHeightPx = topPadPx + (layout.lineCount * headingLineHeightPx) + bottomPadPx

                    if (currentPageElements.isNotEmpty() && currentHeightPx + spacingBefore + totalHeadingHeightPx > availableHeightPx) {
                        commitPage()
                    }
                    if (currentPageElements.isNotEmpty()) {
                        currentHeightPx += paragraphSpacingPx
                    }
                    currentPageElements.add(element)
                    currentHeightPx += totalHeadingHeightPx
                }

                is ChapterElement.Image -> {
                    val imgHeightPx = (availableHeightPx * 0.45f).coerceAtLeast(bodyLineHeightPx * 5f)
                    val spacingBefore = if (currentPageElements.isNotEmpty()) paragraphSpacingPx else 0f

                    if (currentPageElements.isNotEmpty() && currentHeightPx + spacingBefore + imgHeightPx > availableHeightPx) {
                        commitPage()
                    }
                    if (currentPageElements.isNotEmpty()) {
                        currentHeightPx += paragraphSpacingPx
                    }
                    currentPageElements.add(element)
                    currentHeightPx += imgHeightPx
                }

                is ChapterElement.Paragraph -> {
                    val fullAnnotated = element.text
                    val rawText = fullAnnotated.text
                    var remainingStart = 0
                    var isFirstSlice = element.isFirstLineIndented

                    while (remainingStart < rawText.length) {
                        while (remainingStart < rawText.length && rawText[remainingStart].isWhitespace()) {
                            remainingStart++
                        }
                        if (remainingStart >= rawText.length) break

                        val spacingBefore = if (currentPageElements.isNotEmpty()) paragraphSpacingPx else 0f
                        val remainingHeightPx = availableHeightPx - (currentHeightPx + spacingBefore)
                        val maxLinesCanFit = kotlin.math.floor(remainingHeightPx / bodyLineHeightPx).toInt()

                        if (maxLinesCanFit < 1 && currentPageElements.isNotEmpty()) {
                            commitPage()
                            continue
                        }

                        val sliceText = rawText.substring(remainingStart)
                        val charSequence: CharSequence = if (isFirstSlice && paragraphIndentPx > 0f) {
                            SpannableString(sliceText).apply {
                                setSpan(
                                    LeadingMarginSpan.Standard(paragraphIndentPx.toInt(), 0),
                                    0,
                                    length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        } else {
                            sliceText
                        }
                        val layout = buildStaticLayout(charSequence, bodyPaint, availableWidthPx)
                        val totalLines = layout.lineCount.coerceAtLeast(1)
                        val allowedLines = if (currentPageElements.isEmpty()) {
                            maxLinesCanFit.coerceAtLeast(1)
                        } else {
                            maxLinesCanFit
                        }

                        if (totalLines <= allowedLines) {
                            var subEnd = rawText.length
                            while (subEnd > remainingStart && rawText[subEnd - 1].isWhitespace()) {
                                subEnd--
                            }
                            if (subEnd > remainingStart) {
                                if (currentPageElements.isNotEmpty()) {
                                    currentHeightPx += paragraphSpacingPx
                                }
                                currentPageElements.add(
                                    ChapterElement.Paragraph(
                                        text = fullAnnotated.subSequence(remainingStart, subEnd),
                                        isFirstLineIndented = isFirstSlice
                                    )
                                )
                                currentHeightPx += totalLines * bodyLineHeightPx
                            }
                            remainingStart = rawText.length
                        } else {
                            val lastFittingLineIndex = (allowedLines - 1).coerceIn(0, totalLines - 1)
                            var cutOffsetInSlice = layout.getLineEnd(lastFittingLineIndex)
                            if (cutOffsetInSlice <= 0) {
                                cutOffsetInSlice = sliceText.length
                            }

                            val cutPoint = (remainingStart + cutOffsetInSlice).coerceAtMost(rawText.length)
                            var subEnd = cutPoint
                            while (subEnd > remainingStart && rawText[subEnd - 1].isWhitespace()) {
                                subEnd--
                            }

                            if (subEnd > remainingStart) {
                                if (currentPageElements.isNotEmpty()) {
                                    currentHeightPx += paragraphSpacingPx
                                }
                                currentPageElements.add(
                                    ChapterElement.Paragraph(
                                        text = fullAnnotated.subSequence(remainingStart, subEnd),
                                        isFirstLineIndented = isFirstSlice
                                    )
                                )
                            }

                            commitPage()
                            remainingStart = cutPoint
                            isFirstSlice = false
                        }
                    }
                }
            }
        }

        commitPage()
        return if (pages.isEmpty()) listOf(ChapterPage(emptyList())) else pages
    }

    private fun buildStaticLayout(text: CharSequence, paint: TextPaint, widthPx: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, widthPx, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        }
    }
}