package com.cuedeck.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

actual class PlatformStorage {

    private val rootDir = File(System.getProperty("user.home"), ".cuedeck/shows").apply {
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

        val sourceFile = File(sourceUriOrPath)
        Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)

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
