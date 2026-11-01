package com.ozon.books.model

enum class ReaderTypeface(
    val id: String,
    val label: String,
    val fontFamily: String,
    val useLiterata: Boolean
) {
    LITERATA("literata", "Literata", "Serif", true),
    SERIF("serif", "Serif", "Serif", false),
    SANS("sans", "Sans", "Sans-Serif", false),
    MONO("mono", "Mono", "Monospace", false);

    companion object {
        fun fromId(value: String?): ReaderTypeface =
            entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: LITERATA

        fun fromLegacy(fontFamily: String?, useLiterata: Boolean): ReaderTypeface = when {
            fontFamily.equals("Monospace", ignoreCase = true) ||
                fontFamily.equals("Mono", ignoreCase = true) -> MONO
            fontFamily.equals("Sans-Serif", ignoreCase = true) ||
                fontFamily.equals("Sans", ignoreCase = true) -> SANS
            useLiterata -> LITERATA
            else -> SERIF
        }
    }
}

enum class ReaderBgTheme(val id: String, val label: String) {
    SYSTEM("system", "Auto"),
    WHITE("white", "White"),
    SEPIA("sepia", "Sepia"),
    DARK("dark", "Black");

    companion object {
        fun fromId(id: String?): ReaderBgTheme = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
