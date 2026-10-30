package com.cuedeck.audio

import com.cuedeck.model.ActivePlayback
import com.cuedeck.model.Cue
import com.cuedeck.model.TriggerMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.*
import kotlin.math.log10
import kotlin.math.max

/**
 * High-performance JavaSound playback engine for Desktop runner.
 */
class DesktopAudioEngine : AudioEngine {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activePlaybacks = MutableStateFlow<List<ActivePlayback>>(emptyList())
    override val activePlaybacks: StateFlow<List<ActivePlayback>> = _activePlaybacks.asStateFlow()

    private val _playbackCompletedEvents = MutableSharedFlow<Cue>()
    override val playbackCompletedEvents: SharedFlow<Cue> = _playbackCompletedEvents.asSharedFlow()

    private var currentMasterVolume = 1.0f

    private class TrackInstance(
        val playbackId: String,
        val cue: Cue,
        val clip: Clip,
        val startMicroseconds: Long,
        val endMicroseconds: Long,
        var currentVolume: Float,
        var isFadingOut: Boolean = false,
        var fadeJob: Job? = null
    )

    private val activeTracks = ConcurrentHashMap<String, TrackInstance>()

    init {
        // Polling coroutine for UI active track progress (updates at 20Hz / 50ms)
        engineScope.launch {
            while (isActive) {
                delay(50)
                updateActiveState()
            }
        }
    }

    override suspend fun play(cue: Cue, basePath: String, masterVolume: Float): String? = withContext(Dispatchers.IO) {
        currentMasterVolume = masterVolume
        val file = File(basePath, cue.audioRelativePath)
        if (!file.exists()) {
            println("[DesktopAudioEngine] Error: File not found: ${file.absolutePath}")
            return@withContext null
        }

        try {
            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val clip = AudioSystem.getClip()
            clip.open(audioInputStream)

            val playbackId = UUID.randomUUID().toString()
            val startUs = cue.inPointMs * 1000L
            val endUs = if (cue.outPointMs > cue.inPointMs) cue.outPointMs * 1000L else clip.microsecondLength

            clip.microsecondPosition = startUs

            val initialVolume = if (cue.fadeInMs > 0) 0.001f else (cue.volume * currentMasterVolume)
            applyVolumeToClip(clip, initialVolume)

            val track = TrackInstance(
                playbackId = playbackId,
                cue = cue,
                clip = clip,
                startMicroseconds = startUs,
                endMicroseconds = endUs,
                currentVolume = initialVolume
            )

            activeTracks[playbackId] = track

            if (cue.triggerMode == TriggerMode.LOOP) {
                clip.setLoopPoints((startUs / 1000L).toInt() * 44, (endUs / 1000L).toInt() * 44) // approximate frame
                clip.loop(Clip.LOOP_CONTINUOUSLY)
            } else {
                clip.start()
            }

            // Monitor end point & fade-in
            engineScope.launch {
                // Fade in if configured
                if (cue.fadeInMs > 0) {
                    val targetVol = cue.volume * currentMasterVolume
                    val steps = (cue.fadeInMs / 50L).coerceAtLeast(1)
                    val stepDelta = (targetVol - 0.001f) / steps
                    for (i in 0 until steps) {
                        delay(50)
                        if (!track.isFadingOut && track.clip.isOpen) {
                            track.currentVolume = (track.currentVolume + stepDelta).coerceIn(0f, targetVol)
                            applyVolumeToClip(track.clip, track.currentVolume)
                        }
                    }
                }

                // Wait until end point reached
                while (track.clip.isOpen && track.clip.isRunning) {
                    delay(30)
                    if (cue.triggerMode != TriggerMode.LOOP && track.clip.microsecondPosition >= endUs) {
                        break
                    }
                }

                // If finished naturally
                if (!track.isFadingOut) {
                    stopTrackInternal(playbackId, immediate = true)
                    _playbackCompletedEvents.emit(cue)
                }
            }

            playbackId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun stop(playbackId: String, fadeOutMs: Long) {
        val track = activeTracks[playbackId] ?: return
        if (fadeOutMs <= 0) {
            stopTrackInternal(playbackId, immediate = true)
        } else {
            track.isFadingOut = true
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (fadeOutMs / 50L).coerceAtLeast(1)
                val stepDelta = startVol / steps
                for (i in 0 until steps) {
                    delay(50)
                    if (track.clip.isOpen) {
                        track.currentVolume = (track.currentVolume - stepDelta).coerceAtLeast(0f)
                        applyVolumeToClip(track.clip, track.currentVolume)
                    }
                }
                stopTrackInternal(playbackId, immediate = true)
            }
        }
    }

    override suspend fun stopAll(fadeOutMs: Long) {
        val keys = activeTracks.keys.toList()
        for (key in keys) {
            stop(key, fadeOutMs)
        }
    }

    override fun setTrackVolume(playbackId: String, volume: Float, smoothingMs: Long) {
        val track = activeTracks[playbackId] ?: return
        if (track.isFadingOut) return

        val targetVol = (volume * currentMasterVolume).coerceIn(0f, 1.5f)
        if (smoothingMs <= 0L) {
            track.fadeJob?.cancel()
            track.currentVolume = targetVol
            applyVolumeToClip(track.clip, targetVol)
        } else {
            track.fadeJob?.cancel()
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (smoothingMs / 25L).coerceAtLeast(1)
                val delta = (targetVol - startVol) / steps
                for (i in 0 until steps) {
                    delay(25)
                    if (track.clip.isOpen) {
                        track.currentVolume = (track.currentVolume + delta).coerceIn(0f, 1.5f)
                        applyVolumeToClip(track.clip, track.currentVolume)
                    }
                }
                track.currentVolume = targetVol
                if (track.clip.isOpen) {
                    applyVolumeToClip(track.clip, targetVol)
                }
            }
        }
    }

