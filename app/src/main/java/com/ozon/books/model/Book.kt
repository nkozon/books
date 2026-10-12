package com.ozon.books.model

import org.json.JSONObject

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverImagePath: String? = null,
    val extractedDirPath: String,
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val currentScrollProgress: Float = 0f,
    val lastReadTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val isRecentlyRead: Boolean get() = lastReadTimestamp > 0L

    val progressPercent: Int
        get() {
            if (totalChapters <= 0) return 0
            val chapterFraction = currentChapterIndex.toFloat() / totalChapters.toFloat()
            val intraChapter = (1f / totalChapters.toFloat()) * currentScrollProgress
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
            put("lastReadTimestamp", lastReadTimestamp)
            put("addedTimestamp", addedTimestamp)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): Book {
            val cover = json.optString("coverImagePath", "").takeIf { it.isNotBlank() }
            return Book(
                id = json.getString("id"),
                title = json.optString("title", "Untitled Book"),
                author = json.optString("author", "Unknown Author"),
                coverImagePath = cover,
                extractedDirPath = json.getString("extractedDirPath"),
                currentChapterIndex = json.optInt("currentChapterIndex", 0),
                totalChapters = json.optInt("totalChapters", 1).coerceAtLeast(1),
                currentScrollProgress = json.optDouble("currentScrollProgress", 0.0).toFloat(),
                lastReadTimestamp = json.optLong("lastReadTimestamp", 0L),
                addedTimestamp = json.optLong("addedTimestamp", System.currentTimeMillis())
            )
        }
    }
}
