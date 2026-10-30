package com.cuedeck.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

actual class PlatformStorage(private val context: Context) {

    private val rootDir = File(context.filesDir, "shows").apply {
        if (!exists()) mkdirs()
    }

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
        val uniqueName = "${UUID.randomUUID().toString().take(8)}_$sanitized"
        val destination = File(audioDir, uniqueName)

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

        return "audio/$uniqueName"
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
}
