package com.ozon.books.ui.reader

import androidx.compose.ui.text.AnnotatedString
import com.ozon.books.model.ReaderTypeface

sealed class ChapterElement {
    data class Paragraph(
        val text: AnnotatedString,
        val isFirstLineIndented: Boolean = true
    ) : ChapterElement()

    data class Heading(val text: AnnotatedString, val level: Int) : ChapterElement()

    data class Image(val absolutePath: String, val aspectRatio: Float = 1f) : ChapterElement()
}

data class ChapterPage(
    val elements: List<ChapterElement>,
    val startCharOffset: Int = 0,
    val endCharOffset: Int = 0
)

data class ReaderSearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val snippet: String
)

data class ReaderTypography(
    val fontSizePx: Int = 16,
    val lineHeightMultiplier: Float = 1.4f,
    val typeface: ReaderTypeface = ReaderTypeface.LITERATA,
    val isJustified: Boolean = false
)

data class ReaderLayout(
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val imageWidthDp: Int = 0
) {
    val isValid: Boolean get() = widthDp > 0 && heightDp > 0
}

/** Pager slots before/after the chapter allow a swipe to cross a chapter boundary. */
internal data class ChapterPagerLayout(
    val readingPageCount: Int,
    val pagesPerSpread: Int,
    val chapterIndex: Int,
    val chapterCount: Int
) {
    val leadingSlots: Int get() = if (chapterIndex > 0) 1 else 0
    val spreadCount: Int get() = ((readingPageCount + pagesPerSpread - 1) / pagesPerSpread).coerceAtLeast(1)
    val slotCount: Int get() = leadingSlots + spreadCount + if (chapterIndex < chapterCount - 1) 1 else 0
    fun slotForPage(pageIndex: Int): Int = leadingSlots + (pageIndex / pagesPerSpread).coerceIn(0, spreadCount - 1)
    fun pageForSlot(slot: Int): Int? = (slot - leadingSlots)
        .takeIf { it in 0 until spreadCount }
        ?.times(pagesPerSpread)
}

/** Size an image to page width, reserving vertical padding and preserving its ratio. */
internal fun readerImageHeight(width: Float, pageHeight: Float, aspectRatio: Float): Float {
    val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    return minOf(width / safeRatio, (pageHeight - 24f).coerceAtLeast(1f)).coerceAtLeast(1f)
}
