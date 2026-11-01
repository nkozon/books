package com.ozon.books.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozon.books.R
import com.ozon.books.data.PreferencesManager
import com.ozon.books.model.ReaderBgTheme
import com.ozon.books.model.ReaderTypeface
import com.ozon.books.ui.components.AsyncBookImage
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.theme.LocalThemeIsDark
import kotlinx.coroutines.launch

val LiterataFontFamily = FontFamily(
    Font(resId = R.font.literata, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(resId = R.font.literata_italic, weight = FontWeight.Normal, style = FontStyle.Italic)
)

private val ReaderContentMaxWidth = 720.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val readerViewModel: ReaderViewModel = viewModel(
        key = "reader_$bookId",
        factory = ReaderViewModel.factory(context, bookId)
    )
    val state by readerViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val isDark = LocalThemeIsDark.current
    val fontSizePx by preferencesManager.fontSizePx.collectAsState()
    val lineHeightMultiplier by preferencesManager.lineHeightMultiplier.collectAsState()
    val typeface by preferencesManager.typeface.collectAsState()
    val isJustified by preferencesManager.isJustified.collectAsState()
    val isPaginated by preferencesManager.isPaginated.collectAsState()
    val twoPageReading by preferencesManager.twoPageReading.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val readerBgTheme by preferencesManager.readerBgTheme.collectAsState()

    LaunchedEffect(fontSizePx, lineHeightMultiplier, typeface, isJustified) {
        readerViewModel.setTypography(
            ReaderTypography(
                fontSizePx = fontSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                isJustified = isJustified
            )
        )
    }
    LaunchedEffect(isPaginated) {
        readerViewModel.setPaginated(isPaginated)
    }

    BackHandler {
        readerViewModel.saveNow()
        onNavigateBack()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                readerViewModel.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLightReaderBg = when (readerBgTheme) {
        ReaderBgTheme.WHITE, ReaderBgTheme.SEPIA -> true
        ReaderBgTheme.DARK -> false
        ReaderBgTheme.SYSTEM -> !isDark
    }

    DisposableEffect(isLightReaderBg, isDark) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = isLightReaderBg
        controller?.isAppearanceLightNavigationBars = isLightReaderBg
        onDispose {
            controller?.isAppearanceLightStatusBars = !isDark
            controller?.isAppearanceLightNavigationBars = !isDark
        }
    }

    val dynamicAutoBg = MaterialTheme.colorScheme.background
    val dynamicAutoText = MaterialTheme.colorScheme.onBackground
    val backgroundColor = when (readerBgTheme) {
        ReaderBgTheme.WHITE -> Color.White
        ReaderBgTheme.SEPIA -> Color(0xFFF4ECD8)
        ReaderBgTheme.DARK -> Color.Black
        ReaderBgTheme.SYSTEM -> dynamicAutoBg
    }
    val textColor = when (readerBgTheme) {
        ReaderBgTheme.WHITE -> Color(0xFF1D1B20)
        ReaderBgTheme.SEPIA -> Color(0xFF3E3224)
        ReaderBgTheme.DARK -> Color(0xFFE6E0E9)
        ReaderBgTheme.SYSTEM -> dynamicAutoText
    }

    // Open immersive: the top/bottom control bars overlay the page, so showing them by
    // default would cover the first and last lines. A center tap reveals them.
    var showControls by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onNavigateBack) {
                    Text("Go back")
                }
            }
        }
        return
    }

    val book = state.book ?: return
    val resolvedFontFamily = remember(typeface) { fontFamilyFor(typeface) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val topStatusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topPadding = topStatusPadding + 12.dp
        val bottomPadding = bottomNavPadding + 24.dp
        val isWide = maxWidth >= 600.dp
        val pagesPerSpread = if (isWide && isPaginated && twoPageReading) 2 else 1
        val chapterPager = ChapterPagerLayout(
            readingPageCount = state.pages.size,
            pagesPerSpread = pagesPerSpread,
            chapterIndex = state.currentChapterIndex,
            chapterCount = state.chapters.size
        )
        val spreadCount = chapterPager.spreadCount
        val firstVisiblePage = (state.currentPageIndex / pagesPerSpread) * pagesPerSpread + 1
        val lastVisiblePage = (firstVisiblePage + pagesPerSpread - 1)
            .coerceAtMost(state.pages.size.coerceAtLeast(1))
        val pageLabel = if (lastVisiblePage > firstVisiblePage) {
            "$firstVisiblePage–$lastVisiblePage"
        } else {
            "$firstVisiblePage"
        }
        val horizontalPadding = if (isWide && pagesPerSpread == 1) 48.dp else 24.dp
        val pageWidth = maxWidth / pagesPerSpread
        val contentWidthDp = (minOf(pageWidth, ReaderContentMaxWidth) - horizontalPadding * 2)
            .value.toInt().coerceAtLeast(200)
        val contentHeightDp = (maxHeight - topPadding - bottomPadding)
            .value.toInt().coerceAtLeast(300)
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val imageWidthDp = minOf(pageWidth, ReaderContentMaxWidth).value.toInt()
        val imageAvailableHeight = (contentHeightDp - 16).dp

        LaunchedEffect(contentWidthDp, contentHeightDp, imageWidthDp, density.density, density.fontScale) {
            readerViewModel.setLayout(
                ReaderLayout(
                    widthDp = contentWidthDp,
                    imageWidthDp = imageWidthDp,
                    heightDp = contentHeightDp,
                    density = density.density,
                    fontScale = density.fontScale
                )
            )
        }

        // A new chapter has a different slot map. Initialize its pager in the same
        // composition as its content, rather than drawing the old slot until an effect runs.
        val pagerState = key(state.currentChapterIndex, pagesPerSpread) {
            rememberPagerState(
                initialPage = chapterPager.slotForPage(state.currentPageIndex),
                pageCount = { chapterPager.slotCount }
            )
        }
        val listState = rememberLazyListState()
        var suppressScrollTracking by remember { mutableStateOf(false) }

        LaunchedEffect(state.positionRequestId, pagesPerSpread, pagerState) {
            if (isPaginated) {
                if (state.pages.isNotEmpty()) {
                    pagerState.scrollToPage(
                        chapterPager.slotForPage(state.currentPageIndex)
                    )
                }
            } else if (state.elements.isNotEmpty()) {
                suppressScrollTracking = true
                try {
                    val elements = state.elements
                    val charOffset = state.currentCharOffset
                    val index = NativeChapterParser.elementIndexForOffset(elements, charOffset)
                        .coerceIn(0, elements.lastIndex)
                    listState.scrollToItem(index)

                    // LazyColumn only knows item sizes after it has measured them. Wait one frame,
                    // then restore the exact intra-element position instead of snapping to its top.
                    withFrameNanos { }
                    val element = elements.getOrNull(index)
                    val elementStart = readerViewModel.currentElementOffsets().getOrNull(index) ?: 0
                    val elementLength = element?.let(::elementTextLength) ?: 0
                    if (element != null && elementLength > 0) {
                        val itemSize = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }?.size ?: 0
                        if (itemSize > 0) {
                            val fraction = ((charOffset - elementStart).toFloat() / elementLength.toFloat())
                                .coerceIn(0f, 1f)
                            val offsetPx = (fraction * itemSize)
                                .toInt()
                                .coerceIn(0, (itemSize - 1).coerceAtLeast(0))
                            if (offsetPx > 0) listState.scrollToItem(index, offsetPx)
                        }
                    }
                    withFrameNanos { }
                } finally {
                    suppressScrollTracking = false
                }
            }
        }

        // Only report a page change once a real scroll gesture has settled. Programmatic
        // scrolls are ignored because the ViewModel already holds their target position.
        LaunchedEffect(pagerState, isPaginated, chapterPager, state.pages) {
            if (!isPaginated) return@LaunchedEffect
            var wasScrolling = false
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .collect { (isScrolling, page) ->
                    if (isScrolling) {
                        wasScrolling = true
                    } else if (wasScrolling) {
                        wasScrolling = false
                        val latest = readerViewModel.uiState.value
                        if (latest.isChapterLoading || latest.pages.isEmpty()) return@collect
                        val readingPage = chapterPager.pageForSlot(page)
                        when {
                            page < chapterPager.leadingSlots -> readerViewModel.goToPreviousChapter(openAtEnd = true)
                            readingPage == null -> readerViewModel.goToNextChapter()
                            readingPage != latest.currentPageIndex -> readerViewModel.onPageChanged(readingPage)
                        }
                    }
                }
        }

        LaunchedEffect(isPaginated, state.elements) {
            if (!isPaginated && state.elements.isNotEmpty()) {
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offsetPx) ->
                    if (suppressScrollTracking) return@collect
                    val elements = state.elements
                    val element = elements.getOrNull(index) ?: return@collect
                    val offsets = readerViewModel.currentElementOffsets()
                    val start = offsets.getOrNull(index) ?: 0
                    val itemSize = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index }?.size ?: 0
                    val fraction = if (itemSize > 0) {
                        (offsetPx.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val length = elementTextLength(element)
                    val charOffset = start + (length * fraction).toInt()
                    val total = readerViewModel.currentChapterLength().coerceAtLeast(1)
                    readerViewModel.onScrollPositionChanged(
                        charOffset = charOffset,
                        chapterProgress = charOffset.toFloat() / total
                    )
                }
            }
        }

        fun turnPage(next: Boolean) {
            if (isPaginated) {
                val target = (pagerState.currentPage - chapterPager.leadingSlots) + if (next) 1 else -1
                if (target in 0 until spreadCount) {
                    coroutineScope.launch { pagerState.animateScrollToPage(target + chapterPager.leadingSlots) }
                } else if (next) {
                    readerViewModel.goToNextChapter()
                } else {
                    readerViewModel.goToPreviousChapter(openAtEnd = true)
                }
            } else {
                if (next && !listState.canScrollForward) {
                    readerViewModel.goToNextChapter()
                    return
                }
                if (!next && !listState.canScrollBackward) {
                    readerViewModel.goToPreviousChapter()
                    return
                }
                coroutineScope.launch {
                    val viewport = listState.layoutInfo.viewportSize.height
                    val delta = if (viewport > 0) viewport * 0.85f else 600f
                    listState.animateScrollBy(if (next) delta else -delta)
                }
            }
        }

        val contentModifier = Modifier
            .fillMaxSize()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (isPaginated) "Next page" else "Scroll down") {
                        turnPage(true)
                        true
                    },
                    CustomAccessibilityAction(if (isPaginated) "Previous page" else "Scroll up") {
                        turnPage(false)
                        true
                    }
                )
            }
            .pointerInputTapZones(
                layoutDirection = layoutDirection,
                onLeft = { turnPage(false) },
                onRight = { turnPage(true) },
                onCenter = { showControls = !showControls }
            )

        if (isPaginated) {
            Box(modifier = contentModifier) {
                key(pagerState) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        verticalAlignment = Alignment.Top
                    ) { slotIndex ->
                        val isNextChapter = slotIndex >= chapterPager.leadingSlots + chapterPager.spreadCount
                        val displayedPages = if (isNextChapter) state.nextChapterPages else state.pages
                        val firstPageIndex = if (isNextChapter && displayedPages.isNotEmpty()) 0
                            else chapterPager.pageForSlot(slotIndex)
                        if (firstPageIndex == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = textColor.copy(alpha = 0.5f))
                            }
                            return@HorizontalPager
                        }
                        Row(modifier = Modifier.fillMaxSize()) {
                            repeat(pagesPerSpread) { slot ->
                                val page = displayedPages.getOrNull(firstPageIndex + slot)
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = ReaderContentMaxWidth)
                                            .fillMaxSize()
                                            .padding(top = topPadding, bottom = bottomPadding),
                                        verticalArrangement = Arrangement.Top,
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        page?.elements?.forEach { element ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(
                                                    horizontal = if (element is ChapterElement.Image) 0.dp else horizontalPadding
                                                )
                                            ) {
                                                ReaderElementView(
                                                    element = element,
                                                    fontSizePx = fontSizePx,
                                                    lineHeightMultiplier = lineHeightMultiplier,
                                                    isJustified = isJustified,
                                                    fontFamily = resolvedFontFamily,
                                                    textColor = textColor,
                                                    imageAvailableHeight = imageAvailableHeight
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                if (!showControls) {
                    Text(
                        text = "$pageLabel / ${state.pages.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomNavPadding + 2.dp)
                    )
                }
            }
        } else {
            Box(modifier = contentModifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding
                    ),
                    verticalArrangement = Arrangement.Top
                ) {
                    itemsIndexed(state.elements) { _, element ->
                        Box(
                            modifier = Modifier
                                .widthIn(max = ReaderContentMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = if (element is ChapterElement.Image) 0.dp else horizontalPadding)
                        ) {
                            ReaderElementView(
                                element = element,
                                fontSizePx = fontSizePx,
                                lineHeightMultiplier = lineHeightMultiplier,
                                isJustified = isJustified,
                                fontFamily = resolvedFontFamily,
                                textColor = textColor,
                                imageAvailableHeight = Dp.Infinity
                            )
                        }
                    }
                }

                if (!showControls) {
                    Text(
                        text = "${((state.totalBookProgress) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomNavPadding + 2.dp)
                    )
                }
            }
        }

        if (state.isChapterLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopControls(
                bookTitle = book.title,
                chapterTitle = state.currentChapter?.title
                    ?: "Chapter ${state.currentChapterIndex + 1}",
                textColor = textColor,
                backgroundColor = backgroundColor,
                isBookmarked = state.currentBookmark != null,
                onNavigateBack = {
                    readerViewModel.saveNow()
                    onNavigateBack()
                },
                onToggleBookmark = {
                    readerViewModel.toggleBookmark(
                        previewText(state.elements, state.currentCharOffset)
                    )
                },
                onOpenSearch = { showSearchSheet = true },
                onOpenToc = { showTocSheet = true },
                onOpenSettings = { showSettingsSheet = true }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomControls(
                backgroundColor = backgroundColor,
                textColor = textColor,
                chapterIndex = state.currentChapterIndex,
                chapterCount = state.chapters.size,
                pageLabel = pageLabel,
                pageCount = state.pages.size.coerceAtLeast(1),
                isPaginated = isPaginated,
                bookProgress = state.totalBookProgress,
                canGoPrevious = if (isPaginated) {
                    state.currentPageIndex / pagesPerSpread > 0 || state.currentChapterIndex > 0
                } else {
                    listState.canScrollBackward || state.currentChapterIndex > 0
                },
                canGoNext = if (isPaginated) {
                    state.currentPageIndex / pagesPerSpread < spreadCount - 1 ||
                        state.currentChapterIndex < state.chapters.lastIndex
                } else {
                    listState.canScrollForward || state.currentChapterIndex < state.chapters.lastIndex
                },
                onPrevious = { turnPage(false) },
                onNext = { turnPage(true) }
            )
        }

        if (showTocSheet) {
            ReaderTocSheet(
                chapters = state.chapters,
                currentChapterIndex = state.currentChapterIndex,
                bookmarks = state.bookmarks,
                onChapterSelected = { index ->
                    showTocSheet = false
                    readerViewModel.goToChapter(index)
                },
                onBookmarkSelected = { bookmark ->
                    showTocSheet = false
                    readerViewModel.openBookmark(bookmark)
                },
                onBookmarkRemoved = readerViewModel::removeBookmark,
                onDismiss = { showTocSheet = false }
            )
        }

        if (showSearchSheet) {
            ReaderSearchSheet(
                query = state.searchQuery,
                onQueryChange = { query ->
                    readerViewModel.search(query)
                },
                results = state.searchResults,
                isSearching = state.isSearching,
                searchCompleted = state.searchCompleted,
                onResultSelected = { result ->
                    showSearchSheet = false
                    readerViewModel.openSearchResult(result)
                },
                onDismiss = {
                    showSearchSheet = false
                    readerViewModel.clearSearch()
                }
            )
        }

        if (showSettingsSheet) {
            ReaderSettingsSheet(
                fontSizePx = fontSizePx,
                lineHeightMultiplier = lineHeightMultiplier,
                typeface = typeface,
                isJustified = isJustified,
                isPaginated = isPaginated,
                twoPageReading = twoPageReading,
                isTablet = isTablet,
                onTwoPageReadingChange = preferencesManager::setTwoPageReading,
                backgroundTheme = readerBgTheme,
                onFontSizeChange = preferencesManager::setFontSizePx,
                onLineHeightChange = preferencesManager::setLineHeightMultiplier,
                onTypefaceChange = preferencesManager::setTypeface,
                onJustifiedChange = preferencesManager::setIsJustified,
                onPaginatedChange = preferencesManager::setIsPaginated,
                onBackgroundThemeChange = preferencesManager::setReaderBgTheme,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

private fun Modifier.pointerInputTapZones(
    layoutDirection: LayoutDirection,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(layoutDirection) {
        detectTapGestures { offset ->
            val ratio = if (size.width > 0) offset.x / size.width.toFloat() else 0.5f
            val rtl = layoutDirection == LayoutDirection.Rtl
            val goesBack = if (rtl) ratio > 0.78f else ratio < 0.22f
            val goesForward = if (rtl) ratio < 0.22f else ratio > 0.78f
            when {
                goesBack -> onLeft()
                goesForward -> onRight()
                else -> onCenter()
            }
        }
    }
)

@Composable
private fun ReaderTopControls(
    bookTitle: String,
    chapterTitle: String,
    textColor: Color,
    backgroundColor: Color,
    isBookmarked: Boolean,
    onNavigateBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val controlSurface = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    val controlContent = MaterialTheme.colorScheme.onSurface

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
            // Keep taps on the control bar from reaching the page-turn tap zones.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                containerColor = controlSurface,
                contentColor = controlContent
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = controlSurface
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    CircleIconButton(
                        onClick = onToggleBookmark,
                        icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        containerColor = Color.Transparent,
                        contentColor = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            controlContent
                        },
                        iconSize = 22.dp
                    )
                    CircleIconButton(
                        onClick = onOpenSearch,
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search in book",
                        containerColor = Color.Transparent,
                        contentColor = controlContent,
                        iconSize = 22.dp
                    )
                    CircleIconButton(
                        onClick = onOpenToc,
                        icon = Icons.Rounded.FormatListNumbered,
                        contentDescription = "Table of contents",
                        containerColor = Color.Transparent,
                        contentColor = controlContent,
                        iconSize = 22.dp
                    )
                    CircleIconButton(
                        onClick = onOpenSettings,
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Reader settings",
                        containerColor = Color.Transparent,
                        contentColor = controlContent,
                        iconSize = 22.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    backgroundColor: Color,
    textColor: Color,
    chapterIndex: Int,
    chapterCount: Int,
    pageLabel: String,
    pageCount: Int,
    isPaginated: Boolean,
    bookProgress: Float,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val controlSurface = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
    val controlContent = MaterialTheme.colorScheme.onSurface

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
            // Keep taps on the control bar from reaching the page-turn tap zones.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = 24.dp)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = if (isPaginated) "Previous page" else "Scroll up",
                containerColor = controlSurface,
                contentColor = controlContent
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                val statusLabel = if (isPaginated) {
                    "Chapter ${chapterIndex + 1} of $chapterCount · Page $pageLabel of $pageCount"
                } else {
                    "Chapter ${chapterIndex + 1} of $chapterCount · ${(bookProgress * 100).toInt()}%"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { bookProgress },
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
                onClick = onNext,
                enabled = canGoNext,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = if (isPaginated) "Next page" else "Scroll down",
                containerColor = controlSurface,
                contentColor = controlContent
            )
        }
    }
}

@Composable
private fun ReaderElementView(
    element: ChapterElement,
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    isJustified: Boolean,
    fontFamily: FontFamily,
    textColor: Color,
    imageAvailableHeight: Dp
) {
    when (element) {
        is ChapterElement.Heading -> {
            val headingFontSize = when (element.level) {
                1 -> (fontSizePx * 1.55f).sp
                2 -> (fontSizePx * 1.30f).sp
                else -> (fontSizePx * 1.15f).sp
            }
            Text(
                text = element.text,
                style = TextStyle(
                    fontSize = headingFontSize,
                    lineHeight = headingFontSize * 1.3f,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    // Match StaticLayout measurement in the paginator (LocalTextStyle adds 0.5sp).
                    letterSpacing = 0.sp,
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
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    // Match StaticLayout measurement in the paginator (LocalTextStyle adds 0.5sp).
                    letterSpacing = 0.sp,
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
            // Reserve the same height the paginator assumed so late image loads cannot
            // push text past the bottom of the page.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val imageHeight = readerImageHeight(maxWidth.value, imageAvailableHeight.value, element.aspectRatio).dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight + 24.dp)
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(textColor.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncBookImage(
                        path = element.absolutePath,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        targetWidthPx = 1600,
                        targetHeightPx = 1600,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun fontFamilyFor(typeface: ReaderTypeface): FontFamily = when (typeface) {
    ReaderTypeface.LITERATA -> LiterataFontFamily
    ReaderTypeface.SERIF -> FontFamily.Serif
    ReaderTypeface.SANS -> FontFamily.SansSerif
    ReaderTypeface.MONO -> FontFamily.Monospace
}

private fun elementTextLength(element: ChapterElement): Int = when (element) {
    is ChapterElement.Paragraph -> element.text.length
    is ChapterElement.Heading -> element.text.length
    is ChapterElement.Image -> 1
}

private fun previewText(elements: List<ChapterElement>, charOffset: Int): String {
    if (elements.isEmpty()) return "Saved position"
    val index = NativeChapterParser.elementIndexForOffset(elements, charOffset)
    for (i in index until elements.size) {
        val text = when (val element = elements[i]) {
            is ChapterElement.Paragraph -> element.text.text
            is ChapterElement.Heading -> element.text.text
            is ChapterElement.Image -> null
        }
        if (!text.isNullOrBlank()) return text.trim().take(140)
    }
    return "Saved position"
}
