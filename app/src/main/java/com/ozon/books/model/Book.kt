package com.ozon.books.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val chapterIndex: Int,
    val charOffset: Int,
    val chapterProgress: Float,
    val preview: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("chapterIndex", chapterIndex)
        put("charOffset", charOffset)
        put("chapterProgress", chapterProgress.toDouble())
        put("preview", preview)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Bookmark = Bookmark(
            id = json.optString("id", UUID.randomUUID().toString()),
            chapterIndex = json.optInt("chapterIndex", 0),
            charOffset = json.optInt("charOffset", 0),
            chapterProgress = json.optDouble("chapterProgress", 0.0).toFloat().coerceIn(0f, 1f),
            preview = json.optString("preview", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverImagePath: String? = null,
    val extractedDirPath: String,
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 1,
    /**
     * Progress within the current chapter, in the range 0..1.
     */
    val currentScrollProgress: Float = 0f,
    val currentCharOffset: Int = 0,
    val lastReadTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val fileHash: String? = null,
    val bookmarks: List<Bookmark> = emptyList()
) {
    val isRecentlyRead: Boolean get() = lastReadTimestamp > 0L

    val progressPercent: Int
        get() {
            if (totalChapters <= 0) return 0
            val chapterFraction = currentChapterIndex.toFloat() / totalChapters.toFloat()
            val intraChapter = currentScrollProgress.coerceIn(0f, 1f) / totalChapters.toFloat()
            val total = ((chapterFraction + intraChapter) * 100).toInt()
            return total.coerceIn(0, 100)
        }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("author", author)
            put("coverImagePath", coverImagePath ?: "")
            put("extractedDirPath", extractedDirPath)
            put("currentChapterIndex", currentChapterIndex)
            put("totalChapters", totalChapters)
            put("currentScrollProgress", currentScrollProgress.toDouble())
            put("currentCharOffset", currentCharOffset)
            put("lastReadTimestamp", lastReadTimestamp)
            put("addedTimestamp", addedTimestamp)
            put("fileHash", fileHash ?: "")
            put("bookmarks", JSONArray().apply {
                bookmarks.forEach { put(it.toJsonObject()) }
            })
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Book {
            val cover = json.optString("coverImagePath", "").takeIf { it.isNotBlank() }
            val hash = json.optString("fileHash", "").takeIf { it.isNotBlank() }
            val bookmarksJson = json.optJSONArray("bookmarks")
            val bookmarks = buildList {
                if (bookmarksJson != null) {
                    for (i in 0 until bookmarksJson.length()) {
                        runCatching { add(Bookmark.fromJsonObject(bookmarksJson.getJSONObject(i))) }
                    }
                }
            }
            return Book(
                id = json.getString("id"),
                title = json.optString("title", "Untitled Book"),
                author = json.optString("author", "Unknown Author"),
                coverImagePath = cover,
                extractedDirPath = json.getString("extractedDirPath"),
                currentChapterIndex = json.optInt("currentChapterIndex", 0),
                totalChapters = json.optInt("totalChapters", 1).coerceAtLeast(1),
                currentScrollProgress = json.optDouble("currentScrollProgress", 0.0).toFloat().coerceIn(0f, 1f),
                currentCharOffset = json.optInt("currentCharOffset", 0).coerceAtLeast(0),
                lastReadTimestamp = json.optLong("lastReadTimestamp", 0L),
                addedTimestamp = json.optLong("addedTimestamp", System.currentTimeMillis()),
                fileHash = hash,
                bookmarks = bookmarks
            )
        }
    }
}
