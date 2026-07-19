package com.dskmusic.web2app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import java.io.IOException

/**
 * Looks for a favicon / apple-touch-icon / PWA icon declared in a page's <head>, plus any larger
 * icon listed in the page's Web App Manifest (rel="manifest"), falling back to /favicon.ico.
 * Regex-based HTML scan instead of a full parser.
 */
object FaviconFetcher {
    private val client = OkHttpClient()
    private val linkTagRegex = Regex("""<link[^>]+rel=["'][^"']*icon[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val manifestLinkRegex = Regex("""<link[^>]+rel=["']manifest["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val hrefRegex = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val sizesRegex = Regex("""sizes=["'](\d+)x\d+["']""", RegexOption.IGNORE_CASE)

    suspend fun find(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val normalized = if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) pageUrl else "https://$pageUrl"
        val base = Uri.parse(normalized)
        if (base.host == null) return@withContext null
        try {
            val request = Request.Builder().url(normalized).build()
            client.newCall(request).execute().use { response ->
                val html = (if (response.isSuccessful) response.body?.string()?.take(300_000) else null)
                    ?: return@use fallback(base)

                var bestHref: String? = null
                var bestSize = 0
                extractBestLinkIcon(html)?.let { (href, size) ->
                    bestHref = resolveUrl(base, href)
                    bestSize = size
                }
                manifestUrl(html, base)?.let { manifestUrl ->
                    fetchManifestIcon(manifestUrl)?.let { (href, size) ->
                        if (size >= bestSize) {
                            bestHref = href
                            bestSize = size
                        }
                    }
                }
                bestHref ?: fallback(base)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun fallback(base: Uri): String = "${base.scheme}://${base.host}/favicon.ico"

    private fun extractBestLinkIcon(html: String): Pair<String, Int>? {
        var best: String? = null
        var bestSize = 0
        for (match in linkTagRegex.findAll(html)) {
            val tag = match.value
            val href = hrefRegex.find(tag)?.groupValues?.get(1) ?: continue
            val size = sizesRegex.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 32
            if (size >= bestSize) {
                bestSize = size
                best = href
            }
        }
        return best?.let { it to bestSize }
    }

    private fun manifestUrl(html: String, base: Uri): String? {
        val tag = manifestLinkRegex.find(html)?.value ?: return null
        val href = hrefRegex.find(tag)?.groupValues?.get(1) ?: return null
        return resolveUrl(base, href)
    }

    /** Picks the largest icon declared in the manifest's "icons" array. Src is relative to the manifest URL. */
    private fun fetchManifestIcon(manifestUrl: String): Pair<String, Int>? {
        return try {
            val request = Request.Builder().url(manifestUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.take(100_000) ?: return null
                val icons = org.json.JSONObject(body).optJSONArray("icons") ?: return null
                val manifestBase = Uri.parse(manifestUrl)
                var best: String? = null
                var bestSize = 0
                for (i in 0 until icons.length()) {
                    val icon = icons.getJSONObject(i)
                    val src = icon.optString("src").takeIf { it.isNotBlank() } ?: continue
                    val size = icon.optString("sizes").substringBefore("x").toIntOrNull() ?: 0
                    if (size >= bestSize) {
                        bestSize = size
                        best = resolveUrl(manifestBase, src)
                    }
                }
                best?.let { it to bestSize }
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        }
    }

    private fun resolveUrl(base: Uri, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> "${base.scheme}:$href"
        href.startsWith("/") -> "${base.scheme}://${base.host}$href"
        else -> "${base.scheme}://${base.host}/$href"
    }
}
// ponytail: the /favicon.ico fallback can fail to decode (BitmapFactory doesn't support classic
// multi-image .ico); sites that only expose favicon.ico with no <link rel="icon"> and no manifest
// icon will silently skip auto-detection. Add an ICO decoder if that turns out to be common in practice.
