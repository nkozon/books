package com.ozon.books.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ozon.books.model.ThemeMode

private val BaseDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    secondaryContainer = PurpleGrey80.copy(alpha = 0.25f),
    onSecondaryContainer = Color.White,
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930)
)

private val BaseLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    secondaryContainer = PurpleGrey40.copy(alpha = 0.15f),
    onSecondaryContainer = PurpleGrey40,
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFEF7FF),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0)
)

val LocalThemeIsDark = compositionLocalOf { false }
val LocalThemeIsOled = compositionLocalOf { false }

@Composable
fun BooksTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    customAccentColor: Int? = null,
    isOledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val effectiveDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = remember(darkTheme, effectiveDynamic, customAccentColor, isOledMode) {
        when {
            effectiveDynamic -> {
                val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                if (darkTheme) {
                    if (isOledMode) {
                        base.copy(
                            background = Color.Black,
                            surface = Color.Black,
                            surfaceContainerLow = Color(0xFF0F0F0F),
                            surfaceContainer = Color(0xFF141414),
                            surfaceContainerHigh = Color(0xFF1E1E1E)
                        )
                    } else {
                        base.copy(
                            surfaceContainerLow = Color(0xFF1D1B20),
                            surfaceContainerHigh = Color(0xFF2B2930)
                        )
                    }
                } else {
                    base.copy(
                        surfaceContainerLow = Color.White,
                        surfaceContainerHigh = Color(0xFFECE6F0)
                    )
                }
            }

            customAccentColor != null -> {
                val accent = Color(customAccentColor)
                if (darkTheme) {
                    val bg = if (isOledMode) Color.Black else Color(0xFF141218)
                    val sLow = if (isOledMode) Color(0xFF0F0F0F) else Color(0xFF1D1B20)
                    darkColorScheme(
                        primary = accent,
                        onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                        primaryContainer = accent,
                        onPrimaryContainer = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                        secondary = accent,
                        secondaryContainer = accent.copy(alpha = 0.25f),
                        onSecondaryContainer = Color.White,
                        background = bg,
                        surface = bg,
                        surfaceContainerLow = sLow,
                        surfaceContainer = sLow,
                        surfaceContainerHigh = if (isOledMode) Color(0xFF1A1A1A) else Color(0xFF2B2930)
                    )
                } else {
                    lightColorScheme(
                        primary = accent,
                        onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                        primaryContainer = accent.copy(alpha = 0.2f),
                        onPrimaryContainer = accent,
                        secondary = accent,
                        secondaryContainer = accent.copy(alpha = 0.15f),
                        onSecondaryContainer = accent,
                        background = Color(0xFFFEF7FF),
                        surface = Color(0xFFFEF7FF),
                        surfaceContainerLow = Color.White,
                        surfaceContainer = Color(0xFFF3EDF7),
                        surfaceContainerHigh = Color(0xFFECE6F0)
                    )
                }
            }

            darkTheme -> {
                if (isOledMode) {
                    BaseDarkColorScheme.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainerLow = Color(0xFF0F0F0F),
                        surfaceContainer = Color(0xFF141414),
                        surfaceContainerHigh = Color(0xFF1E1E1E)
                    )
                } else {
                    BaseDarkColorScheme
                }
            }

            else -> BaseLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeIsDark provides darkTheme,
        LocalThemeIsOled provides (darkTheme && isOledMode)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
