package com.ozon.books

import com.ozon.books.data.EpubParser
import com.ozon.books.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseSampleEpub_extractsMetadataAndChaptersCorrectly() {
        val stream = createTestEpubStream()
        val destDir = tempFolder.newFolder("test_book")

        val parsed = EpubParser.parse(stream, destDir)

        assertEquals("The Time Machine", parsed.title)
        assertEquals("H. G. Wells", parsed.author)
        assertEquals(3, parsed.chapters.size)
        assertEquals("Chapter I: Introduction", parsed.chapters[0].title)
        assertEquals("Chapter II: The Machine", parsed.chapters[1].title)
        assertEquals("Chapter III: The Time Traveller Returns", parsed.chapters[2].title)
        assertTrue(destDir.exists())
    }

    @Test
    fun bookModel_progressAndReadStatus_calculatedCorrectly() {
        val unreadBook = Book(
            id = "test1",
            title = "Test Book",
            author = "Test Author",
            extractedDirPath = "/tmp/test",
            currentChapterIndex = 0,
            totalChapters = 4,
            currentScrollProgress = 0f,
            lastReadTimestamp = 0L
        )

        assertFalse(unreadBook.isRecentlyRead)
        assertEquals(0, unreadBook.progressPercent)

        val readBook = unreadBook.copy(
            currentChapterIndex = 2,
            currentScrollProgress = 0.5f,
            lastReadTimestamp = System.currentTimeMillis()
        )

        assertTrue(readBook.isRecentlyRead)
        // 2 chapters out of 4 is 50%, plus half of chapter 3 (12.5%) -> 62%
        assertEquals(62, readBook.progressPercent)
    }

    private fun createTestEpubStream(): InputStream {
        val byteOut = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(byteOut)

        addZipEntry(zipOut, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
        addZipEntry(zipOut, "META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))

        val opfXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>The Time Machine</dc:title>
                    <dc:creator>H. G. Wells</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:identifier id="BookID">urn:uuid:12345-67890-hgwells</dc:identifier>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="style" href="style.css" media-type="text/css"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chapter3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="chapter1"/>
                    <itemref idref="chapter2"/>
                    <itemref idref="chapter3"/>
                </spine>
            </package>
        """.trimIndent()
        addZipEntry(zipOut, "OEBPS/content.opf", opfXml.toByteArray(Charsets.UTF_8))

        val ncxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                    <meta name="dtb:uid" content="urn:uuid:12345-67890-hgwells"/>
                </head>
                <docTitle><text>The Time Machine</text></docTitle>
                <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                        <navLabel><text>Chapter I: Introduction</text></navLabel>
                        <content src="chapter1.xhtml"/>
                    </navPoint>
                    <navPoint id="navPoint-2" playOrder="2">
                        <navLabel><text>Chapter II: The Machine</text></navLabel>
                        <content src="chapter2.xhtml"/>
                    </navPoint>
                    <navPoint id="navPoint-3" playOrder="3">
                        <navLabel><text>Chapter III: The Time Traveller Returns</text></navLabel>
                        <content src="chapter3.xhtml"/>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
        addZipEntry(zipOut, "OEBPS/toc.ncx", ncxXml.toByteArray(Charsets.UTF_8))

        val css = "body { font-family: serif; } p { line-height: 1.5; }"
        addZipEntry(zipOut, "OEBPS/style.css", css.toByteArray(Charsets.UTF_8))

        val ch1 = "<html><body><h1>Chapter I: Introduction</h1><p>The Time Traveller...</p></body></html>"
        val ch2 = "<html><body><h1>Chapter II: The Machine</h1><p>The thing...</p></body></html>"
        val ch3 = "<html><body><h1>Chapter III: The Time Traveller Returns</h1><p>I think...</p></body></html>"
        addZipEntry(zipOut, "OEBPS/chapter1.xhtml", ch1.toByteArray(Charsets.UTF_8))
        addZipEntry(zipOut, "OEBPS/chapter2.xhtml", ch2.toByteArray(Charsets.UTF_8))
        addZipEntry(zipOut, "OEBPS/chapter3.xhtml", ch3.toByteArray(Charsets.UTF_8))

        zipOut.finish()
        zipOut.close()
        return byteOut.toByteArray().inputStream()
    }

    private fun addZipEntry(zipOut: ZipOutputStream, entryName: String, content: ByteArray) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        zipOut.write(content)
        zipOut.closeEntry()
    }
}
