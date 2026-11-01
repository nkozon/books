package com.ozon.books.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderImageLayoutTest {
    @Test
    fun landscapeImageFillsPageWidthAtItsNaturalRatio() {
        assertEquals(200f, readerImageHeight(400f, 700f, 2f), 0.01f)
    }

    @Test
    fun portraitImageUsesPageHeightWithoutPushingTextOffscreen() {
        assertEquals(676f, readerImageHeight(400f, 700f, 0.5f), 0.01f)
    }

    @Test
    fun scrollModeDoesNotLimitImageHeight() {
        assertEquals(800f, readerImageHeight(400f, Float.POSITIVE_INFINITY, 0.5f), 0.01f)
    }

    @Test
    fun invalidImageDimensionsUseSquareFallback() {
        assertEquals(400f, readerImageHeight(400f, 700f, 0f), 0.01f)
    }
}
