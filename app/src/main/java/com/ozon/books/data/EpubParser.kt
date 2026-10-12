package com.ozon.books.data

import com.ozon.books.model.Chapter
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedEpub(
    val title: String,
    val author: String,
    val coverImagePath: String?,
    val chapters: List<Chapter>,
    val extractedDirPath: String
)

object EpubParser {

    /**
     * Extracts an EPUB InputStream into target directory and parses its contents.
     */
    fun parse(epubInputStream: InputStream, targetDir: File): ParsedEpub {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // 1. Unzip EPUB contents
        unzip(epubInputStream, targetDir)

        // 2. Parse extracted EPUB
        return parseExtracted(targetDir)
    }

    /**
     * Parses an already extracted EPUB folder.
     */
    fun parseExtracted(rootDir: File): ParsedEpub {
        val opfFile = findOpfFile(rootDir)
            ?: throw IllegalStateException("Invalid EPUB: No OPF package file found")

        val opfDir = opfFile.parentFile ?: rootDir
        val docFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {
            }
        }
        val docBuilder = docFactory.newDocumentBuilder()
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

        if (coverManifestItem != null) {
            val decodedHref = URLDecoder.decode(coverManifestItem.href, "UTF-8")
            val coverFile = File(opfDir, decodedHref)
            if (coverFile.exists()) {
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
                val idref = itemref.getAttribute("idref")
                spineIdRefs.add(idref)
            }
        }

        // Parse Table of Contents if available (NCX or EPUB3 nav)
        val chapters = mutableListOf<Chapter>()
        val tocItem = if (tocId != null) {
            manifestItems[tocId]
        } else {
            manifestItems.values.find {
                it.mediaType == "application/x-dtbncx+xml" || it.properties.contains("nav")
            }
        }

        val titlesByHref = mutableMapOf<String, String>()
        if (tocItem != null) {
            val tocDecodedHref = URLDecoder.decode(tocItem.href, "UTF-8")
            val tocFile = File(opfDir, tocDecodedHref)
            if (tocFile.exists()) {
                try {
                    val tocDoc = docBuilder.parse(tocFile)
                    if (tocItem.mediaType == "application/x-dtbncx+xml") {
                        val navPoints = tocDoc.getElementsByTagName("navPoint")
                        for (i in 0 until navPoints.length) {
                            val np = navPoints.item(i) as Element
                            val label = np.getElementsByTagName("text").item(0)?.textContent?.trim()
                            val src = np.getElementsByTagName("content").item(0)?.attributes?.getNamedItem("src")?.nodeValue
                            if (!label.isNullOrBlank() && !src.isNullOrBlank()) {
                                val cleanSrc = src.substringBefore("#")
                                titlesByHref[cleanSrc] = label
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
                                val cleanHref = href.substringBefore("#")
                                titlesByHref[cleanHref] = label
                            }
                        }
                    }
                } catch (_: Exception) {
                    // TOC parsing fallback
                }
            }
        }

        // Build Chapter list from spine items
        spineIdRefs.forEachIndexed { index, idref ->
            val item = manifestItems[idref]
            if (item != null) {
                val decodedHref = URLDecoder.decode(item.href, "UTF-8")
                val chapterFile = File(opfDir, decodedHref)
                if (chapterFile.exists()) {
                    var chapterTitle = titlesByHref[item.href] ?: titlesByHref[decodedHref]
                    if (chapterTitle.isNullOrBlank()) {
                        // Try reading title from html file
                        chapterTitle = extractTitleFromHtml(chapterFile) ?: "Chapter ${index + 1}"
                    }
                    chapters.add(
                        Chapter(
                            index = index,
                            title = chapterTitle,
                            href = decodedHref,
                            absolutePath = chapterFile.absolutePath
                        )
                    )
                }
            }
        }

        // If spine items are empty, find all xhtml/html files
        if (chapters.isEmpty()) {
            val htmlFiles = opfDir.walkTopDown()
                .filter { it.isFile && (it.extension == "xhtml" || it.extension == "html") }
                .sortedBy { it.name }
                .toList()

            htmlFiles.forEachIndexed { index, file ->
                val titleFromContent = extractTitleFromHtml(file) ?: "Chapter ${index + 1}"
                chapters.add(
                    Chapter(
                        index = index,
                        title = titleFromContent,
                        href = file.name,
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

    private fun findOpfFile(rootDir: File): File? {
        val containerFile = File(rootDir, "META-INF/container.xml")
        if (containerFile.exists()) {
            try {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(containerFile)
                val rootFiles = doc.getElementsByTagName("rootfile")
                if (rootFiles.length > 0) {
                    val rootFileElem = rootFiles.item(0) as Element
                    val fullPath = rootFileElem.getAttribute("full-path")
                    if (fullPath.isNotBlank()) {
                        val opf = File(rootDir, fullPath)
                        if (opf.exists()) return opf
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: search recursively for any .opf file
        return rootDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("opf", ignoreCase = true) }
    }

    private fun extractTitleFromHtml(file: File): String? {
        return try {
            val content = file.readText()
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

    private fun unzip(inputStream: InputStream, destinationDir: File) {
        val zipIn = ZipInputStream(inputStream)
        var entry = zipIn.nextEntry
        val destCanonicalPath = destinationDir.canonicalPath

        while (entry != null) {
            val file = File(destinationDir, entry.name)
            // Path traversal security check (Zip Slip mitigation)
            if (!file.canonicalPath.startsWith(destCanonicalPath)) {
                throw SecurityException("Zip entry is outside of target directory: ${entry.name}")
            }

            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    zipIn.copyTo(output)
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
    }
}
