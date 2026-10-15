package com.ozon.books.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ozon.books.data.BookRepository
import com.ozon.books.data.PreferencesManager
import com.ozon.books.ui.components.BookSortOrder
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.components.ModernFloatingNavigationBar
import com.ozon.books.ui.components.SortDropdown
import com.ozon.books.ui.home.HomeScreen
import com.ozon.books.ui.library.LibraryScreen
import com.ozon.books.ui.reader.ReaderScreen
import com.ozon.books.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    pendingImportUri: Uri? = null,
    onImportUriConsumed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { BookRepository.getInstance(context) }
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val books by repository.books.collectAsState()
    val recentlyReadBooks by repository.recentlyReadBooks.collectAsState()

    val themeMode by preferencesManager.themeMode.collectAsState()
    val isOledMode by preferencesManager.isOledMode.collectAsState()
    val useDynamicColor by preferencesManager.useDynamicColor.collectAsState()
    val customAccentColor by preferencesManager.customAccentColor.collectAsState()
    val fontSizePx by preferencesManager.fontSizePx.collectAsState()
    val fontFamily by preferencesManager.fontFamily.collectAsState()
    val useLiterata by preferencesManager.useLiterata.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeReadingBookId by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(BookSortOrder.RECENT) }

    fun importBook(uri: Uri) {
        if (isImporting) return
        coroutineScope.launch {
            isImporting = true
            try {
                val result = repository.importBookFromUri(uri)
                result.onSuccess { book ->
                    snackbarHostState.showSnackbar("Imported \"${book.title}\" to Library")
                    activeReadingBookId = book.id
                }.onFailure { error ->
                    snackbarHostState.showSnackbar("Failed to import EPUB: ${error.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                isImporting = false
            }
        }
    }

    // Safely consume one-time pending import URI request
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            val uriToImport = pendingImportUri
            onImportUriConsumed?.invoke()
            importBook(uriToImport)
        }
    }

    // EPUB file picker contract
    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importBook(uri)
        }
    }

    fun launchEpubPicker() {
        epubPickerLauncher.launch(
            arrayOf(
                "application/epub+zip",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss while importing */ },
            confirmButton = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Importing Book",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Extracting EPUB content, please wait...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Reader view when book is active
    val readingId = activeReadingBookId
    if (readingId != null) {
        ReaderScreen(
            bookId = readingId,
            onNavigateBack = {
                activeReadingBookId = null
            }
        )
        return
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val mainTabs = listOf(
        Pair("Home", Icons.Rounded.Home),
        Pair("Library", Icons.AutoMirrored.Rounded.MenuBook),
        Pair("Settings", Icons.Rounded.Settings)
    )

    val currentTabTitle = mainTabs.getOrNull(selectedTabIndex)?.first ?: "Home"

    val topStatusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val topBarHeaderHeight = topStatusPadding + 64.dp
    val listTopPadding = topBarHeaderHeight + 12.dp
    val listBottomPadding = navBarPadding + 56.dp + 16.dp + 16.dp // = navBarPadding + 88.dp
    val contentPadding = PaddingValues(
        start = 0.dp,
        end = 0.dp,
        top = listTopPadding,
        bottom = listBottomPadding
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(3f),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    TopAppBar(
                        title = {
                            if (isSearchActive) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "Search books...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Clear,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                )
                                LaunchedEffect(Unit) {
                                    searchFocusRequester.requestFocus()
                                }
                            } else {
                                Text(
                                    text = currentTabTitle,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        },
                        navigationIcon = {
                            if (isSearchActive) {
                                Box(modifier = Modifier.padding(start = 8.dp)) {
                                    CircleIconButton(
                                        onClick = {
                                            isSearchActive = false
                                            searchQuery = ""
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        },
                                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Close Search",
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                }
                            }
                        },
                        actions = {
                            if (!isSearchActive) {
                                if (selectedTabIndex == 0 || selectedTabIndex == 1) {
                                    if (books.isNotEmpty()) {
                                        CircleIconButton(
                                            onClick = {
                                                if (selectedTabIndex != 1) {
                                                    selectedTabIndex = 1
                                                }
                                                isSearchActive = true
                                            },
                                            icon = Icons.Rounded.Search,
                                            contentDescription = "Search books",
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )

                                        if (selectedTabIndex == 1) {
                                            SortDropdown(
                                                selectedOrder = sortOrder,
                                                onOrderSelected = { sortOrder = it },
                                                iconOnly = true,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                        }

                                        CircleIconButton(
                                            onClick = ::launchEpubPicker,
                                            icon = Icons.Rounded.Add,
                                            contentDescription = "Import EPUB",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    SortDropdown(
                                        selectedOrder = sortOrder,
                                        onOrderSelected = { sortOrder = it },
                                        iconOnly = true
                                    )
                                }
                            }
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "TabContentTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> {
                        HomeScreen(
                            recentlyReadBooks = recentlyReadBooks,
                            hasBooksInLibrary = books.isNotEmpty(),
                            onBookClick = { bookId -> activeReadingBookId = bookId },
                            onNavigateToLibrary = { selectedTabIndex = 1 },
                            onImportBookClick = ::launchEpubPicker,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = listTopPadding,
                                bottom = listBottomPadding
                            )
                        )
                    }
                    1 -> {
                        LibraryScreen(
                            books = books,
                            searchQuery = searchQuery,
                            sortOrder = sortOrder,
                            onBookClick = { bookId -> activeReadingBookId = bookId },
                            onDeleteBook = { bookId ->
                                coroutineScope.launch {
                                    repository.deleteBook(bookId)
                                    snackbarHostState.showSnackbar("Book deleted")
                                }
                            },
                            onImportBookClick = ::launchEpubPicker,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = listTopPadding,
                                bottom = listBottomPadding
                            )
                        )
                    }
                    2 -> {
                        SettingsScreen(
                            currentTheme = themeMode,
                            onThemeSelected = { newMode ->
                                preferencesManager.setThemeMode(newMode)
                            },
                            isOledMode = isOledMode,
                            onOledModeChanged = { preferencesManager.setIsOledMode(it) },
                            useDynamicColor = useDynamicColor,
                            onDynamicColorChanged = { preferencesManager.setUseDynamicColor(it) },
                            customAccentColor = customAccentColor,
                            onCustomAccentColorChanged = { preferencesManager.setCustomAccentColor(it) },
                            fontSizePx = fontSizePx,
                            onFontSizeChanged = { newSize ->
                                preferencesManager.setFontSizePx(newSize)
                            },
                            fontFamily = fontFamily,
                            onFontFamilyChanged = { newFamily ->
                                preferencesManager.setFontFamily(newFamily)
                            },
                            useLiterata = useLiterata,
                            onUseLiterataChanged = { preferencesManager.setUseLiterata(it) },
                            contentPadding = contentPadding
                        )
                    }
                }
            }

            // Modern Icon-Only Floating Navigation Bar at bottom center
            AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
                    .zIndex(4f)
            ) {
                ModernFloatingNavigationBar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    tabs = mainTabs
                )
            }
        }
    }
}
