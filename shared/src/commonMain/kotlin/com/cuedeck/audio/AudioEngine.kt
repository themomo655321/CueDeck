package com.cuedeck.audio

import com.cuedeck.model.ActivePlayback
import com.cuedeck.model.Cue
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common platform-agnostic audio playback engine.
 */
interface AudioEngine {
    /**
     * Flow of all actively running playback instances with live countdowns and volumes.
     */
    val activePlaybacks: StateFlow<List<ActivePlayback>>

    /**
     * Emits whenever a cue completes natural playback (used for Auto-Follow).
     */
    val playbackCompletedEvents: SharedFlow<Cue>

    /**
     * Starts playback of a cue.
     * @param cue The cue metadata (includes trims, loop, fades, volume).
     * @param basePath Absolute path to the show directory where audio is stored.
     * @param masterVolume Current global master volume multiplier.
     * @return Unique playback instance ID.
     */
    suspend fun play(cue: Cue, basePath: String, masterVolume: Float): String?

    /**
     * Stops a specific playback instance, optionally with a smooth fade out.
     */
    suspend fun stop(playbackId: String, fadeOutMs: Long = 0L)

    /**
     * Emergency Panic Stop: stops all active audio.
     * @param fadeOutMs 0L for immediate hard mute, or >0 for smooth panic fade.
     */
    suspend fun stopAll(fadeOutMs: Long = 0L)

    /**
     * Updates master volume in real time for all active streams.
     */
    fun setMasterVolume(volume: Float)

    /**
     * Releases all audio resources and worker threads.
     */
    fun release()
}
