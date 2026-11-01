package com.ozon.books.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small disk-image loader that downsamples covers and inline chapter images to the
 * requested size before decoding, keeping large book images out of memory.
 */
object BookImageLoader {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val width = reqWidth.coerceAtLeast(1)
        val height = reqHeight.coerceAtLeast(1)
        val key = "$path:$width:$height"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = decodeSampled(path, width, height)
            if (bitmap != null) cache.put(key, bitmap)
            bitmap
        }
    }

    private fun decodeSampled(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= reqWidth &&
                bounds.outHeight / (sampleSize * 2) >= reqHeight
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun AsyncBookImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthPx: Int = 600,
    targetHeightPx: Int = 900
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = path,
        key2 = targetWidthPx,
        key3 = targetHeightPx
    ) {
        value = BookImageLoader.load(path, targetWidthPx, targetHeightPx)
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}
