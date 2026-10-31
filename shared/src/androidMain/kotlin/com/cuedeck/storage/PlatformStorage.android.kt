package com.cuedeck.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.cuedeck.diagnostics.ErrorLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

actual class PlatformStorage(private val context: Context) {

    // Store in accessible external app files dir so device file managers and PC USB can browse
    private val rootDir: File by lazy {
        val ext = context.getExternalFilesDir(null)
        val target = if (ext != null) File(ext, "shows") else File(context.filesDir, "shows")
        if (!target.exists()) target.mkdirs()

        // Seamless migration from old internal files dir if files exist there
        val oldDir = File(context.filesDir, "shows")
        if (oldDir.exists() && oldDir != target) {
            try {
                oldDir.listFiles()?.forEach { showDir ->
                    val destDir = File(target, showDir.name)
                    if (!destDir.exists()) {
                        showDir.copyRecursively(destDir, overwrite = false)
                    }
                }
            } catch (e: Exception) {
                ErrorLogger.warn("PlatformStorage", "Failed to migrate old shows", e)
            }
        }
        target
    }

    private val sessionFile by lazy {
        val ext = context.getExternalFilesDir(null)
        File(ext ?: context.filesDir, "session_state.json")
    }

    private val lastShowFile by lazy {
        val ext = context.getExternalFilesDir(null)
        File(ext ?: context.filesDir, "last_active_show.txt")
    }

    private val layoutFile by lazy {
        val ext = context.getExternalFilesDir(null)
        File(ext ?: context.filesDir, "workspace_layouts.json")
    }

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

        // Deduplication: If file with same name and size > 0 exists, reuse it
        if (destination.exists() && destination.length() > 0) {
            onProgress?.invoke(1.0f)
            return "audio/$sanitized"
        }

        var totalLength = -1L
        val inputStream: InputStream? = if (sourceUriOrPath.startsWith("content://")) {
            val uri = Uri.parse(sourceUriOrPath)
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) {
                        totalLength = cursor.getLong(sizeIndex)
                    }
                }
            } catch (ignored: Exception) {}
            context.contentResolver.openInputStream(uri)
        } else {
            val f = File(sourceUriOrPath)
            totalLength = f.length()
            f.inputStream()
        }

        if (inputStream == null) {
            throw IllegalArgumentException("Cannot open stream for: $sourceUriOrPath")
        }

        inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var copied = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    if (totalLength > 0 && onProgress != null) {
                        onProgress((copied.toFloat() / totalLength).coerceIn(0f, 1f))
                    }
                }
            }
        }
        onProgress?.invoke(1.0f)
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
        } catch (e: Exception) {
            ErrorLogger.warn("PlatformStorage", "Could not save last active show ID", e)
        }
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
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(sessionJson)
        } catch (e: Exception) {
            ErrorLogger.warn("PlatformStorage", "Failed to save session state", e)
        }
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
            layoutFile.parentFile?.mkdirs()
            layoutFile.writeText(layoutJson)
        } catch (e: Exception) {
            ErrorLogger.warn("PlatformStorage", "Failed to save layout state", e)
        }
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
            val audioDir = File(showDir, "audio")
            val targetFolder = if (audioDir.exists()) audioDir else showDir

            val uri = FileProvider.getUriForFile(
                context,
                "com.cuedeck.app.fileprovider",
                targetFolder
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            ErrorLogger.warn("PlatformStorage", "Could not open folder in native file manager", e)
        }
    }
}
