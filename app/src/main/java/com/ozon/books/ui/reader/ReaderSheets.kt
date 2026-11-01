package com.ozon.books.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozon.books.model.Bookmark
import com.ozon.books.model.Chapter
import com.ozon.books.model.ReaderBgTheme
import com.ozon.books.model.ReaderTypeface
import java.util.Locale

@Composable
fun ReaderTocSheet(
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    bookmarks: List<Bookmark>,
    onChapterSelected: (Int) -> Unit,
    onBookmarkSelected: (Bookmark) -> Unit,
    onBookmarkRemoved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val chapterListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
    )
    val bookmarkListState = rememberLazyListState()
    val bottomContentPadding = if (LocalConfiguration.current.screenWidthDp < 600) 96.dp else 24.dp
    ReaderSidePanel(onDismiss = onDismiss) { dismiss ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = if (showBookmarks) bookmarkListState else chapterListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 140.dp, bottom = bottomContentPadding)
            ) {
                if (showBookmarks) {
                    if (bookmarks.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp))
                                Text("Keep a place for later", style = MaterialTheme.typography.titleMedium)
                                Text("Bookmarks you save while reading appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Surface(
                            onClick = { onBookmarkSelected(bookmark) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapters.getOrNull(bookmark.chapterIndex)?.title
                                            ?: "Chapter ${bookmark.chapterIndex + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bookmark.preview.ifBlank { "Saved position" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onBookmarkRemoved(bookmark.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove bookmark",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(chapters, key = { index, _ -> index }) { index, chapter ->
                        val isSelected = index == currentChapterIndex
                        val topRadius by animateDpAsState(
                            if (isSelected || index == 0) 24.dp else 5.dp,
                            label = "chapterTopRadius"
                        )
                        val bottomRadius by animateDpAsState(
                            if (isSelected || index == chapters.lastIndex) 24.dp else 5.dp,
                            label = "chapterBottomRadius"
                        )
                        Surface(
                            onClick = { onChapterSelected(index) },
                            shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .width(40.dp)
                                        .padding(vertical = 10.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )))
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp)
            ) {
                ReaderSheetHeader(title = "Contents", onDismiss = dismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderContentsTab(
                        label = "Chapters (${chapters.size})",
                        selected = !showBookmarks,
                        onClick = { showBookmarks = false },
                        modifier = Modifier.weight(1f)
                    )
                    ReaderContentsTab(
                        label = "Bookmarks (${bookmarks.size})",
                        selected = showBookmarks,
                        onClick = { showBookmarks = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<ReaderSearchResult>,
    isSearching: Boolean,
    searchCompleted: Boolean,
    onResultSelected: (ReaderSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val bottomContentPadding = if (LocalConfiguration.current.screenWidthDp < 600) 96.dp else 24.dp
    ReaderSidePanel(onDismiss = onDismiss) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp)
        ) {
            ReaderSheetHeader(title = "Search in book", onDismiss = dismiss)

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Type at least 2 characters") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                results.isEmpty() && searchCompleted -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matches found for \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search across every chapter in this book.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomContentPadding)
                    ) {
                        items(results) { result ->
                            Surface(
                                onClick = { onResultSelected(result) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = result.chapterTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = result.snippet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
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
fun ReaderSettingsSheet(
    fontSizePx: Int,
    lineHeightMultiplier: Float,
    typeface: ReaderTypeface,
    isJustified: Boolean,
    isPaginated: Boolean,
    twoPageReading: Boolean,
    isTablet: Boolean,
    onTwoPageReadingChange: (Boolean) -> Unit,
    backgroundTheme: ReaderBgTheme,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onTypefaceChange: (ReaderTypeface) -> Unit,
    onJustifiedChange: (Boolean) -> Unit,
    onPaginatedChange: (Boolean) -> Unit,
    onBackgroundThemeChange: (ReaderBgTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val autoBg = MaterialTheme.colorScheme.background

    val bottomContentPadding = if (LocalConfiguration.current.screenWidthDp < 600) 96.dp else 32.dp
    ReaderSidePanel(onDismiss = onDismiss) { dismiss ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 92.dp, bottom = bottomContentPadding)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ReaderSettingSection(label = "Layout", index = 0, total = 6) {
                            ReaderChoiceRow(
                                options = listOf("Pages" to true, "Scroll" to false),
                                selected = isPaginated,
                                onSelected = { onPaginatedChange(it) }
                            )
                            if (isTablet) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Two-page view", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Show facing pages in Pages mode",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = twoPageReading,
                                        onCheckedChange = onTwoPageReadingChange,
                                        enabled = isPaginated
                                    )
                                }
                            }
                        }

                        ReaderSettingSection(label = "Background", index = 1, total = 6) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReaderThemeSwatch(
                                    label = "Auto",
                                    swatchColor = autoBg,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    isSelected = backgroundTheme == ReaderBgTheme.SYSTEM,
                                    onClick = { onBackgroundThemeChange(ReaderBgTheme.SYSTEM) },
                                    modifier = Modifier.weight(1f)
                                )
                                ReaderThemeSwatch(
                                    label = "White",
                                    swatchColor = Color.White,
                                    contentColor = Color(0xFF1D1B20),
                                    isSelected = backgroundTheme == ReaderBgTheme.WHITE,
                                    onClick = { onBackgroundThemeChange(ReaderBgTheme.WHITE) },
                                    modifier = Modifier.weight(1f)
                                )
                                ReaderThemeSwatch(
                                    label = "Sepia",
                                    swatchColor = Color(0xFFF4ECD8),
                                    contentColor = Color(0xFF3E3224),
                                    isSelected = backgroundTheme == ReaderBgTheme.SEPIA,
                                    onClick = { onBackgroundThemeChange(ReaderBgTheme.SEPIA) },
                                    modifier = Modifier.weight(1f)
                                )
                                ReaderThemeSwatch(
                                    label = "Black",
                                    swatchColor = Color.Black,
                                    contentColor = Color(0xFFE6E0E9),
                                    isSelected = backgroundTheme == ReaderBgTheme.DARK,
                                    onClick = { onBackgroundThemeChange(ReaderBgTheme.DARK) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ReaderSettingSection(label = "Font size", index = 2, total = 6) {
                                ReaderStepper(
                                    valueText = "$fontSizePx px",
                                    onDecrease = { onFontSizeChange(fontSizePx - 1) },
                                    onIncrease = { onFontSizeChange(fontSizePx + 1) },
                                    canDecrease = fontSizePx > 10,
                                    canIncrease = fontSizePx < 22
                                )
                            }
                            ReaderSettingSection(label = "Line height", index = 3, total = 6) {
                                ReaderStepper(
                                    valueText = String.format(Locale.US, "%.1fx", lineHeightMultiplier),
                                    onDecrease = { onLineHeightChange(lineHeightMultiplier - 0.1f) },
                                    onIncrease = { onLineHeightChange(lineHeightMultiplier + 0.1f) },
                                    canDecrease = lineHeightMultiplier > 1.25f,
                                    canIncrease = lineHeightMultiplier < 1.95f
                                )
                            }
                        }

                        ReaderSettingSection(label = "Typeface", index = 4, total = 6) {
                            ReaderChoiceRow(
                                options = ReaderTypeface.entries.map { it.label to it },
                                selected = typeface,
                                onSelected = onTypefaceChange
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipClickable(shape = readerSettingsShape(index = 5, total = 6)) { onJustifiedChange(!isJustified) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isJustified) {
                                        Icons.Rounded.FormatAlignJustify
                                    } else {
                                        Icons.AutoMirrored.Rounded.FormatAlignLeft
                                    },
                                    contentDescription = null,
                                    tint = if (isJustified) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Justify text",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Switch(checked = isJustified, onCheckedChange = onJustifiedChange)
                        }
                    }
                }
            }
            ReaderSheetHeader(
                title = "Reader settings",
                onDismiss = dismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )))
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)
            )
        }
    }
}

