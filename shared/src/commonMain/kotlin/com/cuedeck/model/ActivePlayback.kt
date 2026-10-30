package com.cuedeck.model

import kotlinx.serialization.Serializable

/**
 * State representing a live playing cue.
 */
data class ActivePlayback(
    val playbackId: String,
    val cueId: String,
    val cueName: String,
    val colorHex: Long,
    val elapsedMs: Long,
    val totalMs: Long,
    val volume: Float,
    val isLooping: Boolean,
    val isFadingOut: Boolean
) {
    val progress: Float
        get() = if (totalMs > 0) (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (totalMs - elapsedMs).coerceAtLeast(0L)
}
