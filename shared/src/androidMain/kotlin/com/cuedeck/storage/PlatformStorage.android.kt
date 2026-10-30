package com.cuedeck.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

actual class PlatformStorage(private val context: Context) {

    private val rootDir = File(context.filesDir, "shows").apply {
        if (!exists()) mkdirs()
    }

    private val lastShowFile = File(context.filesDir, "last_active_show.txt")

    actual fun getShowDirectory(showId: String): String {
        val dir = File(rootDir, showId)
        if (!dir.exists()) dir.mkdirs()
        val audioDir = File(dir, "audio")
        if (!audioDir.exists()) audioDir.mkdirs()
        return dir.absolutePath
    }

    actual fun copyFileToManagedDir(showId: String, sourceUriOrPath: String, targetFileName: String): String {
        val showDir = File(getShowDirectory(showId))
        val audioDir = File(showDir, "audio")

        val sanitized = targetFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destination = File(audioDir, sanitized)

        // Deduplication: If file with same name and non-zero size exists, reuse it to avoid storage bloat
        if (destination.exists() && destination.length() > 0) {
            return "audio/$sanitized"
        }

        val inputStream: InputStream? = if (sourceUriOrPath.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(sourceUriOrPath))
        } else {
            File(sourceUriOrPath).inputStream()
        }

        inputStream?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open stream for: $sourceUriOrPath")

        return "audio/$sanitized"
    }

    actual fun writeText(filePath: String, text: String) {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    actual fun readText(filePath: String): String? {
        val file = File(filePath)
        return if (file.exists()) file.readText() else null
    }

    actual fun listShowIds(): List<String> {
        return rootDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    actual fun deleteShow(showId: String) {
        val dir = File(rootDir, showId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    actual fun saveLastActiveShowId(showId: String) {
        try {
            lastShowFile.writeText(showId)
        } catch (ignored: Exception) {}
    }

    actual fun getLastActiveShowId(): String? {
        return try {
            if (lastShowFile.exists()) lastShowFile.readText().trim() else null
        } catch (e: Exception) {
            null
        }
    }

    actual fun deleteAudioFile(showId: String, relativePath: String) {
        val showDir = File(getShowDirectory(showId))
        val file = File(showDir, relativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    actual fun cleanupUnusedAudio(showId: String, activeRelativePaths: Set<String>) {
        val showDir = File(getShowDirectory(showId))
        val audioDir = File(showDir, "audio")
        if (!audioDir.exists()) return

        audioDir.listFiles()?.forEach { file ->
            val relPath = "audio/${file.name}"
            if (!activeRelativePaths.contains(relPath)) {
                file.delete()
            }
        }
    }
}
