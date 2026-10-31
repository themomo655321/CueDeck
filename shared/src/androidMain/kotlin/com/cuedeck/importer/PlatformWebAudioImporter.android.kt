package com.cuedeck.importer

import android.media.MediaMetadataRetriever
import com.cuedeck.diagnostics.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual class PlatformWebAudioImporter {

    actual suspend fun downloadAudio(
        url: String,
        targetDir: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): WebAudioResult? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        try {
            // 1. Freesound.org
            if (trimmed.contains("freesound.org")) {
                return@withContext importFreesound(trimmed, targetDir, onProgress)
            }

            // 2. YouTube & YouTube Music
            if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
                return@withContext importYouTube(trimmed, targetDir, onProgress)
            }

            // 3. Direct Audio URL (MP3, WAV, AAC, M4A, OGG, etc.)
            return@withContext downloadDirectFile(trimmed, "Imported Sound", targetDir, onProgress)
        } catch (e: Exception) {
            ErrorLogger.error("PlatformWebAudioImporter", "Failed to import web audio from $trimmed", e)
            null
        }
    }

    private fun importFreesound(
        pageUrl: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit
    ): WebAudioResult? {
        onProgress(0.05f, "Connecting to Freesound.org...")
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        // Extract title
        val titleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
        val rawTitle = titleMatch?.groupValues?.get(1)?.replace(" - Freesound", "")?.trim() ?: "Freesound Audio"

        // Extract preview audio link
        val audioMatch = Regex("""<meta\s+property="og:audio"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""(https://cdn\.freesound\.org/previews/[^"'\s]+\.mp3)""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""(https://[^"'\s]+\.(mp3|ogg|wav))""", RegexOption.IGNORE_CASE).find(html)

        val directAudioUrl = audioMatch?.groupValues?.get(1) ?: return null
        return downloadDirectFile(directAudioUrl, rawTitle, targetDir, onProgress, defaultExt = ".mp3")
    }

    private fun importYouTube(
        youtubeUrl: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit
    ): WebAudioResult? {
        onProgress(0.05f, "Resolving YouTube audio stream...")
        val videoId = Regex("""(?:v=|youtu\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})""").find(youtubeUrl)?.groupValues?.get(1)
            ?: return null

        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.video",
            "https://piped-api.lunar.icu"
        )

        for (instance in pipedInstances) {
            try {
                val apiUrl = "$instance/streams/$videoId"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val title = json.optString("title", "YouTube Audio")
                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null && audioStreams.length() > 0) {
                        var bestUrl = ""
                        for (i in 0 until audioStreams.length()) {
                            val stream = audioStreams.getJSONObject(i)
                            val streamUrl = stream.optString("url")
                            if (streamUrl.isNotEmpty()) {
                                bestUrl = streamUrl
                                val mime = stream.optString("mimeType")
                                if (mime.contains("mp4") || mime.contains("m4a")) break
                            }
                        }
                        if (bestUrl.isNotEmpty()) {
                            return downloadDirectFile(bestUrl, title, targetDir, onProgress, defaultExt = ".m4a")
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        // Invidious fallback
        val invidiousInstances = listOf("https://inv.nadeko.net", "https://yewtu.be", "https://invidious.jing.rocks")
        for (instance in invidiousInstances) {
            try {
                val apiUrl = "$instance/api/v1/videos/$videoId"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val title = json.optString("title", "YouTube Audio")
                    val formats = json.optJSONArray("adaptiveFormats")
                    if (formats != null) {
                        for (i in 0 until formats.length()) {
                            val f = formats.getJSONObject(i)
                            val type = f.optString("type")
                            if (type.startsWith("audio/")) {
                                val streamUrl = f.optString("url")
                                if (streamUrl.isNotEmpty()) {
                                    return downloadDirectFile(streamUrl, title, targetDir, onProgress, defaultExt = ".m4a")
                                }
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        return null
    }

    private fun downloadDirectFile(
        directUrl: String,
        title: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit,
        defaultExt: String = ".mp3"
    ): WebAudioResult? {
        val conn = URL(directUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.connect()

        val totalBytes = conn.contentLengthLong
        val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(40)
        val ext = when {
            directUrl.contains(".wav") -> ".wav"
            directUrl.contains(".m4a") -> ".m4a"
            directUrl.contains(".ogg") -> ".ogg"
            directUrl.contains(".aac") -> ".aac"
            else -> defaultExt
        }
        val fileName = "${safeTitle}_${System.currentTimeMillis() % 10000}$ext"
        val targetFile = File(targetDir, fileName)

        conn.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val frac = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                        onProgress(frac, "Downloading: ${(frac * 100).toInt()}%")
                    } else {
                        onProgress(0.5f, "Downloading: ${totalRead / 1024} KB")
                    }
                }
            }
        }

        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(targetFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (ignored: Exception) {}

        return WebAudioResult(
            fileName = targetFile.name,
            title = safeTitle,
            relativePath = targetFile.name,
            durationMs = durationMs
        )
    }
}
