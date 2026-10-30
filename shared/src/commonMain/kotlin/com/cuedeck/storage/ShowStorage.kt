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

/**
 * Expected platform-specific show file manager.
 */
expect class PlatformStorage {
    fun getShowDirectory(showId: String): String
    fun copyFileToManagedDir(showId: String, sourceUriOrPath: String, targetFileName: String): String
    fun writeText(filePath: String, text: String)
    fun readText(filePath: String): String?
    fun listShowIds(): List<String>
    fun deleteShow(showId: String)
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

    fun importAudioFile(showId: String, sourcePath: String, originalFileName: String): String {
        return platformStorage.copyFileToManagedDir(showId, sourcePath, originalFileName)
    }

    fun listShows(): List<String> = platformStorage.listShowIds()
}
