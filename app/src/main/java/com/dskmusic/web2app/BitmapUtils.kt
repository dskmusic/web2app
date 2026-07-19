package com.dskmusic.web2app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object BitmapUtils {
    private val client = OkHttpClient()

    suspend fun download(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: IOException) {
            null
        }
    }

    fun cropToSquare(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        return Bitmap.createBitmap(source, x, y, size, size)
    }

    /** Flattens [source] onto an opaque [bgColor], or keeps alpha when [bgColor] is null (transparent). */
    fun compose(source: Bitmap, bgColor: Int?): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (bgColor != null) canvas.drawColor(bgColor)
        canvas.drawBitmap(source, 0f, 0f, null)
        return result
    }

    /**
     * Same as [compose], but shrinks [source] into the adaptive-icon "safe zone" (~66% of the
     * canvas) before flattening. Android launchers crop the outer bleed of any bitmap passed to
     * IconCompat.createWithAdaptiveBitmap, so without this inset a full-bleed image (or one with
     * its own transparent padding) ends up looking zoomed in with the background ring cropped
     * away. Used for both the on-screen preview and the actual generated icon so they match.
     */
    fun composeAdaptive(source: Bitmap, bgColor: Int?): Bitmap {
        val size = maxOf(source.width, source.height)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (bgColor != null) canvas.drawColor(bgColor)
        val contentSize = (size * SAFE_ZONE_RATIO).toInt()
        val offset = (size - contentSize) / 2
        val destRect = Rect(offset, offset, offset + contentSize, offset + contentSize)
        canvas.drawBitmap(source, null, destRect, null)
        return result
    }

    private const val SAFE_ZONE_RATIO = 0.66f
}
