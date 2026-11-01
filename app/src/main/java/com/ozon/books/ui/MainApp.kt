package com.ozon.books.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ozon.books.data.PreferencesManager
import com.ozon.books.model.Book
import com.ozon.books.ui.components.BookSortOrder
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.components.ModernFloatingNavigationBar
import com.ozon.books.ui.components.SortDropdown
import com.ozon.books.ui.home.HomeScreen
import com.ozon.books.ui.library.LibraryScreen
import com.ozon.books.ui.reader.ReaderScreen
import com.ozon.books.ui.settings.SettingsScreen
import com.ozon.books.ui.theme.LocalThemeIsDark
import kotlinx.coroutines.launch

private data class MainTab(
    val route: String,
    val title: String,
    val icon: ImageVector
)

private val MainTabs = listOf(
    MainTab("home", "Home", Icons.Rounded.Home),
    MainTab("library", "Library", Icons.AutoMirrored.Rounded.MenuBook),
    MainTab("settings", "Settings", Icons.Rounded.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    pendingImportUri: Uri? = null,
    onImportUriConsumed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = LocalThemeIsDark.current
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.factory(context))
    val preferencesManager = remember { PreferencesManager.getInstance(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val books by mainViewModel.books.collectAsState()
    val recentlyReadBooks by mainViewModel.recentlyReadBooks.collectAsState()
    val isImporting by mainViewModel.isImporting.collectAsState()
    val isLoaded by mainViewModel.isLoaded.collectAsState()

    DisposableEffect(isDark) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !isDark
        controller?.isAppearanceLightNavigationBars = !isDark
        onDispose { }
    }

    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) mainViewModel.importBook(uri)
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

    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) {
            onImportUriConsumed?.invoke()
            mainViewModel.importBook(pendingImportUri)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            when (event) {
                is MainEvent.Message -> {
                    snackbarHostState.showSnackbar(event.text)
                }

                is MainEvent.Imported -> {
                    navController.navigate("reader/${event.book.id}")
                    snackbarHostState.showSnackbar("Imported \"${event.book.title}\"")
                }

                is MainEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Removed \"${event.book.title}\"",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        mainViewModel.undoDelete(event.book)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val snackbarBottomPadding = if (isWide) 24.dp else 96.dp

        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("main") {
                MainScreen(
                    books = books,
                    recentlyReadBooks = recentlyReadBooks,
                    isLoaded = isLoaded,
                    isImporting = isImporting,
                    onCancelImport = mainViewModel::cancelImport,
                    onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
                    onImportClick = ::launchEpubPicker,
                    onDeleteBook = mainViewModel::deleteBook,
                    preferencesManager = preferencesManager
                )
            }
            composable(
                route = "reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = snackbarBottomPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    books: List<Book>,
    recentlyReadBooks: List<Book>,
    isLoaded: Boolean,
    isImporting: Boolean,
    onCancelImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onImportClick: () -> Unit,
    onDeleteBook: (String) -> Unit,
    preferencesManager: PreferencesManager
) {
    var currentRoute by rememberSaveable { mutableStateOf("home") }
    val tabStateHolder = rememberSaveableStateHolder()
    val currentTitle = MainTabs.firstOrNull { it.route == currentRoute }?.title ?: "Home"

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var sortOrderName by rememberSaveable { mutableStateOf(BookSortOrder.RECENTLY_ADDED.name) }
    val sortOrder = runCatching { BookSortOrder.valueOf(sortOrderName) }
        .getOrDefault(BookSortOrder.RECENTLY_ADDED)

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    val themeMode by preferencesManager.themeMode.collectAsState()
    val isOledMode by preferencesManager.isOledMode.collectAsState()
    val useDynamicColor by preferencesManager.useDynamicColor.collectAsState()
    val customAccentColor by preferencesManager.customAccentColor.collectAsState()
    val fontSizePx by preferencesManager.fontSizePx.collectAsState()
    val lineHeightMultiplier by preferencesManager.lineHeightMultiplier.collectAsState()
    val typeface by preferencesManager.typeface.collectAsState()
    val isJustified by preferencesManager.isJustified.collectAsState()
    val isPaginated by preferencesManager.isPaginated.collectAsState()
    val readerBgTheme by preferencesManager.readerBgTheme.collectAsState()

    fun closeSearch() {
        isSearchActive = false
        searchQuery = ""
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BackHandler(enabled = isSearchActive) { closeSearch() }

    fun navigateTab(route: String) {
        if (route == currentRoute) return
        closeSearch()
        currentRoute = route
    }

    BackHandler(enabled = currentRoute != "home" && !isSearchActive) {
        navigateTab("home")
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val listBottomPadding = when {
            isWide -> navBarPadding + 20.dp
            isSearchActive -> navBarPadding + 20.dp
            else -> navBarPadding + 92.dp
        }
        val horizontalContentPadding = if (isWide) {
            ((maxWidth - 900.dp) / 2).coerceAtLeast(16.dp)
        } else {
            16.dp
        }
        val screenPadding = PaddingValues(
            start = horizontalContentPadding,
            end = horizontalContentPadding,
            top = 8.dp,
            bottom = listBottomPadding
        )

        val headerActions: @Composable () -> Unit = {
            if (isSearchActive) {
                Box(modifier = Modifier.padding(end = if (isWide) 0.dp else 8.dp)) {
                    SortDropdown(
                        selectedOrder = sortOrder,
                        onOrderSelected = { sortOrderName = it.name },
                        iconOnly = true
                    )
                }
            } else {
                val searchAction: @Composable () -> Unit = {
                    CircleIconButton(
                        onClick = { isSearchActive = true },
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search books",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = if (isWide) 0.dp else 6.dp)
                    )
                }
                val sortAction: @Composable () -> Unit = {
                    SortDropdown(
                        selectedOrder = sortOrder,
                        onOrderSelected = { sortOrderName = it.name },
                        iconOnly = true,
                        modifier = Modifier.padding(end = if (isWide) 0.dp else 6.dp)
                    )
                }
                val importAction: @Composable () -> Unit = {
                    CircleIconButton(
                        onClick = onImportClick,
                        enabled = !isImporting,
                        icon = Icons.Rounded.Add,
                        contentDescription = "Import EPUB",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = if (isWide) 0.dp else 12.dp)
                    )
                }
                if (isWide) {
                    if (currentRoute != "settings") importAction()
                    if (currentRoute == "library") {
                        sortAction()
                        searchAction()
                    }
                } else {
                    if (currentRoute == "library") {
                        searchAction()
                        sortAction()
                    }
                    if (currentRoute != "settings") importAction()
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (currentRoute == "settings") Color.Transparent
                            else MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
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
                                                if (!isWide && searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Clear,
                                                            contentDescription = "Clear search",
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
                                            text = currentTitle,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    if (!isWide && isSearchActive) {
                                        Box(modifier = Modifier.padding(start = 8.dp)) {
                                            CircleIconButton(
                                                onClick = ::closeSearch,
                                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Close search",
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    if (!isWide) headerActions()
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
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (currentRoute == "settings") PaddingValues(0.dp) else innerPadding)
                ) {
                    if (!isLoaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        tabStateHolder.SaveableStateProvider(currentRoute) {
                            when (currentRoute) {
                                "home" -> {
                                    HomeScreen(
                                        recentlyReadBooks = recentlyReadBooks,
                                        hasBooksInLibrary = books.isNotEmpty(),
                                        onBookClick = onOpenBook,
                                        onNavigateToLibrary = { navigateTab("library") },
                                        onImportBookClick = onImportClick,
                                        contentPadding = screenPadding
                                    )
                                }
                                "library" -> {
                                    LibraryScreen(
                                        books = books,
                                        searchQuery = searchQuery,
                                        sortOrder = sortOrder,
                                        onBookClick = onOpenBook,
                                        onDeleteBook = onDeleteBook,
                                        onImportBookClick = onImportClick,
                                        contentPadding = screenPadding
                                    )
                                }
                                "settings" -> {
                                    SettingsScreen(
                                        currentTheme = themeMode,
                                        onThemeSelected = preferencesManager::setThemeMode,
                                        isOledMode = isOledMode,
                                        onOledModeChanged = preferencesManager::setIsOledMode,
                                        useDynamicColor = useDynamicColor,
                                        onDynamicColorChanged = preferencesManager::setUseDynamicColor,
                                        customAccentColor = customAccentColor,
                                        onCustomAccentColorChanged = preferencesManager::setCustomAccentColor,
                                        fontSizePx = fontSizePx,
                                        onFontSizeChanged = preferencesManager::setFontSizePx,
                                        lineHeightMultiplier = lineHeightMultiplier,
                                        onLineHeightChanged = preferencesManager::setLineHeightMultiplier,
                                        typeface = typeface,
                                        onTypefaceChanged = preferencesManager::setTypeface,
                                        isJustified = isJustified,
                                        onJustifiedChanged = preferencesManager::setIsJustified,
                                        isPaginated = isPaginated,
                                        onPaginatedChanged = preferencesManager::setIsPaginated,
                                        readerBgTheme = readerBgTheme,
                                        onReaderBgThemeChanged = preferencesManager::setReaderBgTheme,
                                        contentPadding = PaddingValues(
                                            start = horizontalContentPadding,
                                            end = horizontalContentPadding,
                                            top = innerPadding.calculateTopPadding() + 8.dp,
                                            bottom = listBottomPadding
                                        )
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isImporting,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (isWide) 16.dp else 80.dp)
                    ) {
                        ImportProgressBanner(onCancel = onCancelImport)
                    }
                }
            }

            if (isWide) {
                Spacer(modifier = Modifier.width(88.dp))
            }
        }

        if (isWide) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                headerActions()
                if (isSearchActive) {
                    if (searchQuery.isNotEmpty()) {
                        CircleIconButton(
                            onClick = { searchQuery = "" },
                            icon = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                    CircleIconButton(
                        onClick = ::closeSearch,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close search",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isWide || !isSearchActive,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(if (isWide) Alignment.BottomEnd else Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(end = if (isWide) 16.dp else 0.dp, bottom = 16.dp)
        ) {
            ModernFloatingNavigationBar(
                selectedTabIndex = MainTabs.indexOfFirst { it.route == currentRoute }
                    .coerceAtLeast(0),
                onTabSelected = { index -> navigateTab(MainTabs[index].route) },
                tabs = MainTabs.map { it.title to it.icon },
                vertical = isWide
            )
        }
    }
}

@Composable
private fun ImportProgressBanner(onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = "Importing EPUB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Extracting chapters and cover",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", textAlign = TextAlign.Center)
            }
        }
    }
}
