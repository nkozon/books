package com.ozon.books

import com.ozon.books.data.PathSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathSafetyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveWithin_rejectsParentTraversal() {
        val root = tempFolder.newFolder("book")
        File(root, "chapter1.xhtml").writeText("<html/>")

        assertNull(PathSafety.resolveWithin(root, "../outside.xhtml"))
        assertNull(PathSafety.resolveWithin(root, "OEBPS/../../outside.xhtml"))
    }

    @Test
    fun resolveWithin_acceptsNestedChapter() {
        val root = tempFolder.newFolder("book")
        val chapter = File(root, "OEBPS/text/chapter1.xhtml")
        chapter.parentFile?.mkdirs()
        chapter.writeText("<html/>")

        val resolved = PathSafety.resolveWithin(root, "OEBPS/text/chapter1.xhtml")

        assertNotNull(resolved)
        assertEquals(chapter.canonicalPath, resolved?.canonicalPath)
    }

    @Test
    fun resolveWithin_relativeToBaseDirectory_keepsImagesInsideRoot() {
        val root = tempFolder.newFolder("book")
        val textDir = File(root, "OEBPS/text").apply { mkdirs() }
        val image = File(root, "OEBPS/images/cover.jpg")
        image.parentFile?.mkdirs()
        image.writeText("image")

        val resolved = PathSafety.resolveWithin(root, textDir, "../images/cover.jpg")

        assertNotNull(resolved)
        assertEquals(image.canonicalPath, resolved?.canonicalPath)
        assertNull(PathSafety.resolveWithin(root, textDir, "../../../etc/passwd"))
    }

    @Test
    fun resolveWithin_preservesPlusSignsInFileNames() {
        val root = tempFolder.newFolder("book")
        val chapter = File(root, "chapter+1.xhtml")
        chapter.writeText("<html/>")

        val resolved = PathSafety.resolveWithin(root, "chapter+1.xhtml")

        assertNotNull(resolved)
        assertEquals(chapter.canonicalPath, resolved?.canonicalPath)
    }
}
