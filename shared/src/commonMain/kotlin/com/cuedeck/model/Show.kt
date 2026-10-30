package com.cuedeck.model

import kotlinx.serialization.Serializable

/**
 * Core audio cue entity. Designed to be lightweight and serializable.
 */
@Serializable
data class Cue(
    val id: String,
    val cueNumber: String,
    val name: String,
    val audioRelativePath: String,
    val originalFileName: String,
    val durationMs: Long = 0L,
    val inPointMs: Long = 0L,
    val outPointMs: Long = 0L,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f,
    val colorHex: Long = 0xFF3F51B5, // Default indigo
    val triggerMode: TriggerMode = TriggerMode.PLAY_ONCE,
    val followAction: FollowAction = FollowAction.WAIT_GO,
    val preWaitMs: Long = 0L,
    val postWaitMs: Long = 0L,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 1000L,
    val notes: String = ""
) {
    /**
     * Effective playable duration between inPoint and outPoint.
     */
    val effectiveDurationMs: Long
        get() = if (outPointMs > inPointMs) outPointMs - inPointMs else (durationMs - inPointMs).coerceAtLeast(0L)
}

/**
 * A single slot in the SoundShow cart grid.
 */
@Serializable
data class CartSlot(
    val index: Int,
    val cueId: String? = null
)

/**
 * A bank of cart pads (e.g. "SFX", "Music", "Stingers").
 */
@Serializable
data class CartBank(
    val id: String,
    val name: String,
    val rows: Int = 3,
    val cols: Int = 3,
    val slots: List<CartSlot> = emptyList()
)

/**
 * Represents a complete show project file.
 */
@Serializable
data class Show(
    val id: String,
    val title: String = "Untitled Show",
    val cues: List<Cue> = emptyList(),
    val cartBanks: List<CartBank> = emptyList(),
    val activeBankIndex: Int = 0,
    val masterVolume: Float = 1.0f,
    val armedCueId: String? = null,
    val isPerformanceLocked: Boolean = false,
    val currentViewMode: ViewMode = ViewMode.CUE_LIST
) {
    /**
     * Find a cue by its unique ID.
     */
    fun findCue(cueId: String?): Cue? = cues.find { it.id == cueId }

    /**
     * Locate the index of the armed cue.
     */
    fun armedIndex(): Int = cues.indexOfFirst { it.id == armedCueId }

    /**
     * Returns the next cue in sequence after the given cue ID.
     */
    fun nextCueAfter(cueId: String?): Cue? {
        val idx = cues.indexOfFirst { it.id == cueId }
        return if (idx in 0 until cues.lastIndex) cues[idx + 1] else null
    }
}