    override fun setMasterVolume(volume: Float) {
        currentMasterVolume = volume
        for (track in activeTracks.values) {
            if (!track.isFadingOut && track.clip.isOpen) {
                track.currentVolume = track.cue.volume * currentMasterVolume
                applyVolumeToClip(track.clip, track.currentVolume)
            }
        }
    }

    private fun stopTrackInternal(playbackId: String, immediate: Boolean) {
        val track = activeTracks.remove(playbackId) ?: return
        track.fadeJob?.cancel()
        try {
            if (track.clip.isRunning) track.clip.stop()
            track.clip.close()
        } catch (ignored: Exception) {}
        updateActiveState()
    }

    private fun applyVolumeToClip(clip: Clip, volume: Float) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val clamped = volume.coerceIn(0.0001f, 1.5f)
                val dB = (20f * log10(clamped)).coerceIn(gainControl.minimum, gainControl.maximum)
                gainControl.value = dB
            }
        } catch (ignored: Exception) {}
    }

    private fun updateActiveState() {
        val list = activeTracks.values.mapNotNull { track ->
            if (!track.clip.isOpen) return@mapNotNull null
            val currentUs = track.clip.microsecondPosition
            val totalUs = max(1L, track.endMicroseconds - track.startMicroseconds)
            val elapsedUs = (currentUs - track.startMicroseconds).coerceAtLeast(0L)

            ActivePlayback(
                playbackId = track.playbackId,
                cueId = track.cue.id,
                cueName = track.cue.name,
                colorHex = track.cue.colorHex,
                elapsedMs = elapsedUs / 1000L,
                totalMs = totalUs / 1000L,
                volume = track.currentVolume,
                isLooping = track.cue.triggerMode == TriggerMode.LOOP,
                isFadingOut = track.isFadingOut
            )
        }
        _activePlaybacks.value = list
    }

    override fun release() {
        engineScope.cancel()
        for (track in activeTracks.values) {
            try {
                if (track.clip.isRunning) track.clip.stop()
                track.clip.close()
            } catch (ignored: Exception) {}
        }
        activeTracks.clear()
        _activePlaybacks.value = emptyList()
    }
}
