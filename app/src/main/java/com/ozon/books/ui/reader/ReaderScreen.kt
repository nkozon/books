package com.ozon.books.ui.reader

import android.text.Spanned
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.ozon.books.data.BookRepository
import com.ozon.books.data.PreferencesManager
import com.ozon.books.model.Book
import com.ozon.books.model.Chapter
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.components.SettingsItemContainer
import com.ozon.books.ui.theme.LocalThemeIsDark
import com.ozon.books.ui.theme.LocalThemeIsOled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

sealed class ChapterElement {
    data class Paragraph(val text: AnnotatedString) : ChapterElement()
    data class Heading(val text: AnnotatedString, val level: Int) : ChapterElement()
    data class Image(val absolutePath: String) : ChapterElement()
}

data class ChapterPage(val elements: List<ChapterElement>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { BookRepository.getInstance(context) }
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val isDark = LocalThemeIsDark.current
    val isOled = LocalThemeIsOled.current
    val fontFamily by preferencesManager.fontFamily.collectAsState()
    val savedFontSize by preferencesManager.fontSizePx.collectAsState()
    val isPaginated by preferencesManager.isPaginated.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var fontSizePx by remember { mutableIntStateOf(savedFontSize) }
    var rawHtml by remember { mutableStateOf("") }
    var pendingLastPage by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

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
        isLoading = false
    }

    val currentChapter = chapters.getOrNull(currentChapterIndex)

    LaunchedEffect(currentChapter?.absolutePath) {
        if (currentChapter != null) {
            rawHtml = withContext(Dispatchers.IO) {
                try {
                    File(currentChapter.absolutePath).readText()
                } catch (_: Exception) {
                    "<p>Error loading chapter content.</p>"
                }
            }
        }
    }

    // Parse elements natively
    val elements = remember(rawHtml, currentChapter?.absolutePath) {
        if (currentChapter != null && rawHtml.isNotBlank()) {
            NativeChapterParser.parse(rawHtml, File(currentChapter.absolutePath))
        } else {
            emptyList()
        }
    }

    // Paginate elements for Pages view
    val pages = remember(elements, fontSizePx, screenWidthDp, screenHeightDp) {
        NativeChapterParser.paginate(elements, fontSizePx, screenWidthDp, screenHeightDp)
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size.coerceAtLeast(1) }
    )

    val listState = rememberLazyListState()

    LaunchedEffect(currentChapterIndex, pages.size) {
        if (pendingLastPage && pages.isNotEmpty()) {
            pagerState.scrollToPage(pages.lastIndex)
            pendingLastPage = false
        } else if (!pendingLastPage) {
            pagerState.scrollToPage(0)
            listState.scrollToItem(0)
        }
    }

    fun changeChapter(newIndex: Int, startAtLastPage: Boolean = false) {
        if (newIndex in chapters.indices && book != null) {
            currentChapterIndex = newIndex
            pendingLastPage = startAtLastPage
            coroutineScope.launch {
                repository.updateReadingProgress(book!!.id, newIndex, 0f)
            }
        }
    }

    fun nextPage() {
        if (pagerState.currentPage < pages.lastIndex) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        } else if (currentChapterIndex < chapters.lastIndex) {
            changeChapter(currentChapterIndex + 1, startAtLastPage = false)
        }
    }

    fun prevPage() {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else if (currentChapterIndex > 0) {
            changeChapter(currentChapterIndex - 1, startAtLastPage = true)
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

    val resolvedFontFamily = when {
        fontFamily.equals("Serif", ignoreCase = true) -> FontFamily.Serif
        fontFamily.equals("Monospace", ignoreCase = true) || fontFamily.equals("Mono", ignoreCase = true) -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    val backgroundColor = if (isDark) {
        if (isOled) Color.Black else Color(0xFF141218)
    } else {
        Color(0xFFFEF7FF)
    }
    val textColor = if (isDark) Color(0xFFE6E0E9) else Color(0xFF1D1B20)

    val topStatusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val topContentPadding = topStatusPadding + 64.dp
    val bottomContentPadding = bottomNavPadding + 80.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Native Content: Paged view or Continuous Scroll view
        if (elements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No content available in this chapter.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        } else if (isPaginated) {
            // Paginated View: Native HorizontalPager
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pages.size, currentChapterIndex) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val ratio = offset.x / width.toFloat()
                            when {
                                ratio < 0.22f -> prevPage()
                                ratio > 0.78f -> nextPage()
                                else -> showControls = !showControls
                            }
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = topContentPadding, bottom = bottomContentPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            page.elements.forEach { element ->
                                ChapterElementItem(
                                    element = element,
                                    fontSizePx = fontSizePx,
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
                            .padding(bottom = bottomNavPadding + 10.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${pages.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = textColor.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        } else {
            // Scroll View: Native LazyColumn with edge tapping and smooth scrolling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val ratio = offset.x / width.toFloat()
                            when {
                                ratio < 0.22f -> {
                                    coroutineScope.launch {
                                        listState.animateScrollBy(-550f)
                                    }
                                }
                                ratio > 0.78f -> {
                                    coroutineScope.launch {
                                        listState.animateScrollBy(550f)
                                    }
                                }
                                else -> {
                                    showControls = !showControls
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = topContentPadding,
                        bottom = bottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(elements) { element ->
                        ChapterElementItem(
                            element = element,
                            fontSizePx = fontSizePx,
                            fontFamily = resolvedFontFamily,
                            textColor = textColor
                        )
                    }
                }
            }
        }

        // Top App Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = book!!.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChapter?.title ?: "Chapter ${currentChapterIndex + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            CircleIconButton(
                                onClick = onNavigateBack,
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            CircleIconButton(
                                onClick = { showTocSheet = true },
                                icon = Icons.Rounded.FormatListNumbered,
                                contentDescription = "Table of Contents",
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )

                            // Settings Button with Anchored Top-Right Popup Menu
                            Box {
                                CircleIconButton(
                                    onClick = { showSettingsPopup = true },
                                    icon = Icons.Rounded.Settings,
                                    contentDescription = "Reader Settings",
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )

                                DropdownMenu(
                                    expanded = showSettingsPopup,
                                    onDismissRequest = { showSettingsPopup = false },
                                    shape = RoundedCornerShape(20.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.width(280.dp)
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

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        // 1. Reading Mode
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Layout",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    onClick = { preferencesManager.setIsPaginated(true) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isPaginated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    contentColor = if (isPaginated) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(text = "Pages", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                                    }
                                                }

                                                Surface(
                                                    onClick = { preferencesManager.setIsPaginated(false) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (!isPaginated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    contentColor = if (!isPaginated) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(text = "Scroll", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Font Size
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Font Size",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircleIconButton(
                                                    onClick = {
                                                        fontSizePx = (fontSizePx - 1).coerceAtLeast(10)
                                                        preferencesManager.setFontSizePx(fontSizePx)
                                                    },
                                                    enabled = fontSizePx > 10,
                                                    icon = Icons.Rounded.TextDecrease,
                                                    contentDescription = "Decrease",
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(36.dp),
                                                    iconSize = 18.dp
                                                )

                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "$fontSizePx px",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }

                                                CircleIconButton(
                                                    onClick = {
                                                        fontSizePx = (fontSizePx + 1).coerceAtMost(22)
                                                        preferencesManager.setFontSizePx(fontSizePx)
                                                    },
                                                    enabled = fontSizePx < 22,
                                                    icon = Icons.Rounded.TextIncrease,
                                                    contentDescription = "Increase",
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(36.dp),
                                                    iconSize = 18.dp
                                                )
                                            }
                                        }

                                        // 3. Typeface
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
                                                listOf("Serif", "Sans", "Mono").forEach { familyName ->
                                                    val target = when (familyName) {
                                                        "Sans" -> "Sans-Serif"
                                                        "Mono" -> "Monospace"
                                                        else -> "Serif"
                                                    }
                                                    val isSelected = fontFamily.equals(target, ignoreCase = true) ||
                                                            (target == "Sans-Serif" && fontFamily.equals("Sans", ignoreCase = true))
                                                    Surface(
                                                        onClick = { preferencesManager.setFontFamily(target) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = familyName,
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                                ),
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }

        // Bottom Controls Floating Pill from nkozon/notes
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomNavPadding + 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        onClick = {
                            if (isPaginated) {
                                prevPage()
                            } else {
                                changeChapter(currentChapterIndex - 1)
                            }
                        },
                        enabled = if (isPaginated) (pagerState.currentPage > 0 || currentChapterIndex > 0) else currentChapterIndex > 0,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = if (isPaginated) "Previous Page" else "Previous Chapter",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isPaginated) {
                            Text(
                                text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${((currentChapterIndex + 1).toFloat() / chapters.size.coerceAtLeast(1) * 100).toInt()}% completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CircleIconButton(
                        onClick = {
                            if (isPaginated) {
                                nextPage()
                            } else {
                                changeChapter(currentChapterIndex + 1)
                            }
                        },
                        enabled = if (isPaginated) (pagerState.currentPage < pages.lastIndex || currentChapterIndex < chapters.lastIndex) else currentChapterIndex < chapters.lastIndex,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = if (isPaginated) "Next Page" else "Next Chapter",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Table of contents Bottom Sheet
    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = rememberModalBottomSheetState(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Table of Contents",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(chapters) { index, ch ->
                        val isSelected = index == currentChapterIndex
                        SettingsItemContainer(
                            index = index,
                            total = chapters.size,
                            onClick = {
                                changeChapter(index)
                                showTocSheet = false
                            }
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
private fun ChapterElementItem(
    element: ChapterElement,
    fontSizePx: Int,
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = headingFontSize,
                    lineHeight = headingLineHeight,
                    fontWeight = if (element.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = fontFamily
                ),
                color = textColor,
                modifier = Modifier.padding(
                    top = if (element.level == 1) 12.dp else 6.dp,
                    bottom = 4.dp
                )
            )
        }
        is ChapterElement.Paragraph -> {
            Text(
                text = element.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSizePx.sp,
                    lineHeight = (fontSizePx * 1.6f).sp,
                    fontFamily = fontFamily
                ),
                color = textColor
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
                            android.graphics.Typeface.BOLD ->
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), sStart, sEnd)
                            android.graphics.Typeface.ITALIC ->
                                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), sStart, sEnd)
                            android.graphics.Typeface.BOLD_ITALIC ->
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
            (it.style == android.graphics.Typeface.BOLD || it.style == android.graphics.Typeface.BOLD_ITALIC) &&
                    spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end
        }
        return if (isAllBold && text.length < 70 && !text.endsWith(".")) 3 else null
    }

    fun paginate(
        elements: List<ChapterElement>,
        fontSizePx: Int,
        screenWidthDp: Int,
        screenHeightDp: Int
    ): List<ChapterPage> {
        if (elements.isEmpty()) return listOf(ChapterPage(emptyList()))

        val availableHeightDp = (screenHeightDp - 176).coerceAtLeast(300)
        val availableWidthDp = (screenWidthDp - 48).coerceAtLeast(200)

        val lineHeightDp = fontSizePx * 1.6f
        val maxLinesPerPage = (availableHeightDp / lineHeightDp).toInt().coerceAtLeast(10)
        val charsPerLine = (availableWidthDp / (fontSizePx * 0.55f)).toInt().coerceAtLeast(20)

        val pages = mutableListOf<ChapterPage>()
        var currentPageElements = mutableListOf<ChapterElement>()
        var currentLinesOnPage = 0

        for (element in elements) {
            when (element) {
                is ChapterElement.Heading -> {
                    val headingLines = kotlin.math.ceil(element.text.length.toFloat() / (charsPerLine * 0.75f)).toInt() + 2
                    if (currentLinesOnPage + headingLines > maxLinesPerPage && currentPageElements.isNotEmpty()) {
                        pages.add(ChapterPage(currentPageElements))
                        currentPageElements = mutableListOf()
                        currentLinesOnPage = 0
                    }
                    currentPageElements.add(element)
                    currentLinesOnPage += headingLines
                }
                is ChapterElement.Image -> {
                    val lines = (maxLinesPerPage / 2).coerceAtLeast(6)
                    if (currentLinesOnPage + lines > maxLinesPerPage && currentPageElements.isNotEmpty()) {
                        pages.add(ChapterPage(currentPageElements))
                        currentPageElements = mutableListOf()
                        currentLinesOnPage = 0
                    }
                    currentPageElements.add(element)
                    currentLinesOnPage += lines
                }
                is ChapterElement.Paragraph -> {
                    val textLength = element.text.length
                    val paragraphLines = kotlin.math.ceil(textLength.toFloat() / charsPerLine.toFloat()).toInt() + 1

                    if (currentLinesOnPage + paragraphLines <= maxLinesPerPage) {
                        currentPageElements.add(element)
                        currentLinesOnPage += paragraphLines
                    } else if (paragraphLines <= maxLinesPerPage) {
                        if (currentPageElements.isNotEmpty()) {
                            pages.add(ChapterPage(currentPageElements))
                            currentPageElements = mutableListOf()
                            currentLinesOnPage = 0
                        }
                        currentPageElements.add(element)
                        currentLinesOnPage += paragraphLines
                    } else {
                        // Split long paragraph while PRESERVING AnnotatedString spans
                        val fullAnnotated = element.text
                        val rawText = fullAnnotated.text
                        var remainingStart = 0

                        while (remainingStart < rawText.length) {
                            val availableLines = (maxLinesPerPage - currentLinesOnPage).coerceAtLeast(4)
                            val chunkChars = (availableLines * charsPerLine).coerceAtMost(rawText.length - remainingStart)

                            var cutPoint = remainingStart + chunkChars
                            if (cutPoint < rawText.length) {
                                val periodIdx = rawText.lastIndexOf('.', cutPoint)
                                val spaceIdx = rawText.lastIndexOf(' ', cutPoint)
                                cutPoint = when {
                                    periodIdx > remainingStart + (chunkChars / 2) -> periodIdx + 1
                                    spaceIdx > remainingStart + (chunkChars / 2) -> spaceIdx + 1
                                    else -> cutPoint
                                }
                            }

                            // Trim range bounds so we can use AnnotatedString.subSequence() directly
                            var subStart = remainingStart
                            var subEnd = cutPoint
                            while (subStart < subEnd && rawText[subStart].isWhitespace()) subStart++
                            while (subEnd > subStart && rawText[subEnd - 1].isWhitespace()) subEnd--

                            if (subStart < subEnd) {
                                val chunkAnnotated = fullAnnotated.subSequence(subStart, subEnd)
                                currentPageElements.add(ChapterElement.Paragraph(chunkAnnotated))
                            }
                            remainingStart = cutPoint

                            if (remainingStart < rawText.length) {
                                pages.add(ChapterPage(currentPageElements))
                                currentPageElements = mutableListOf()
                                currentLinesOnPage = 0
                            } else {
                                val addedLen = (subEnd - subStart).coerceAtLeast(0)
                                currentLinesOnPage += kotlin.math.ceil(addedLen.toFloat() / charsPerLine.toFloat()).toInt() + 1
                            }
                        }
                    }
                }
            }
        }

        if (currentPageElements.isNotEmpty()) {
            pages.add(ChapterPage(currentPageElements))
        }

        return if (pages.isEmpty()) listOf(ChapterPage(emptyList())) else pages
    }
}