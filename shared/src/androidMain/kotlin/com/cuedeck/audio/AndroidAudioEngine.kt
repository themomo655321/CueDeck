package com.cuedeck.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
import kotlin.math.max

/**
 * Lightweight Android audio playback engine designed for high reliability
 * and low CPU/RAM overhead on both older hardware (e.g. Unisoc T310, Android 11)
 * and flagship devices.
 */
class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activePlaybacks = MutableStateFlow<List<ActivePlayback>>(emptyList())
    override val activePlaybacks: StateFlow<List<ActivePlayback>> = _activePlaybacks.asStateFlow()

    private val _playbackCompletedEvents = MutableSharedFlow<Cue>()
    override val playbackCompletedEvents: SharedFlow<Cue> = _playbackCompletedEvents.asSharedFlow()

    private var currentMasterVolume = 1.0f

    private class AndroidTrack(
        val playbackId: String,
        val cue: Cue,
        val player: MediaPlayer,
        val startMs: Long,
        val endMs: Long,
        var currentVolume: Float,
        var isFadingOut: Boolean = false,
        var fadeJob: Job? = null
    )

    private val activeTracks = ConcurrentHashMap<String, AndroidTrack>()
    private var preloadedTrack: Pair<String, MediaPlayer>? = null

    fun preload(cue: Cue, basePath: String) {
        engineScope.launch(Dispatchers.IO) {
            try {
                if (preloadedTrack?.first == cue.id) return@launch
                preloadedTrack?.second?.release()
                preloadedTrack = null

                val file = File(basePath, cue.audioRelativePath)
                if (!file.exists()) return@launch

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    if (cue.inPointMs > 0) {
                        seekTo(cue.inPointMs.toInt())
                    }
                }
                preloadedTrack = cue.id to player
            } catch (ignored: Exception) {}
        }
    }

    init {
        // UI poll timer for real-time progress bars (20Hz)
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
            return@withContext null
        }

        try {
            val player = if (preloadedTrack?.first == cue.id) {
                val p = preloadedTrack!!.second
                preloadedTrack = null
                p
            } else {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                }
            }

            val playbackId = UUID.randomUUID().toString()
            val totalDuration = player.duration.toLong()
            val startMs = cue.inPointMs.coerceIn(0L, totalDuration)
            val endMs = if (cue.outPointMs in (startMs + 1)..totalDuration) cue.outPointMs else totalDuration

            if (startMs > 0) {
                player.seekTo(startMs.toInt())
            }

            val initialVol = if (cue.fadeInMs > 0) 0.001f else (cue.volume * currentMasterVolume)
            player.setVolume(initialVol, initialVol)

            if (cue.triggerMode == TriggerMode.LOOP) {
                player.isLooping = true
            }

            val track = AndroidTrack(
                playbackId = playbackId,
                cue = cue,
                player = player,
                startMs = startMs,
                endMs = endMs,
                currentVolume = initialVol
            )

            activeTracks[playbackId] = track
            player.start()

            // Monitor end point & fade
            engineScope.launch {
                if (cue.fadeInMs > 0) {
                    val targetVol = cue.volume * currentMasterVolume
                    val steps = (cue.fadeInMs / 50L).coerceAtLeast(1)
                    val delta = (targetVol - 0.001f) / steps
                    for (i in 0 until steps) {
                        delay(50)
                        if (!track.isFadingOut && track.player.isPlaying) {
                            track.currentVolume = (track.currentVolume + delta).coerceIn(0f, targetVol)
                            track.player.setVolume(track.currentVolume, track.currentVolume)
                        }
                    }
                }

                // Check out-point
                while (track.player.isPlaying) {
                    delay(30)
                    val currentPos = track.player.currentPosition.toLong()
                    if (cue.triggerMode != TriggerMode.LOOP && currentPos >= endMs) {
                        break
                    }
                }

                if (!track.isFadingOut) {
                    stopTrackInternal(playbackId)
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
            stopTrackInternal(playbackId)
        } else {
            track.isFadingOut = true
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (fadeOutMs / 50L).coerceAtLeast(1)
                val delta = startVol / steps
                for (i in 0 until steps) {
                    delay(50)
                    try {
                        track.currentVolume = (track.currentVolume - delta).coerceAtLeast(0f)
                        track.player.setVolume(track.currentVolume, track.currentVolume)
                    } catch (ignored: Exception) {}
                }
                stopTrackInternal(playbackId)
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
            try {
                track.player.setVolume(targetVol, targetVol)
            } catch (ignored: Exception) {}
        } else {
            track.fadeJob?.cancel()
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (smoothingMs / 25L).coerceAtLeast(1)
                val delta = (targetVol - startVol) / steps
                for (i in 0 until steps) {
                    delay(25)
                    try {
                        track.currentVolume = (track.currentVolume + delta).coerceIn(0f, 1.5f)
                        track.player.setVolume(track.currentVolume, track.currentVolume)
                    } catch (ignored: Exception) {}
                }
                track.currentVolume = targetVol
                try {
                    track.player.setVolume(targetVol, targetVol)
                } catch (ignored: Exception) {}
            }
        }
    }

    override fun setMasterVolume(volume: Float) {
        currentMasterVolume = volume
        for (track in activeTracks.values) {
            if (!track.isFadingOut) {
                track.currentVolume = track.cue.volume * currentMasterVolume
                try {
                    track.player.setVolume(track.currentVolume, track.currentVolume)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun stopTrackInternal(playbackId: String) {
        val track = activeTracks.remove(playbackId) ?: return
        track.fadeJob?.cancel()
        try {
            if (track.player.isPlaying) track.player.stop()
            track.player.reset()
            track.player.release()
        } catch (ignored: Exception) {}
        updateActiveState()
    }

    private fun updateActiveState() {
        val list = activeTracks.values.mapNotNull { track ->
            try {
                val currentMs = track.player.currentPosition.toLong()
                val totalMs = max(1L, track.endMs - track.startMs)
                val elapsedMs = (currentMs - track.startMs).coerceAtLeast(0L)

                ActivePlayback(
                    playbackId = track.playbackId,
                    cueId = track.cue.id,
                    cueName = track.cue.name,
                    colorHex = track.cue.colorHex,
                    elapsedMs = elapsedMs,
                    totalMs = totalMs,
                    volume = track.currentVolume,
                    isLooping = track.cue.triggerMode == TriggerMode.LOOP,
                    isFadingOut = track.isFadingOut
                )
            } catch (e: Exception) {
                null
            }
        }
        _activePlaybacks.value = list
    }

    override fun release() {
        engineScope.cancel()
        for (track in activeTracks.values) {
            try {
                track.player.stop()
                track.player.release()
            } catch (ignored: Exception) {}
        }
        activeTracks.clear()
        _activePlaybacks.value = emptyList()
    }
}
