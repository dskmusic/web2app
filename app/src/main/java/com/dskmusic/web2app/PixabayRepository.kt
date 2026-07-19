package com.dskmusic.web2app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class PixabayImage(val previewURL: String, val largeImageURL: String)

object PixabayRepository {
    private val client = OkHttpClient()

    suspend fun search(query: String, imageType: String): List<PixabayImage> = withContext(Dispatchers.IO) {
        val url = "${ApiConfig.PIXABAY_BASE_URL}?key=${BuildConfig.PIXABAY_API_KEY}&q=${Uri.encode(query)}&image_type=$imageType&safesearch=true"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val json = JSONObject(body)
            val hits = json.getJSONArray("hits")
            val list = mutableListOf<PixabayImage>()
            for (i in 0 until hits.length()) {
                val obj = hits.getJSONObject(i)
                list.add(PixabayImage(previewURL = obj.getString("previewURL"), largeImageURL = obj.getString("largeImageURL")))
            }
            list
        }
    }
}