private class ReaderPanelGesture(
    val enabled: Boolean = false,
    val onDrag: (Float) -> Unit = {},
    val onRelease: (Float) -> Unit = {}
)

private val LocalReaderPanelGesture = staticCompositionLocalOf { ReaderPanelGesture() }

/** Phone bottom sheet or floating tablet panel anchored to the physical right. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSidePanel(
    onDismiss: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                reveal.animateTo(0f, tween(220))
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(300)) }
    // Read stable insets from the reader window before entering the Dialog. Dialog
    // insets can be zero/consumed, especially while the reader is immersive.
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val dismissDistance = with(density) { 80.dp.toPx() }
    val dismissVelocity = with(density) { 900.dp.toPx() }
    val drag: (Float) -> Unit = { delta ->
        if (!isTablet && !closing) {
            resetJob?.cancel()
            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
        }
    }
    val release: (Float) -> Unit = { velocity ->
        if (dragOffset >= dismissDistance || (dragOffset > 0f && velocity >= dismissVelocity)) {
            dismiss()
        } else {
            resetJob?.cancel()
            resetJob = scope.launch {
                animate(dragOffset, 0f, animationSpec = tween(180)) { value, _ -> dragOffset = value }
            }
        }
    }
    val scrollConnection = remember(isTablet, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Restore a partly dragged panel before allowing upward content scrolling.
                if (!isTablet && source == NestedScrollSource.UserInput && available.y < 0f && dragOffset > 0f) {
                    val consumed = available.y.coerceAtLeast(-dragOffset)
                    drag(consumed)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // A scrollable child consumes downward swipes until it reaches its top.
                // Only the remaining drag is allowed to move the panel, never a fling.
                if (!isTablet && source == NestedScrollSource.UserInput && available.y > 0f) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isTablet && dragOffset > 0f) {
                    release(available.y)
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }
    val direction = LocalLayoutDirection.current
    val systemInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val panelInsets = with(density) {
        PaddingValues(
            start = (if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                systemInsets.getLeft(this, direction) else systemInsets.getRight(this, direction)).toDp(),
            top = systemInsets.getTop(this).toDp(),
            end = (if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr)
                systemInsets.getRight(this, direction) else systemInsets.getLeft(this, direction)).toDp(),
            bottom = systemInsets.getBottom(this).toDp()
        )
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.setWindowAnimations(0)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = if (isTablet) minOf((maxWidth - 32.dp).coerceAtLeast(0.dp), 440.dp) else maxWidth
            val panelWidthPx = with(LocalDensity.current) { panelWidth.toPx() }
            val travelHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val panelShape = if (isTablet) RoundedCornerShape(28.dp)
                else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f * reveal.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss
                    )
            )
            Box(modifier = Modifier.fillMaxSize().padding(panelInsets).padding(
                if (isTablet) PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 48.dp)
                else PaddingValues(0.dp)
            )) {
                Surface(
                    modifier = Modifier
                        .align(if (isTablet) AbsoluteAlignment.CenterRight else Alignment.BottomCenter)
                        .width(panelWidth)
                        .fillMaxHeight(if (isTablet) 1f else 0.9f)
                        .clip(panelShape)
                        .absoluteOffset {
                            if (isTablet) IntOffset(((1f - reveal.value) * panelWidthPx * 1.1f).roundToInt(), 0)
                            else IntOffset(0, ((1f - reveal.value) * travelHeightPx + dragOffset).roundToInt())
                        }
                        .nestedScroll(scrollConnection),
                    shape = panelShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp
                ) {
                    CompositionLocalProvider(
                        LocalReaderPanelGesture provides ReaderPanelGesture(!isTablet, drag, release)
                    ) {
                        content(dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContentsTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radius by animateDpAsState(if (selected) 14.dp else 28.dp, label = "contentsTabRadius")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(radius),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReaderSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gesture = LocalReaderPanelGesture.current
    Row(
        modifier = Modifier.draggable(
                state = rememberDraggableState { gesture.onDrag(it) },
                orientation = Orientation.Vertical,
                enabled = gesture.enabled,
                onDragStopped = { gesture.onRelease(it) }
            )
            .then(modifier)
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
        }
    }
}

@Composable
private fun Modifier.clipClickable(shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit): Modifier =
    this
        .clip(shape)
        .clickable(onClick = onClick)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
private fun readerSettingsShape(index: Int, total: Int): RoundedCornerShape {
    val top by animateDpAsState(if (index == 0) 28.dp else 5.dp, label = "settingsTopRadius")
    val bottom by animateDpAsState(if (index == total - 1) 28.dp else 5.dp, label = "settingsBottomRadius")
    return RoundedCornerShape(top, top, bottom, bottom)
}

@Composable
private fun ReaderSettingSection(
    label: String,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, readerSettingsShape(index, total))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun <T> ReaderChoiceRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelected(value) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onDecrease,
            enabled = canDecrease,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = onIncrease,
            enabled = canIncrease,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = swatchColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        modifier = modifier.height(80.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Aa", style = MaterialTheme.typography.headlineSmall, color = contentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
