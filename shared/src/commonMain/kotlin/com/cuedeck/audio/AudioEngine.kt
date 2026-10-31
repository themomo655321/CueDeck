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
     * Updates individual track volume in real time with optional smoothing.
     */
    fun setTrackVolume(playbackId: String, volume: Float, smoothingMs: Long = 150L)

    /**
     * Updates master volume in real time for all active streams.
     */
    fun setMasterVolume(volume: Float)

    /**
     * Pre-buffers the next armed cue in memory for instantaneous zero-latency playback.
     */
    fun preload(cue: Cue, basePath: String) {}

    /**
     * Extracts normalized acoustic amplitude peaks (0.0f..1.0f) for real waveform rendering.
     */
    suspend fun getWaveformPeaks(cue: Cue, basePath: String, peakCount: Int = 100): List<Float> = emptyList()

    /**
     * DJ-style scrub audio cueing: plays a short ~40-60ms audio grain at positionMs.
     */
    fun scrubAt(cue: Cue, basePath: String, positionMs: Long) {}

    /**
     * Auditions/previews a cue between startMs and endMs.
     */
    suspend fun startPreview(
        cue: Cue,
        basePath: String,
        startMs: Long,
        endMs: Long,
        onPositionUpdate: (Long) -> Unit,
        onCompleted: () -> Unit
    ) {}

    /**
     * Stops any running preview playback.
     */
    fun stopPreview() {}

    /**
     * Checks if preview playback is currently running.
     */
    fun isPreviewing(): Boolean = false

    /**
     * Releases all audio resources and worker threads.
     */
    fun release()
}
