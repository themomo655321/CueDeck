package com.cuedeck.importer

data class WebAudioResult(
    val fileName: String,
    val title: String,
    val relativePath: String,
    val durationMs: Long
)

expect class PlatformWebAudioImporter() {
    suspend fun downloadAudio(
        url: String,
        targetDir: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): List<WebAudioResult>
}
