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

    /** Downscales [source] so its longest side is at most [maxDimension], keeping aspect ratio. No-op if already smaller. */
    fun scaleDownToMax(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
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
     * Shrinks [source] into the adaptive-icon "safe zone" (~66% of the canvas) before flattening
     * onto [bgColor]. Pinned shortcuts on Android 8+ always get reshaped/cropped into the
     * launcher's adaptive icon mask, regardless of which IconCompat factory is used — without this
     * inset, that crop eats into the actual image content. With it, the crop only eats into our
     * own uniform background bleed, which is invisible. Used for both the preview and final icon.
     */
    fun composeAdaptive(source: Bitmap, bgColor: Int): Bitmap {
        val trimmed = cropToSquare(trimUniformBorder(source))
        val size = maxOf(source.width, source.height)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)
        val contentSize = (size * SAFE_ZONE_RATIO).toInt()
        val offset = (size - contentSize) / 2
        val destRect = Rect(offset, offset, offset + contentSize, offset + contentSize)
        canvas.drawBitmap(trimmed, null, destRect, null)
        return result
    }

    /**
     * Crops away a uniform border matching [source]'s own corner color. Many picked icons (favicons
     * especially) already bake in their own padding around the real content — without this, that
     * padding stacks with composeAdaptive's own safe-zone inset and the visible content ends up
     * tiny. No-op if the image already fills its own edges.
     */
    private fun trimUniformBorder(source: Bitmap, tolerance: Int = 30): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 1 || h <= 1) return source
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val bg = pixels[0]
        val br = Color.red(bg); val bgc = Color.green(bg); val bb = Color.blue(bg); val ba = Color.alpha(bg)
        val toleranceSq = tolerance * tolerance
        fun isBackground(p: Int): Boolean {
            if (p == bg) return true
            if (ba == 0 && Color.alpha(p) == 0) return true // fully transparent pixels are indistinguishable regardless of their leftover RGB
            val dr = Color.red(p) - br
            val dg = Color.green(p) - bgc
            val db = Color.blue(p) - bb
            val da = Color.alpha(p) - ba
            return dr * dr + dg * dg + db * db + da * da <= toleranceSq
        }

        var top = 0
        top@ while (top < h) {
            for (x in 0 until w) if (!isBackground(pixels[top * w + x])) break@top
            top++
        }
        var bottom = h - 1
        bottom@ while (bottom > top) {
            for (x in 0 until w) if (!isBackground(pixels[bottom * w + x])) break@bottom
            bottom--
        }
        var left = 0
        left@ while (left < w) {
            for (y in top..bottom) if (!isBackground(pixels[y * w + left])) break@left
            left++
        }
        var right = w - 1
        right@ while (right > left) {
            for (y in top..bottom) if (!isBackground(pixels[y * w + right])) break@right
            right--
        }

        val trimmedWidth = right - left + 1
        val trimmedHeight = bottom - top + 1
        if (trimmedWidth <= 0 || trimmedHeight <= 0 || (trimmedWidth == w && trimmedHeight == h)) return source
        return Bitmap.createBitmap(source, left, top, trimmedWidth, trimmedHeight)
    }

    /**
     * Crops a [composeAdaptive] result down to just its safe-zone content, for on-screen preview,
     * so the preview matches what actually ends up visible on the home screen after the OS masks it.
     */
    fun previewCrop(adaptive: Bitmap): Bitmap {
        val contentSize = (adaptive.width * SAFE_ZONE_RATIO).toInt()
        val offset = (adaptive.width - contentSize) / 2
        return Bitmap.createBitmap(adaptive, offset, offset, contentSize, contentSize)
    }

    private const val SAFE_ZONE_RATIO = 0.66f

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
}
