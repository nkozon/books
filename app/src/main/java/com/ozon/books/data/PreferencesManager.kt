package com.ozon.books.data

import android.content.Context
import android.content.SharedPreferences
import com.ozon.books.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isOledMode = MutableStateFlow(
        prefs.getBoolean(KEY_OLED_MODE, false)
    )
    val isOledMode: StateFlow<Boolean> = _isOledMode.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(
        prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    )
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _customAccentColor = MutableStateFlow<Int?>(
        if (prefs.contains(KEY_ACCENT_COLOR)) prefs.getInt(KEY_ACCENT_COLOR, 0) else null
    )
    val customAccentColor: StateFlow<Int?> = _customAccentColor.asStateFlow()

    private val _fontSizePx = MutableStateFlow(
        loadInitialFontSizePx()
    )
    val fontSizePx: StateFlow<Int> = _fontSizePx.asStateFlow()

    private fun loadInitialFontSizePx(): Int {
        val raw = prefs.getInt(KEY_FONT_SIZE_PX, -1)
        if (raw in 10..22) return raw
        val oldPercent = prefs.getInt(KEY_OLD_FONT_SIZE, -1)
        if (oldPercent != -1) {
            val migrated = when {
                oldPercent <= 85 -> 12
                oldPercent <= 95 -> 14
                oldPercent <= 115 -> 16
                oldPercent <= 135 -> 18
                oldPercent <= 155 -> 20
                else -> 22
            }
            prefs.edit().putInt(KEY_FONT_SIZE_PX, migrated).remove(KEY_OLD_FONT_SIZE).apply()
            return migrated
        }
        return DEFAULT_FONT_SIZE_PX
    }

    private val _fontFamily = MutableStateFlow(
        prefs.getString(KEY_FONT_FAMILY, "Serif") ?: "Serif"
    )
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _isPaginated = MutableStateFlow(
        prefs.getBoolean(KEY_IS_PAGINATED, true)
    )
    val isPaginated: StateFlow<Boolean> = _isPaginated.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setIsOledMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OLED_MODE, enabled).apply()
        _isOledMode.value = enabled
    }

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _useDynamicColor.value = enabled
    }

    fun setCustomAccentColor(color: Int?) {
        if (color == null) {
            prefs.edit().remove(KEY_ACCENT_COLOR).apply()
        } else {
            prefs.edit()
                .putInt(KEY_ACCENT_COLOR, color)
                .putBoolean(KEY_DYNAMIC_COLOR, false)
                .apply()
            _useDynamicColor.value = false
        }
        _customAccentColor.value = color
    }

    fun setFontSizePx(px: Int) {
        val safePx = px.coerceIn(10, 22)
        prefs.edit().putInt(KEY_FONT_SIZE_PX, safePx).apply()
        _fontSizePx.value = safePx
    }

    fun setFontFamily(family: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, family).apply()
        _fontFamily.value = family
    }

    fun setIsPaginated(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PAGINATED, enabled).apply()
        _isPaginated.value = enabled
    }

    companion object {
        const val DEFAULT_FONT_SIZE_PX = 16
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_OLED_MODE = "key_oled_mode"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_ACCENT_COLOR = "key_accent_color"
        private const val KEY_FONT_SIZE_PX = "key_font_size_px"
        private const val KEY_OLD_FONT_SIZE = "key_font_size"
        private const val KEY_FONT_FAMILY = "key_font_family"
        private const val KEY_IS_PAGINATED = "key_is_paginated"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
