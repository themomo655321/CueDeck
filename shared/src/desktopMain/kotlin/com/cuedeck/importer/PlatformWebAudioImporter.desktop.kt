package com.cuedeck.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            val fileName = "web_audio_${System.currentTimeMillis() % 10000}.mp3"
            val targetFile = File(targetDir, fileName)

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            WebAudioResult(
                fileName = targetFile.name,
                title = "Imported Web Audio",
                relativePath = targetFile.name,
                durationMs = 10000L
            )
        } catch (e: Exception) {
            null
        }
    }
}
