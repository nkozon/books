package com.ozon.books.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ozon.books.data.BookRepository
import com.ozon.books.data.PreferencesManager
import com.ozon.books.model.Book
import com.ozon.books.model.Chapter
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.components.SettingsItemContainer
import com.ozon.books.ui.theme.LocalThemeIsDark
import com.ozon.books.ui.theme.LocalThemeIsOled
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val coroutineScope = rememberCoroutineScope()

    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var fontSizePx by remember { mutableIntStateOf(savedFontSize) }
    var showControls by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(savedFontSize) {
        fontSizePx = savedFontSize
    }

    LaunchedEffect(bookId) {
        val loadedBook = repository.getBook(bookId)
        if (loadedBook != null) {
            book = loadedBook
            currentChapterIndex = loadedBook.currentChapterIndex
            val loadedChapters = repository.getChapters(loadedBook)
            chapters = loadedChapters
        }
        isLoading = false
    }

    fun changeChapter(newIndex: Int) {
        if (newIndex in chapters.indices && book != null) {
            currentChapterIndex = newIndex
            coroutineScope.launch {
                repository.updateReadingProgress(book!!.id, newIndex, 0f)
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

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val topStatusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main reader web view
        if (currentChapter != null) {
            EpubWebView(
                chapter = currentChapter,
                isDark = isDark,
                isOled = isOled,
                fontSizePx = fontSizePx,
                fontFamily = fontFamily,
                onTap = { showControls = !showControls },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No content available in this chapter.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
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
                                onClick = {
                                    fontSizePx = (fontSizePx - 1).coerceAtLeast(10)
                                    preferencesManager.setFontSizePx(fontSizePx)
                                },
                                icon = Icons.Rounded.TextDecrease,
                                contentDescription = "Decrease Font Size",
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            CircleIconButton(
                                onClick = {
                                    fontSizePx = (fontSizePx + 1).coerceAtMost(22)
                                    preferencesManager.setFontSizePx(fontSizePx)
                                },
                                icon = Icons.Rounded.TextIncrease,
                                contentDescription = "Increase Font Size",
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            CircleIconButton(
                                onClick = { showTocSheet = true },
                                icon = Icons.Rounded.FormatListNumbered,
                                contentDescription = "Table of Contents",
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
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
                        onClick = { changeChapter(currentChapterIndex - 1) },
                        enabled = currentChapterIndex > 0,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Previous Chapter",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                    CircleIconButton(
                        onClick = { changeChapter(currentChapterIndex + 1) },
                        enabled = currentChapterIndex < chapters.lastIndex,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Next Chapter",
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
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

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun EpubWebView(
    chapter: Chapter,
    isDark: Boolean,
    isOled: Boolean,
    fontSizePx: Int,
    fontFamily: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawHtml by remember { mutableStateOf("") }
    val backgroundColor = if (isDark) {
        if (isOled) "#000000" else "#141218"
    } else {
        "#FEF7FF"
    }
    val textColor = if (isDark) "#E6E0E9" else "#1D1B20"
    val linkColor = if (isDark) "#D0BCFF" else "#6650A4"

    LaunchedEffect(chapter.absolutePath) {
        rawHtml = withContext(Dispatchers.IO) {
            try {
                File(chapter.absolutePath).readText()
            } catch (e: Exception) {
                "<html><body><p>Error loading chapter content.</p></body></html>"
            }
        }
    }

    val styledHtml = remember(rawHtml, isDark, fontSizePx, fontFamily) {
        val cssInject = """
            <style>
                html, body {
                    background-color: $backgroundColor !important;
                    color: $textColor !important;
                    font-size: ${fontSizePx}px !important;
                    font-family: ${if (fontFamily.equals("Serif", ignoreCase = true)) "Georgia, serif" else if (fontFamily.equals("Monospace", ignoreCase = true)) "monospace" else "sans-serif"} !important;
                    line-height: 1.65 !important;
                    padding: 8px 16px 90px 16px !important;
                    word-wrap: break-word !important;
                    -webkit-font-smoothing: antialiased;
                }
                a {
                    color: $linkColor !important;
                }
                img {
                    max-width: 100% !important;
                    height: auto !important;
                    display: block;
                    margin: 12px auto;
                }
                h1, h2, h3, h4 {
                    color: $textColor !important;
                    line-height: 1.3 !important;
                }
            </style>
        """.trimIndent()

        if (rawHtml.contains("<head>", ignoreCase = true)) {
            rawHtml.replaceFirst("(?i)<head>".toRegex(), "<head>$cssInject")
        } else {
            "<html><head>$cssInject</head><body>$rawHtml</body></html>"
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.parseColor(backgroundColor))
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = false
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                webViewClient = WebViewClient()

                var startX = 0f
                var startY = 0f
                var isClick = false

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                            isClick = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val diffX = kotlin.math.abs(event.x - startX)
                            val diffY = kotlin.math.abs(event.y - startY)
                            if (diffX > 15 || diffY > 15) {
                                isClick = false
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isClick) {
                                onTap()
                            }
                        }
                    }
                    false
                }
            }
        },
        update = { webView ->
            webView.setBackgroundColor(AndroidColor.parseColor(backgroundColor))
            val baseDir = File(chapter.absolutePath).parentFile ?: File("/")
            webView.loadDataWithBaseURL(
                "file://${baseDir.absolutePath}/",
                styledHtml,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}
