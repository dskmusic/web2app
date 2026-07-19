package com.dskmusic.web2app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dskmusic.web2app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Checks a `version.json` published on GitHub Releases (see .github/workflows/build-release.yml)
 * and, if it's newer than the installed build, offers to download and install it.
 * No Play Store involved — this is how the app updates itself outside of it.
 */
object UpdateChecker {
    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/dskmusic/web2app/main/version.json"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("$VERSION_JSON_URL?t=${System.currentTimeMillis()}") // avoid caching
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val remoteCode = json.optInt("versionCode", -1)
                val versionName = json.optString("versionName", "")
                val apkUrl = json.optString("apkUrl", "")
                if (remoteCode <= currentVersionCode(context) || apkUrl.isBlank()) return@withContext null
                UpdateInfo(remoteCode, versionName, apkUrl)
            }
        }.getOrNull()
    }

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    /** Shared "update available" dialog, reused from both the main screen and Settings. */
    fun promptInstall(context: Context, info: UpdateInfo) {
        AlertDialog.Builder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, info.versionName))
            .setPositiveButton(R.string.update_download_positive) { _, _ -> downloadAndInstall(context, info) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadAndInstall(context: Context, info: UpdateInfo) {
        runCatching {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Web2App-update.apk")
            if (dest.exists()) dest.delete()

            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle(context.getString(R.string.update_download_notification_title))
                .setDestinationUri(Uri.fromFile(dest))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val downloadId = downloadManager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (id == downloadId) {
                        runCatching { context.unregisterReceiver(this) }
                        installApk(context, dest)
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    private fun installApk(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
