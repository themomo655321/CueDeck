package com.cuedeck.storage

import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual class PlatformStorage {

    private val rootDir = File(System.getProperty("user.home"), ".cuedeck/shows").apply {
        if (!exists()) mkdirs()
    }

    private val lastShowFile = File(rootDir, "last_active_show.txt")
    private val sessionFile = File(rootDir, "session_state.json")
    private val layoutFile = File(rootDir, "workspace_layouts.json")

    actual fun getShowDirectory(showId: String): String {
        val dir = File(rootDir, showId)
        if (!dir.exists()) dir.mkdirs()
        val audioDir = File(dir, "audio")
        if (!audioDir.exists()) audioDir.mkdirs()
        return dir.absolutePath
    }

    actual fun copyFileToManagedDir(
        showId: String,
        sourceUriOrPath: String,
        targetFileName: String,
        onProgress: ((Float) -> Unit)?
    ): String {
        val showDir = File(getShowDirectory(showId))
        val audioDir = File(showDir, "audio")
        
        val sanitized = targetFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destination = File(audioDir, sanitized)

        if (destination.exists() && destination.length() > 0) {
            onProgress?.invoke(1f)
            return "audio/$sanitized"
        }

        val sourceFile = File(sourceUriOrPath)
        Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        onProgress?.invoke(1f)

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

    actual fun getAudioFiles(showId: String): List<Pair<String, Long>> {
        val showDir = File(getShowDirectory(showId))
        val audioDir = File(showDir, "audio")
        if (!audioDir.exists()) return emptyList()

        return audioDir.listFiles()?.filter { it.isFile }?.map { it.name to it.length() } ?: emptyList()
    }

    actual fun saveSessionState(sessionJson: String) {
        try {
            sessionFile.writeText(sessionJson)
        } catch (ignored: Exception) {}
    }

    actual fun loadSessionState(): String? {
        return try {
            if (sessionFile.exists()) sessionFile.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    actual fun saveLayoutState(layoutJson: String) {
        try {
            layoutFile.writeText(layoutJson)
        } catch (ignored: Exception) {}
    }

    actual fun loadLayoutState(): String? {
        return try {
            if (layoutFile.exists()) layoutFile.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    actual fun openFolderInSystem(showId: String) {
        try {
            val showDir = File(getShowDirectory(showId))
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(showDir)
            }
        } catch (ignored: Exception) {}
    }
}
