package com.cuedeck.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformStorage {

    private val fileManager = NSFileManager.defaultManager

    private val rootDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docDir = paths.firstOrNull() as? String ?: ""
        val target = "$docDir/shows"
        if (!fileManager.fileExistsAtPath(target)) {
            fileManager.createDirectoryAtPath(target, withIntermediateDirectories = true, attributes = null, error = null)
        }
        target
    }

    private val lastShowFile by lazy { "$rootDir/last_active_show.txt" }
    private val sessionFile by lazy { "$rootDir/session_state.json" }
    private val layoutFile by lazy { "$rootDir/workspace_layouts.json" }

    actual fun getShowDirectory(showId: String): String {
        val dir = "$rootDir/$showId"
        val audioDir = "$dir/audio"
        if (!fileManager.fileExistsAtPath(audioDir)) {
            fileManager.createDirectoryAtPath(audioDir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return dir
    }

    actual fun copyFileToManagedDir(
        showId: String,
        sourceUriOrPath: String,
        targetFileName: String,
        onProgress: ((Float) -> Unit)?
    ): String {
        val showDir = getShowDirectory(showId)
        val sanitized = targetFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destination = "$showDir/audio/$sanitized"

        if (fileManager.fileExistsAtPath(destination)) {
            onProgress?.invoke(1f)
            return "audio/$sanitized"
        }

        fileManager.copyItemAtPath(sourceUriOrPath, destination, error = null)
        onProgress?.invoke(1f)
        return "audio/$sanitized"
    }

    actual fun writeText(filePath: String, text: String) {
        try {
            val parent = (filePath as NSString).stringByDeletingLastPathComponent
            if (!fileManager.fileExistsAtPath(parent)) {
                fileManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
            }
            val nsStr = NSString.create(string = text)
            nsStr.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (ignored: Exception) {}
    }

    actual fun readText(filePath: String): String? {
        return try {
            if (!fileManager.fileExistsAtPath(filePath)) return null
            NSString.create(contentsOfFile = filePath, encoding = NSUTF8StringEncoding, error = null) as? String
        } catch (e: Exception) {
            null
        }
    }

    actual fun listShowIds(): List<String> {
        val contents = fileManager.contentsOfDirectoryAtPath(rootDir, error = null) as? List<*> ?: return emptyList()
        return contents.filterIsInstance<String>().filter { name ->
            !name.startsWith(".") && !name.endsWith(".txt") && !name.endsWith(".json")
        }
    }

    actual fun deleteShow(showId: String) {
        val dir = "$rootDir/$showId"
        if (fileManager.fileExistsAtPath(dir)) {
            fileManager.removeItemAtPath(dir, error = null)
        }
    }

    actual fun saveLastActiveShowId(showId: String) {
        try {
            writeText(lastShowFile, showId)
        } catch (ignored: Exception) {}
    }

    actual fun getLastActiveShowId(): String? {
        return try {
            readText(lastShowFile)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    actual fun deleteAudioFile(showId: String, relativePath: String) {
        val full = "${getShowDirectory(showId)}/$relativePath"
        if (fileManager.fileExistsAtPath(full)) {
            fileManager.removeItemAtPath(full, error = null)
        }
    }

    actual fun cleanupUnusedAudio(showId: String, activeRelativePaths: Set<String>) {
        val audioDir = "${getShowDirectory(showId)}/audio"
        val contents = fileManager.contentsOfDirectoryAtPath(audioDir, error = null) as? List<*> ?: return
        for (item in contents.filterIsInstance<String>()) {
            val relPath = "audio/$item"
            if (!activeRelativePaths.contains(relPath)) {
                fileManager.removeItemAtPath("$audioDir/$item", error = null)
            }
        }
    }

    actual fun getAudioFiles(showId: String): List<Pair<String, Long>> {
        val audioDir = "${getShowDirectory(showId)}/audio"
        val contents = fileManager.contentsOfDirectoryAtPath(audioDir, error = null) as? List<*> ?: return emptyList()
        return contents.filterIsInstance<String>().map { name ->
            val path = "$audioDir/$name"
            val attrs = fileManager.attributesOfItemAtPath(path, error = null)
            val size = (attrs?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            name to size
        }
    }

    actual fun saveSessionState(sessionJson: String) {
        try {
            writeText(sessionFile, sessionJson)
        } catch (ignored: Exception) {}
    }

    actual fun loadSessionState(): String? {
        return try {
            readText(sessionFile)
        } catch (e: Exception) {
            null
        }
    }

    actual fun saveLayoutState(layoutJson: String) {
        try {
            writeText(layoutFile, layoutJson)
        } catch (ignored: Exception) {}
    }

    actual fun loadLayoutState(): String? {
        return try {
            readText(layoutFile)
        } catch (e: Exception) {
            null
        }
    }

    actual fun openFolderInSystem(showId: String) {
        // Sandboxed on iOS; files can be accessed via iOS Files app Documents sharing
    }
}
