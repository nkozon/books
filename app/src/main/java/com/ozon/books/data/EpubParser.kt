package com.ozon.books.data

import com.ozon.books.model.Chapter
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEpub(
    val title: String,
    val author: String,
    val coverImagePath: String?,
    val chapters: List<Chapter>,
    val extractedDirPath: String
)

object EpubParser {

    private const val MAX_ENTRY_COUNT = 10_000
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L
    private const val MAX_SINGLE_ENTRY_BYTES = 128L * 1024L * 1024L
    private const val COPY_BUFFER_SIZE = 32 * 1024

    /**
     * Extracts an EPUB file into target directory and parses its contents.
     * [cancellationCheck] is invoked while copying entries so a cancelled import stops promptly.
     */
    fun parse(
        epubFile: File,
        targetDir: File,
        cancellationCheck: () -> Unit = {}
    ): ParsedEpub {
        FileInputStream(epubFile).use { input ->
            return parse(input, targetDir, cancellationCheck)
        }
    }

    /**
     * Extracts an EPUB InputStream into target directory and parses its contents.
     * The supplied stream is owned and closed by this method.
     */
    fun parse(
        epubInputStream: InputStream,
        targetDir: File,
        cancellationCheck: () -> Unit = {}
    ): ParsedEpub {
        try {
            cancellationCheck()
            if (!targetDir.exists()) targetDir.mkdirs()
            unzip(epubInputStream, targetDir, cancellationCheck)
            cancellationCheck()
            return parseExtracted(targetDir)
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        }
    }

