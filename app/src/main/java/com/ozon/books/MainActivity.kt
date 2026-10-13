package com.ozon.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ozon.books.data.PreferencesManager
import com.ozon.books.ui.MainApp
import com.ozon.books.ui.theme.BooksTheme

class MainActivity : ComponentActivity() {

    private val preferencesManager by lazy { PreferencesManager.getInstance(applicationContext) }
    private var pendingImportUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        handleViewIntent(intent)

        setContent {
            val themeMode by preferencesManager.themeMode.collectAsState()
            val isOledMode by preferencesManager.isOledMode.collectAsState()
            val useDynamicColor by preferencesManager.useDynamicColor.collectAsState()
            val customAccentColor by preferencesManager.customAccentColor.collectAsState()

            BooksTheme(
                themeMode = themeMode,
                isOledMode = isOledMode,
                dynamicColor = useDynamicColor,
                customAccentColor = customAccentColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(
                        pendingImportUri = pendingImportUri,
                        onImportUriConsumed = { pendingImportUri = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && intent.action == Intent.ACTION_VIEW) {
            pendingImportUri = uri
            intent.data = null
        }
    }
}
