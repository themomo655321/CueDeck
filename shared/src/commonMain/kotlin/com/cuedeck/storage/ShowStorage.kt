package com.cuedeck.storage

import com.cuedeck.model.Show
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ShowSummary(
    val id: String,
    val title: String,
    val cueCount: Int
)

@Serializable
data class AudioFileInfo(
    val fileName: String,
    val sizeBytes: Long,
    val isActiveInShow: Boolean
)

@Serializable
data class StorageInspection(
    val directoryPath: String,
    val totalSizeBytes: Long,
    val files: List<AudioFileInfo>
)

@Serializable
data class SessionState(
    val showId: String,
    val armedCueId: String? = null,
    val activeTab: Int = 0, // 0 = Cue Stack, 1 = Soundboard
    val cartPageIndex: Int = 0,
    val faderVolumes: List<Float> = listOf(1f, 1f, 1f, 1f)
)

/**
 * Expected platform-specific show file manager.
 */
expect class PlatformStorage {
    fun getShowDirectory(showId: String): String
    fun copyFileToManagedDir(
        showId: String,
        sourceUriOrPath: String,
        targetFileName: String,
        onProgress: ((Float) -> Unit)? = null
    ): String
    fun writeText(filePath: String, text: String)
    fun readText(filePath: String): String?
    fun listShowIds(): List<String>
    fun deleteShow(showId: String)
    fun saveLastActiveShowId(showId: String)
    fun getLastActiveShowId(): String?
    fun deleteAudioFile(showId: String, relativePath: String)
    fun cleanupUnusedAudio(showId: String, activeRelativePaths: Set<String>)
    fun getAudioFiles(showId: String): List<Pair<String, Long>>
    fun saveSessionState(sessionJson: String)
    fun loadSessionState(): String?
    fun openFolderInSystem(showId: String)
}

class ShowRepository(private val platformStorage: PlatformStorage) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun getBasePath(showId: String): String = platformStorage.getShowDirectory(showId)

    fun saveShow(show: Show) {
        val dir = platformStorage.getShowDirectory(show.id)
        val jsonString = json.encodeToString(show)
        platformStorage.writeText("$dir/show.json", jsonString)
        platformStorage.saveLastActiveShowId(show.id)

        // Clean up unreferenced audio files to prevent device storage bloat
        val activeAudioPaths = show.cues.map { it.audioRelativePath }.toSet()
        platformStorage.cleanupUnusedAudio(show.id, activeAudioPaths)
    }

    fun loadShow(showId: String): Show? {
        val dir = platformStorage.getShowDirectory(showId)
        val text = platformStorage.readText("$dir/show.json") ?: return null
        return try {
            json.decodeFromString<Show>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadLastActiveShow(): Show? {
        val lastId = platformStorage.getLastActiveShowId()
        if (lastId != null) {
            val show = loadShow(lastId)
            if (show != null) return show
        }
        val allShows = listShows()
        if (allShows.isNotEmpty()) {
            val first = loadShow(allShows.first())
            if (first != null) return first
        }
        return null
    }

    fun importAudioFile(
        showId: String,
        sourcePath: String,
        originalFileName: String,
        onProgress: ((Float) -> Unit)? = null
    ): String {
        return platformStorage.copyFileToManagedDir(showId, sourcePath, originalFileName, onProgress)
    }

    fun inspectStorage(show: Show): StorageInspection {
        val dir = platformStorage.getShowDirectory(show.id)
        val activeAudioPaths = show.cues.map { it.audioRelativePath.substringAfterLast("/") }.toSet()
        val filesOnDisk = platformStorage.getAudioFiles(show.id)

        var totalSize = 0L
        val fileInfos = filesOnDisk.map { (name, size) ->
            totalSize += size
            AudioFileInfo(
                fileName = name,
                sizeBytes = size,
                isActiveInShow = activeAudioPaths.contains(name)
            )
        }

        return StorageInspection(
            directoryPath = "$dir/audio",
            totalSizeBytes = totalSize,
            files = fileInfos
        )
    }

    fun purgeUnusedAudio(show: Show) {
        val activeAudioPaths = show.cues.map { it.audioRelativePath }.toSet()
        platformStorage.cleanupUnusedAudio(show.id, activeAudioPaths)
    }

    fun saveSession(session: SessionState) {
        try {
            val str = json.encodeToString(session)
            platformStorage.saveSessionState(str)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSession(): SessionState? {
        val str = platformStorage.loadSessionState() ?: return null
        return try {
            json.decodeFromString<SessionState>(str)
        } catch (e: Exception) {
            null
        }
    }

    fun openFolderInSystem(showId: String) {
        platformStorage.openFolderInSystem(showId)
    }

    fun listShows(): List<String> = platformStorage.listShowIds()
}