    /**
     * Parses an already extracted EPUB folder.
     */
    fun parseExtracted(
        rootDir: File,
        cancellationCheck: () -> Unit = {}
    ): ParsedEpub {
        cancellationCheck()
        val opfFile = findOpfFile(rootDir)
            ?: throw IllegalStateException("Invalid EPUB: No OPF package file found")

        val opfDir = opfFile.parentFile ?: rootDir
        val docBuilder = secureDocumentBuilderFactory().newDocumentBuilder()
        val doc = docBuilder.parse(opfFile)

        // Parse Metadata
        val metadataList = doc.getElementsByTagName("metadata")
        var title = "Untitled Book"
        var author = "Unknown Author"
        var coverId: String? = null

        if (metadataList.length > 0) {
            val metaNode = metadataList.item(0) as Element
            val childNodes = metaNode.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val elem = node as Element
                    val localName = elem.localName ?: elem.nodeName
                    when {
                        localName.equals("title", ignoreCase = true) -> {
                            val text = elem.textContent?.trim()
                            if (!text.isNullOrBlank()) title = text
                        }
                        localName.equals("creator", ignoreCase = true) -> {
                            val text = elem.textContent?.trim()
                            if (!text.isNullOrBlank()) author = text
                        }
                        localName.equals("meta", ignoreCase = true) -> {
                            if (elem.getAttribute("name").equals("cover", ignoreCase = true)) {
                                coverId = elem.getAttribute("content")
                            }
                        }
                    }
                }
            }
        }

        // Parse Manifest
        data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
        val manifestItems = mutableMapOf<String, ManifestItem>()
        val manifestNodeList = doc.getElementsByTagName("manifest")
        if (manifestNodeList.length > 0) {
            val manifestElement = manifestNodeList.item(0) as Element
            val items = manifestElement.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val itemElem = items.item(i) as Element
                val id = itemElem.getAttribute("id")
                val href = itemElem.getAttribute("href")
                val mediaType = itemElem.getAttribute("media-type")
                val properties = itemElem.getAttribute("properties")
                manifestItems[id] = ManifestItem(id, href, mediaType, properties)
                if (properties.contains("cover-image") && coverId == null) {
                    coverId = id
                }
            }
        }

        // Determine Cover Image
        var coverImagePath: String? = null
        val coverManifestItem = if (coverId != null) {
            manifestItems[coverId]
        } else {
            manifestItems.values.find {
                it.mediaType.startsWith("image/") &&
                    (it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true))
            }
        }

        if (coverManifestItem != null &&
            coverManifestItem.mediaType.startsWith("image/") &&
            !coverManifestItem.mediaType.contains("svg", ignoreCase = true)
        ) {
            val coverFile = PathSafety.resolveWithin(rootDir, opfDir, coverManifestItem.href)
            if (coverFile != null && coverFile.exists()) {
                coverImagePath = coverFile.absolutePath
            }
        }

        // Parse Spine
        val spineIdRefs = mutableListOf<String>()
        var tocId: String? = null
        val spineNodeList = doc.getElementsByTagName("spine")
        if (spineNodeList.length > 0) {
            val spineElement = spineNodeList.item(0) as Element
            tocId = spineElement.getAttribute("toc").takeIf { it.isNotBlank() }
            val itemrefs = spineElement.getElementsByTagName("itemref")
            for (i in 0 until itemrefs.length) {
                val itemref = itemrefs.item(i) as Element
                if (itemref.getAttribute("linear").equals("no", ignoreCase = true)) continue
                val idref = itemref.getAttribute("idref")
                if (idref.isNotBlank()) spineIdRefs.add(idref)
            }
        }

        // Parse Table of Contents if available (NCX or EPUB3 nav)
        val tocItem = if (tocId != null) {
            manifestItems[tocId]
        } else {
            manifestItems.values.find {
                it.mediaType == "application/x-dtbncx+xml" || it.properties.contains("nav")
            }
        }

        // Keyed by canonical path so hrefs relative to the TOC file resolve correctly.
        val titlesByPath = mutableMapOf<String, String>()
        if (tocItem != null) {
            val tocFile = PathSafety.resolveWithin(rootDir, opfDir, tocItem.href)
            if (tocFile != null && tocFile.exists()) {
                try {
                    val tocDoc = docBuilder.parse(tocFile)
                    val tocDir = tocFile.parentFile ?: opfDir
                    if (tocItem.mediaType == "application/x-dtbncx+xml") {
                        val navPoints = tocDoc.getElementsByTagName("navPoint")
                        for (i in 0 until navPoints.length) {
                            val np = navPoints.item(i) as Element
                            val label = np.getElementsByTagName("text").item(0)?.textContent?.trim()
                            val src = np.getElementsByTagName("content").item(0)?.attributes?.getNamedItem("src")?.nodeValue
                            if (!label.isNullOrBlank() && !src.isNullOrBlank()) {
                                PathSafety.resolveWithin(rootDir, tocDir, src)?.let { target ->
                                    titlesByPath[target.canonicalPath] = label
                                }
                            }
                        }
                    } else {
                        // EPUB 3 nav.xhtml
                        val links = tocDoc.getElementsByTagName("a")
                        for (i in 0 until links.length) {
                            val a = links.item(i) as Element
                            val label = a.textContent?.trim()
                            val href = a.getAttribute("href")
                            if (!label.isNullOrBlank() && href.isNotBlank()) {
                                PathSafety.resolveWithin(rootDir, tocDir, href)?.let { target ->
                                    titlesByPath[target.canonicalPath] = label
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // TOC parsing fallback: chapter titles are read from the HTML files.
                }
            }
        }

        // Build Chapter list from spine items
        val chapters = mutableListOf<Chapter>()
        spineIdRefs.forEachIndexed { index, idref ->
            cancellationCheck()
            val item = manifestItems[idref]
            if (item != null) {
                val chapterFile = PathSafety.resolveWithin(rootDir, opfDir, item.href)
                if (chapterFile != null && chapterFile.exists()) {
                    val chapterTitle = titlesByPath[chapterFile.canonicalPath]
                        ?: extractTitleFromHtml(chapterFile)
                        ?: "Chapter ${index + 1}"
                    chapters.add(
                        Chapter(
                            index = chapters.size,
                            title = chapterTitle,
                            href = chapterFile.relativeTo(rootDir).path,
                            absolutePath = chapterFile.absolutePath
                        )
                    )
                }
            }
        }

        // If spine items are empty, find all content xhtml/html files.
        if (chapters.isEmpty()) {
            val htmlFiles = opfDir.walkTopDown()
                .filter { it.isFile && (it.extension == "xhtml" || it.extension == "html") }
                .filterNot { it.name.equals("nav.xhtml", true) || it.name.equals("toc.xhtml", true) }
                .filterNot { it.name.startsWith("toc", ignoreCase = true) && it.name.endsWith(".html", true) }
                .sortedWith(compareByNaturalName())
                .toList()

            htmlFiles.forEachIndexed { index, file ->
                cancellationCheck()
                val titleFromContent = extractTitleFromHtml(file) ?: "Chapter ${index + 1}"
                chapters.add(
                    Chapter(
                        index = index,
                        title = titleFromContent,
                        href = file.relativeTo(rootDir).path,
                        absolutePath = file.absolutePath
                    )
                )
            }
        }

        return ParsedEpub(
            title = title,
            author = author,
            coverImagePath = coverImagePath,
            chapters = chapters,
            extractedDirPath = rootDir.absolutePath
        )
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // Some Android parser implementations (Harmony) throw for these setters even
        // though the defaults already match; guard so imports work on-device.
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        fun feature(name: String, enabled: Boolean) {
            runCatching { factory.setFeature(name, enabled) }
        }
        feature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        feature("http://apache.org/xml/features/disallow-doctype-decl", true)
        feature("http://xml.org/sax/features/external-general-entities", false)
        feature("http://xml.org/sax/features/external-parameter-entities", false)
        feature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return factory
    }

    private fun findOpfFile(rootDir: File): File? {
        val containerFile = File(rootDir, "META-INF/container.xml")
        if (containerFile.exists()) {
            try {
                val doc = secureDocumentBuilderFactory().newDocumentBuilder().parse(containerFile)
                val rootFiles = doc.getElementsByTagName("rootfile")
                if (rootFiles.length > 0) {
                    val rootFileElem = rootFiles.item(0) as Element
                    val fullPath = rootFileElem.getAttribute("full-path")
                    if (fullPath.isNotBlank()) {
                        val opf = PathSafety.resolveWithin(rootDir, fullPath)
                        if (opf != null && opf.exists()) return opf
                    }
                }
            } catch (_: Exception) {
                // Fall through to the recursive search below.
            }
        }

        // Fallback: search recursively for any .opf file
        return rootDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("opf", ignoreCase = true) }
    }

    private fun extractTitleFromHtml(file: File): String? {
        return try {
            val content = readTextLimited(file)
            // Look for <title>...</title>
            val titleRegex = "<title[^>]*>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
            val titleMatch = titleRegex.find(content)?.groupValues?.get(1)?.trim()
            if (!titleMatch.isNullOrBlank()) return titleMatch

            // Look for first <h1>...</h1> or <h2>...</h2>
            val headingRegex = "<h[1-2][^>]*>(.*?)</h[1-2]>".toRegex(RegexOption.IGNORE_CASE)
            val headingMatch = headingRegex.find(content)?.groupValues?.get(1)
                ?.replace("<[^>]+>".toRegex(), "")?.trim()
            if (!headingMatch.isNullOrBlank()) headingMatch else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readTextLimited(file: File, maxBytes: Int = 512 * 1024): String {
        val limit = minOf(maxBytes.toLong(), file.length()).toInt().coerceAtLeast(1)
        return file.inputStream().use { input ->
            val buffer = ByteArray(limit)
            var total = 0
            while (total < limit) {
                val read = input.read(buffer, total, limit - total)
                if (read <= 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
    }

    private fun compareByNaturalName(): Comparator<File> = Comparator { a, b ->
        val aParts = a.name.split(Regex("(\\d+)"))
        val bParts = b.name.split(Regex("(\\d+)"))
        val size = minOf(aParts.size, bParts.size)
        for (i in 0 until size) {
            val aPart = aParts[i]
            val bPart = bParts[i]
            val cmp = if (i % 2 == 1) {
                aPart.toLongOrNull().orZero().compareTo(bPart.toLongOrNull().orZero())
            } else {
                aPart.compareTo(bPart, ignoreCase = true)
            }
            if (cmp != 0) return@Comparator cmp
        }
        aParts.size.compareTo(bParts.size)
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun unzip(
        inputStream: InputStream,
        destinationDir: File,
        cancellationCheck: () -> Unit
    ) {
        val destPath = destinationDir.canonicalFile.toPath()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                cancellationCheck()
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw SecurityException("EPUB contains too many entries")
                }
                val target = destPath.resolve(entry.name).normalize()
                if (!target.startsWith(destPath)) {
                    throw SecurityException("Zip entry is outside of target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.toFile().mkdirs()
                } else {
                    val parent = target.parent ?: throw SecurityException("Invalid zip entry: ${entry.name}")
                    parent.toFile().mkdirs()
                    var entryBytes = 0L
                    target.toFile().outputStream().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            cancellationCheck()
                            val read = zipIn.read(buffer)
                            if (read <= 0) break
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_SINGLE_ENTRY_BYTES ||
                                totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES
                            ) {
                                throw SecurityException("EPUB is too large when extracted")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
