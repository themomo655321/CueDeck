package com.cuedeck.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import com.cuedeck.diagnostics.ErrorLogger
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Lightweight, rock-solid Android audio playback engine designed for zero crashes,
 * ultra-low latency, and live performance safety.
 */
class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private val engineScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            ErrorLogger.error("AndroidAudioEngine", "Uncaught audio engine exception", throwable)
        }
    )

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
        var fadeJob: Job? = null,
        var monitorJob: Job? = null,
        val isReleased: AtomicBoolean = AtomicBoolean(false)
    )

    private val activeTracks = ConcurrentHashMap<String, AndroidTrack>()
    private var preloadedTrack: Pair<String, MediaPlayer>? = null

    // Preview & Scrubbing
    private var previewPlayer: MediaPlayer? = null
    private var previewJob: Job? = null
    private var scrubPlayer: MediaPlayer? = null
    private var scrubJob: Job? = null
    private var scrubPath: String? = null
    private var lastScrubTimestamp: Long = 0L

    override fun preload(cue: Cue, basePath: String) {
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
            safeSetVolume(player, initialVol)

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
            val monitorJob = engineScope.launch {
                try {
                    if (cue.fadeInMs > 0) {
                        val targetVol = cue.volume * currentMasterVolume
                        val steps = (cue.fadeInMs / 50L).coerceAtLeast(1)
                        val delta = (targetVol - 0.001f) / steps
                        for (i in 0 until steps) {
                            delay(50)
                            if (track.isReleased.get() || !isActive) break
                            if (!track.isFadingOut && safeIsPlaying(track.player)) {
                                track.currentVolume = (track.currentVolume + delta).coerceIn(0f, targetVol)
                                safeSetVolume(track.player, track.currentVolume)
                            }
                        }
                    }

                    // Check out-point safely
                    while (isActive && !track.isReleased.get() && safeIsPlaying(track.player)) {
                        delay(30)
                        if (track.isReleased.get() || !isActive) break
                        val currentPos = safeGetCurrentPosition(track.player)
                        if (cue.triggerMode != TriggerMode.LOOP && currentPos >= endMs) {
                            break
                        }
                    }

                    if (!track.isFadingOut && !track.isReleased.get()) {
                        stopTrackInternal(playbackId)
                        _playbackCompletedEvents.emit(cue)
                    }
                } catch (e: Exception) {
                    ErrorLogger.error("AndroidAudioEngine", "Monitor loop exception for ${cue.name}", e)
                }
            }
            track.monitorJob = monitorJob

            playbackId
        } catch (e: Exception) {
            ErrorLogger.error("AndroidAudioEngine", "Error playing cue ${cue.name}", e)
            null
        }
    }

    override suspend fun stop(playbackId: String, fadeOutMs: Long) {
        val track = activeTracks[playbackId] ?: return
        if (track.isReleased.get()) return

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
                    if (track.isReleased.get() || !isActive) break
                    track.currentVolume = (track.currentVolume - delta).coerceAtLeast(0f)
                    safeSetVolume(track.player, track.currentVolume)
                }
                stopTrackInternal(playbackId)
            }
        }
    }

    override suspend fun stopAll(fadeOutMs: Long) {
        try {
            val tracks = activeTracks.values.toList()
            for (track in tracks) {
                stop(track.playbackId, fadeOutMs)
            }
        } catch (e: Exception) {
            ErrorLogger.error("AndroidAudioEngine", "Error stopping all tracks in panic", e)
        }
    }

    override fun setTrackVolume(playbackId: String, volume: Float, smoothingMs: Long) {
        val track = activeTracks[playbackId] ?: return
        if (track.isFadingOut || track.isReleased.get()) return

        val targetVol = (volume * currentMasterVolume).coerceIn(0f, 1.5f)

        if (smoothingMs <= 0L) {
            track.fadeJob?.cancel()
            track.currentVolume = targetVol
            safeSetVolume(track.player, targetVol)
        } else {
            track.fadeJob?.cancel()
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (smoothingMs / 25L).coerceAtLeast(1)
                val delta = (targetVol - startVol) / steps
                for (i in 0 until steps) {
                    delay(25)
                    if (track.isReleased.get() || !isActive) break
                    track.currentVolume = (track.currentVolume + delta).coerceIn(0f, 1.5f)
                    safeSetVolume(track.player, track.currentVolume)
                }
                track.currentVolume = targetVol
                safeSetVolume(track.player, targetVol)
            }
        }
    }

    override fun setMasterVolume(volume: Float) {
        currentMasterVolume = volume
        for (track in activeTracks.values) {
            if (!track.isFadingOut && !track.isReleased.get()) {
                track.currentVolume = track.cue.volume * currentMasterVolume
                safeSetVolume(track.player, track.currentVolume)
            }
        }
    }

    override suspend fun getWaveformPeaks(cue: Cue, basePath: String, peakCount: Int): List<Float> {
        val file = File(basePath, cue.audioRelativePath)
        return AndroidWaveformExtractor.getWaveformPeaks(file, peakCount)
    }

    override fun scrubAt(cue: Cue, basePath: String, positionMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastScrubTimestamp < 50L) return
        lastScrubTimestamp = now

        val file = File(basePath, cue.audioRelativePath)
        if (!file.exists()) return

        scrubJob?.cancel()
        scrubJob = engineScope.launch(Dispatchers.IO) {
            try {
                if (scrubPlayer == null || scrubPath != file.absolutePath) {
                    try { scrubPlayer?.release() } catch (ignored: Exception) {}
                    scrubPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                    scrubPath = file.absolutePath
                }

                val sp = scrubPlayer ?: return@launch
                val safePos = positionMs.coerceIn(0L, sp.duration.toLong())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sp.seekTo(safePos, MediaPlayer.SEEK_CLOSEST)
                } else {
                    sp.seekTo(safePos.toInt())
                }
                val scrubVol = (0.7f * currentMasterVolume).coerceIn(0.1f, 1.0f)
                safeSetVolume(sp, scrubVol)
                sp.start()
                delay(60)
                if (safeIsPlaying(sp)) {
                    sp.pause()
                }
            } catch (ignored: Exception) {}
        }
    }

    override suspend fun startPreview(
        cue: Cue,
        basePath: String,
        startMs: Long,
        endMs: Long,
        onPositionUpdate: (Long) -> Unit,
        onCompleted: () -> Unit
    ) {
        stopPreview()
        val file = File(basePath, cue.audioRelativePath)
        if (!file.exists()) return

        withContext(Dispatchers.IO) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                }
                previewPlayer = player

                val startPos = startMs.coerceIn(0L, player.duration.toLong())
                val endPos = if (endMs in (startPos + 1)..player.duration.toLong()) endMs else player.duration.toLong()

                player.seekTo(startPos.toInt())
                val vol = cue.volume * currentMasterVolume
                safeSetVolume(player, vol)
                player.start()

                previewJob = engineScope.launch {
                    try {
                        while (isActive && safeIsPlaying(player)) {
                            delay(30)
                            val current = safeGetCurrentPosition(player)
                            onPositionUpdate(current)
                            if (current >= endPos) {
                                break
                            }
                        }
                    } catch (ignored: Exception) {}
                    stopPreview()
                    onCompleted()
                }
            } catch (e: Exception) {
                ErrorLogger.error("AndroidAudioEngine", "Failed to start preview", e)
                stopPreview()
                onCompleted()
            }
        }
    }

    override fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        try {
            previewPlayer?.let { p ->
                if (safeIsPlaying(p)) p.stop()
                p.reset()
                p.release()
            }
        } catch (ignored: Exception) {}
        previewPlayer = null
    }

    override fun isPreviewing(): Boolean {
        val p = previewPlayer ?: return false
        return safeIsPlaying(p)
    }

    private fun stopTrackInternal(playbackId: String) {
        val track = activeTracks.remove(playbackId) ?: return
        if (track.isReleased.compareAndSet(false, true)) {
            track.fadeJob?.cancel()
            track.monitorJob?.cancel()
            try {
                if (safeIsPlaying(track.player)) {
                    track.player.stop()
                }
                track.player.reset()
                track.player.release()
            } catch (ignored: Exception) {}
        }
        updateActiveState()
    }

    private fun safeIsPlaying(player: MediaPlayer): Boolean {
        return try {
            player.isPlaying
        } catch (e: Exception) {
            false
        }
    }

    private fun safeGetCurrentPosition(player: MediaPlayer): Long {
        return try {
            player.currentPosition.toLong()
        } catch (e: Exception) {
            0L
        }
    }

    private fun safeSetVolume(player: MediaPlayer, volume: Float) {
        try {
            val v = volume.coerceIn(0f, 1.5f)
            player.setVolume(v, v)
        } catch (ignored: Exception) {}
    }

    private fun updateActiveState() {
        val list = activeTracks.values.mapNotNull { track ->
            if (track.isReleased.get()) return@mapNotNull null
            try {
                val currentMs = safeGetCurrentPosition(track.player)
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
        stopPreview()
        try {
            scrubJob?.cancel()
            scrubPlayer?.release()
        } catch (ignored: Exception) {}
        scrubPlayer = null

        try {
            preloadedTrack?.second?.release()
        } catch (ignored: Exception) {}
        preloadedTrack = null

        for (track in activeTracks.values) {
            if (track.isReleased.compareAndSet(false, true)) {
                try {
                    track.fadeJob?.cancel()
                    track.monitorJob?.cancel()
                    if (safeIsPlaying(track.player)) track.player.stop()
                    track.player.release()
                } catch (ignored: Exception) {}
            }
        }
        activeTracks.clear()
        _activePlaybacks.value = emptyList()
    }
}
