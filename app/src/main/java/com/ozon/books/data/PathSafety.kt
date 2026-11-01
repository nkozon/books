package com.ozon.books.data

import java.io.File
import java.net.URLDecoder

object PathSafety {
    /**
     * Resolves an EPUB href against [baseDir], rejecting anything that escapes [root].
     * Returns null when the path is invalid or outside the root.
     */
    fun resolveWithin(root: File, baseDir: File, href: String): File? {
        val clean = href.substringBefore('#').substringBefore('?')
        if (clean.isBlank()) return null
        val decoded = try {
            // URLDecoder treats '+' as a space; preserve literal plus signs in file names.
            URLDecoder.decode(clean.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            clean
        }
        return try {
            val rootPath = root.canonicalFile.toPath()
            val target = File(baseDir, decoded).canonicalFile.toPath()
            if (target.startsWith(rootPath)) target.toFile() else null
        } catch (_: Exception) {
            null
        }
    }

    fun resolveWithin(root: File, href: String): File? = resolveWithin(root, root, href)

    fun isInside(root: File, file: File): Boolean = try {
        file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    } catch (_: Exception) {
        false
    }
}
