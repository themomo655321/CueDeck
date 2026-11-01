package com.cuedeck.importer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformWebAudioImporter {

    actual suspend fun downloadAudio(
        url: String,
        targetDir: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): List<WebAudioResult> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "Connecting...")
            val nsUrl = NSURL.URLWithString(url) ?: return@withContext emptyList()
            val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@withContext emptyList()
            onProgress(0.7f, "Saving...")
            val timestamp = NSDate().timeIntervalSince1970.toLong() % 100000
            val fileName = "web_audio_$timestamp.mp3"
            val targetPath = "$targetDir/$fileName"

            data.writeToFile(targetPath, atomically = true)
            onProgress(1.0f, "Completed")

            listOf(
                WebAudioResult(
                    fileName = fileName,
                    title = "Imported Audio $timestamp",
                    relativePath = "audio/$fileName",
                    durationMs = 10000L
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
