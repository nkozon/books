package com.ozon.books.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterPagerLayoutTest {
    @Test
    fun middleChapterHasBoundarySlotsAndKeepsReadingPageNumbers() {
        val pager = ChapterPagerLayout(5, 1, 1, 3)
        assertEquals(7, pager.slotCount)
        assertNull(pager.pageForSlot(0))
        assertEquals(0, pager.pageForSlot(1))
        assertEquals(4, pager.pageForSlot(5))
        assertNull(pager.pageForSlot(6))
        assertEquals(5, pager.slotForPage(4))
    }

    @Test
    fun oddTabletSpreadIncludesLastPageBeforeNextChapter() {
        val pager = ChapterPagerLayout(5, 2, 1, 3)
        assertEquals(3, pager.spreadCount)
        assertEquals(5, pager.slotCount)
        assertEquals(4, pager.pageForSlot(3))
        assertEquals(3, pager.slotForPage(4))
        assertNull(pager.pageForSlot(4))
    }

    @Test
    fun bookBoundariesDoNotOfferNonexistentChapters() {
        val first = ChapterPagerLayout(2, 1, 0, 3)
        assertEquals(0, first.leadingSlots)
        assertEquals(0, first.pageForSlot(0))
        assertEquals(3, first.slotCount)
        val last = ChapterPagerLayout(2, 1, 2, 3)
        assertEquals(3, last.slotCount)
        assertEquals(1, last.pageForSlot(last.slotCount - 1))
        val only = ChapterPagerLayout(1, 2, 0, 1)
        assertEquals(1, only.slotCount)
        assertEquals(0, only.pageForSlot(0))
    }
}
