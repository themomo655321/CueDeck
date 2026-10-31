package com.cuedeck.importer

import android.media.MediaMetadataRetriever
import com.cuedeck.diagnostics.ErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    ): List<WebAudioResult> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        try {
            // 1. Freesound.org
            if (trimmed.contains("freesound.org")) {
                val res = importFreesound(trimmed, targetDir, onProgress)
                return@withContext if (res != null) listOf(res) else emptyList()
            }

            // 2. YouTube & YouTube Music
            if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
                return@withContext importYouTube(trimmed, targetDir, onProgress)
            }

            // 3. Direct Audio URL (MP3, WAV, AAC, M4A, OGG, etc.)
            val res = downloadDirectFile(trimmed, "Imported Sound", targetDir, onProgress)
            return@withContext if (res != null) listOf(res) else emptyList()
        } catch (e: Exception) {
            ErrorLogger.error("PlatformWebAudioImporter", "Failed to import web audio from $trimmed", e)
            emptyList()
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
        if (conn.responseCode !in 200..299) {
            ErrorLogger.error("PlatformWebAudioImporter", "Freesound HTTP ${conn.responseCode}")
            return null
        }
        val html = conn.inputStream.bufferedReader().use { it.readText() }

        // Extract title
        val titleMatch = Regex("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
        val rawTitle = titleMatch?.groupValues?.get(1)?.replace(" - Freesound", "")?.trim() ?: "Freesound Audio"

        // Extract preview audio link
        val audioMatch = Regex("""<meta\s+property=["']og:audio["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""(https://cdn\.freesound\.org/previews/[^"'\s]+\.mp3)""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""(https://[^"'\s]+\.(mp3|ogg|wav))""", RegexOption.IGNORE_CASE).find(html)

        val directAudioUrl = audioMatch?.groupValues?.get(1) ?: return null
        return downloadDirectFile(directAudioUrl, rawTitle, targetDir, onProgress, defaultExt = ".mp3")
    }

    private fun getInvidiousInstances(): List<String> {
        val list = mutableListOf("https://yt.chocolatemoo53.com")
        try {
            val conn = URL("https://api.invidious.io/instances.json?sort_by=health").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONArray(i) ?: continue
                    val domain = item.optString(0)
                    val meta = item.optJSONObject(1) ?: continue
                    if (meta.optString("type") == "https" && meta.optBoolean("api", false)) {
                        val candidate = "https://$domain"
                        if (!list.contains(candidate)) {
                            list.add(candidate)
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        // Fallbacks
        val fallbacks = listOf(
            "https://invidious.nerdvpn.de",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com"
        )
        for (fb in fallbacks) {
            if (!list.contains(fb)) list.add(fb)
        }
        return list
    }

    private fun importYouTube(
        youtubeUrl: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit
    ): List<WebAudioResult> {
        val videoId = Regex("""(?:v=|youtu\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})""").find(youtubeUrl)?.groupValues?.get(1)
        val playlistId = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(youtubeUrl)?.groupValues?.get(1)

        // 1. Single video (even if playlist param is attached to URL)
        if (videoId != null) {
            val res = importSingleYouTubeVideo(videoId, targetDir, onProgress)
            return if (res != null) listOf(res) else emptyList()
        }

        // 2. Pure playlist URL
        if (playlistId != null) {
            return importYouTubePlaylist(playlistId, targetDir, onProgress)
        }

        return emptyList()
    }

    private fun importSingleYouTubeVideo(
        videoId: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit
    ): WebAudioResult? {
        onProgress(0.05f, "Resolving YouTube audio stream...")
        val instances = getInvidiousInstances()

        for (instance in instances) {
            try {
                val apiUrl = "$instance/api/v1/videos/$videoId?local=true"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:130.0)")
                conn.connectTimeout = 7000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val title = json.optString("title", "YouTube Audio")
                    val lengthSeconds = json.optLong("lengthSeconds", 0L)
                    val expectedDurMs = lengthSeconds * 1000L

                    val formats = json.optJSONArray("adaptiveFormats")
                    if (formats != null && formats.length() > 0) {
                        var selectedUrl = ""
                        var selectedExt = ".m4a"

                        // Prefer m4a / mp4
                        for (i in 0 until formats.length()) {
                            val f = formats.getJSONObject(i)
                            val type = f.optString("type")
                            val container = f.optString("container")
                            if (type.startsWith("audio/") && (container == "m4a" || type.contains("mp4"))) {
                                val u = f.optString("url")
                                if (u.isNotEmpty()) {
                                    selectedUrl = u
                                    selectedExt = ".m4a"
                                    break
                                }
                            }
                        }

                        // Fallback to webm/opus
                        if (selectedUrl.isEmpty()) {
                            for (i in 0 until formats.length()) {
                                val f = formats.getJSONObject(i)
                                val type = f.optString("type")
                                if (type.startsWith("audio/")) {
                                    val u = f.optString("url")
                                    if (u.isNotEmpty()) {
                                        selectedUrl = u
                                        selectedExt = if (f.optString("container") == "webm") ".webm" else ".m4a"
                                        break
                                    }
                                }
                            }
                        }

                        if (selectedUrl.isNotEmpty()) {
                            // Convert to proxied endpoint on instance to prevent YouTube 403 Forbidden
                            val streamUrl = if (selectedUrl.contains("googlevideo.com")) {
                                val parsed = URL(selectedUrl)
                                "$instance${parsed.file}"
                            } else if (selectedUrl.startsWith("/")) {
                                "$instance$selectedUrl"
                            } else {
                                selectedUrl
                            }

                            onProgress(0.15f, "Downloading ($title)...")
                            val downloaded = downloadDirectFile(
                                directUrl = streamUrl,
                                title = title,
                                targetDir = targetDir,
                                onProgress = onProgress,
                                defaultExt = selectedExt,
                                expectedDurationMs = expectedDurMs
                            )
                            if (downloaded != null) {
                                return downloaded
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorLogger.warn("PlatformWebAudioImporter", "Instance $instance failed for $videoId: ${e.message}")
            }
        }
        return null
    }

    private fun importYouTubePlaylist(
        playlistId: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit
    ): List<WebAudioResult> {
        onProgress(0.05f, "Fetching playlist info...")
        val instances = getInvidiousInstances()
        val results = mutableListOf<WebAudioResult>()

        for (instance in instances) {
            try {
                val apiUrl = "$instance/api/v1/playlists/$playlistId"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val videos = json.optJSONArray("videos") ?: continue
                    val maxItems = minOf(videos.length(), 25)

                    for (i in 0 until maxItems) {
                        val v = videos.getJSONObject(i)
                        val vId = v.optString("videoId")
                        val vTitle = v.optString("title", "Track ${i + 1}")
                        if (vId.isNotEmpty()) {
                            val baseProg = i.toFloat() / maxItems
                            onProgress(baseProg, "Track ${i + 1}/$maxItems: $vTitle")
                            val res = importSingleYouTubeVideo(vId, targetDir) { p, st ->
                                val combined = baseProg + (p / maxItems)
                                onProgress(combined, "[${i + 1}/$maxItems] $st")
                            }
                            if (res != null) {
                                results.add(res)
                            }
                        }
                    }
                    if (results.isNotEmpty()) {
                        return results
                    }
                }
            } catch (e: Exception) {
                ErrorLogger.warn("PlatformWebAudioImporter", "Playlist instance $instance failed: ${e.message}")
            }
        }
        return results
    }

    private fun downloadDirectFile(
        directUrl: String,
        title: String,
        targetDir: String,
        onProgress: (Float, String) -> Unit,
        defaultExt: String = ".mp3",
        expectedDurationMs: Long = 0L
    ): WebAudioResult? {
        try {
            val conn = URL(directUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 12000
            conn.readTimeout = 25000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                ErrorLogger.error("PlatformWebAudioImporter", "Failed HTTP $responseCode for $directUrl")
                return null
            }

            val totalBytes = conn.contentLengthLong
            val cleanTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(40)
            val safeTitle = if (cleanTitle.isBlank()) "Imported_Sound" else cleanTitle
            val ext = when {
                directUrl.contains(".wav") -> ".wav"
                directUrl.contains(".m4a") || defaultExt == ".m4a" -> ".m4a"
                directUrl.contains(".ogg") -> ".ogg"
                directUrl.contains(".aac") -> ".aac"
                directUrl.contains(".webm") || defaultExt == ".webm" -> ".webm"
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

            var durationMs = expectedDurationMs
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(targetFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val parsed = durStr?.toLongOrNull() ?: 0L
                if (parsed > 0L) {
                    durationMs = parsed
                }
                retriever.release()
            } catch (ignored: Exception) {}

            return WebAudioResult(
                fileName = targetFile.name,
                title = safeTitle,
                relativePath = targetFile.name,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            ErrorLogger.error("PlatformWebAudioImporter", "Error downloading direct file: $directUrl", e)
            return null
        }
    }
}
