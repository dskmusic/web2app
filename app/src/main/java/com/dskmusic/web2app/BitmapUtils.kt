package com.dskmusic.web2app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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

    /**
     * Crops a [composeAdaptive] result down to just its safe-zone content, for on-screen preview.
     * The full bled canvas is correct for IconCompat (the OS masks it down to this same region),
     * but showing that full canvas directly in an ImageView looks like a small icon with a big
     * background margin — the mask is what makes the real launcher icon fill its shape. Cropping
     * here first makes the preview match what actually ends up on the home screen.
     */
    fun previewCrop(adaptive: Bitmap): Bitmap {
        val contentSize = (adaptive.width * SAFE_ZONE_RATIO).toInt()
        val offset = (adaptive.width - contentSize) / 2
        return Bitmap.createBitmap(adaptive, offset, offset, contentSize, contentSize)
    }

    /** Turns every pixel close enough to [keyColor] fully transparent (simple chroma-key). */
    fun makeColorTransparent(source: Bitmap, keyColor: Int, tolerance: Int = 40): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)
        val kr = Color.red(keyColor)
        val kg = Color.green(keyColor)
        val kb = Color.blue(keyColor)
        val toleranceSq = tolerance * tolerance
        for (i in pixels.indices) {
            val p = pixels[i]
            val dr = Color.red(p) - kr
            val dg = Color.green(p) - kg
            val db = Color.blue(p) - kb
            if (dr * dr + dg * dg + db * db <= toleranceSq) {
                pixels[i] = p and 0x00FFFFFF
            }
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private const val SAFE_ZONE_RATIO = 0.66f
}
