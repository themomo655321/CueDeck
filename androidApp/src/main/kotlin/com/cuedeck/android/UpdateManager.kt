package com.cuedeck.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val downloadUrl: String,
    val releaseName: String,
    val updatedAt: String,
    val sizeBytes: Long
)

object UpdateManager {

    private const val PREFS_NAME = "cuedeck_update_prefs"
    private const val KEY_LAST_UPDATE_TIMESTAMP = "last_installed_updated_at"
    private const val RELEASES_API_URL = "https://api.github.com/repos/themomo655321/CueDeck/releases"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "CueDeck-Android-App")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != 200) return@withContext null

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            val releaseObj = if (jsonStr.startsWith("[")) {
                val array = JSONArray(jsonStr)
                if (array.length() == 0) return@withContext null
                array.getJSONObject(0)
            } else {
                JSONObject(jsonStr)
            }

            val releaseName = releaseObj.optString("name", "CueDeck Update")
            val assets = releaseObj.optJSONArray("assets") ?: return@withContext null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    val downloadUrl = asset.getString("browser_download_url")
                    val updatedAt = asset.optString("updated_at", "")
                    val size = asset.optLong("size", 0L)

                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastTimestamp = prefs.getString(KEY_LAST_UPDATE_TIMESTAMP, "")

                    // Check if there is a newer build timestamp
                    if (updatedAt.isNotBlank() && updatedAt != lastTimestamp) {
                        return@withContext UpdateInfo(
                            downloadUrl = downloadUrl,
                            releaseName = releaseName,
                            updatedAt = updatedAt,
                            sizeBytes = size
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }
        val destinationApk = File(updatesDir, "CueDeck-update.apk")
        if (destinationApk.exists()) destinationApk.delete()

        val url = URL(updateInfo.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "CueDeck-Android-App")
        conn.instanceFollowRedirects = true

        val totalLength = conn.contentLength.toFloat().coerceAtLeast(1f)
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(destinationApk).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress((downloaded / totalLength).coerceIn(0f, 1f))
                }
            }
        }

        // Save last installed timestamp
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_UPDATE_TIMESTAMP, updateInfo.updatedAt).apply()

        // Launch Android System Package Installer
        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return@withContext
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "com.cuedeck.app.fileprovider",
                destinationApk
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        }
    }
}
