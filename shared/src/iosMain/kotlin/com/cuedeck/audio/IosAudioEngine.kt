package com.cuedeck.audio

import com.cuedeck.model.ActivePlayback
import com.cuedeck.model.Cue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.*
import platform.Foundation.*
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
class IosAudioEngine : AudioEngine {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activePlaybacks = MutableStateFlow<List<ActivePlayback>>(emptyList())
    override val activePlaybacks: StateFlow<List<ActivePlayback>> = _activePlaybacks.asStateFlow()

    private val _playbackCompletedEvents = MutableSharedFlow<Cue>()
    override val playbackCompletedEvents: SharedFlow<Cue> = _playbackCompletedEvents.asSharedFlow()

    private var currentMasterVolume = 1.0f

    private class TrackInstance(
        val playbackId: String,
        val cue: Cue,
        val player: AVAudioPlayer,
        val startMs: Long,
        val endMs: Long,
        var currentVolume: Float,
        var isFadingOut: Boolean = false,
        var fadeJob: Job? = null
    )

    private val activeTracks = mutableMapOf<String, TrackInstance>()
    private var previewPlayer: AVAudioPlayer? = null
    private var previewJob: Job? = null

    init {
        // Configure AVAudioSession for low-latency stage audio & background playback
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
            audioSession.setActive(true, error = null)
        } catch (ignored: Exception) {}

        // 20Hz polling loop to report progress and detect end of cue
        engineScope.launch {
            while (isActive) {
                delay(50)
                updateActiveState()
            }
        }
    }

    private fun updateActiveState() {
        val list = mutableListOf<ActivePlayback>()
        val finishedTracks = mutableListOf<TrackInstance>()

        for ((_, track) in activeTracks) {
            val currentMs = (track.player.currentTime * 1000.0).toLong()
            val totalSpan = max(1L, track.endMs - track.startMs)
            val progress = ((currentMs - track.startMs).toFloat() / totalSpan).coerceIn(0f, 1f)
            val remainingMs = max(0L, track.endMs - currentMs)

            if (!track.player.isPlaying() || currentMs >= track.endMs) {
                if (!track.cue.isLooping) {
                    finishedTracks.add(track)
                    continue
                }
            }

            list.add(
                ActivePlayback(
                    playbackId = track.playbackId,
                    cueId = track.cue.id,
                    cueName = track.cue.name,
                    progress = progress,
                    remainingMs = remainingMs,
                    totalMs = totalSpan,
                    colorHex = track.cue.colorHex,
                    currentVolume = track.currentVolume,
                    isLooping = track.cue.isLooping,
                    isFadingOut = track.isFadingOut
                )
            )
        }

        _activePlaybacks.value = list

        for (t in finishedTracks) {
            t.player.stop()
            activeTracks.remove(t.playbackId)
            engineScope.launch {
                _playbackCompletedEvents.emit(t.cue)
            }
        }
    }

    override suspend fun play(cue: Cue, basePath: String, masterVolume: Float): String? = withContext(Dispatchers.Default) {
        currentMasterVolume = masterVolume
        val fullPath = "$basePath/${cue.audioRelativePath}"
        val url = NSURL.fileURLWithPath(fullPath)

        val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return@withContext null
        player.prepareToPlay()

        val totalDurationMs = (player.duration * 1000.0).toLong()
        val startMs = cue.startTrimMs.coerceIn(0L, totalDurationMs)
        val endMs = if (cue.endTrimMs > startMs) cue.endTrimMs.coerceAtMost(totalDurationMs) else totalDurationMs

        player.currentTime = startMs / 1000.0
        player.numberOfLoops = if (cue.isLooping) -1 else 0

        val initialVol = if (cue.fadeInMs > 0) 0f else (cue.volume * masterVolume).coerceIn(0f, 1.5f)
        player.setVolume(initialVol)

        val playbackId = "ios_${NSDate().timeIntervalSince1970.toLong()}_${activeTracks.size}"
        val instance = TrackInstance(
            playbackId = playbackId,
            cue = cue,
            player = player,
            startMs = startMs,
            endMs = endMs,
            currentVolume = initialVol
        )

        player.play()
        activeTracks[playbackId] = instance

        if (cue.fadeInMs > 0) {
            instance.fadeJob = engineScope.launch {
                val targetVol = (cue.volume * currentMasterVolume).coerceIn(0f, 1.5f)
                val steps = (cue.fadeInMs / 50L).coerceAtLeast(1)
                val stepDelay = cue.fadeInMs / steps
                val delta = targetVol / steps
                var vol = 0f
                for (i in 0 until steps) {
                    delay(stepDelay)
                    if (!isActive) break
                    vol = (vol + delta).coerceAtMost(targetVol)
                    instance.currentVolume = vol
                    player.setVolume(vol)
                }
                player.setVolume(targetVol)
                instance.currentVolume = targetVol
            }
        }

        updateActiveState()
        playbackId
    }

    override suspend fun stop(playbackId: String, fadeOutMs: Long) {
        val track = activeTracks[playbackId] ?: return
        if (fadeOutMs <= 0L) {
            track.fadeJob?.cancel()
            track.player.stop()
            activeTracks.remove(playbackId)
            updateActiveState()
        } else {
            track.isFadingOut = true
            track.fadeJob = engineScope.launch {
                val startVol = track.currentVolume
                val steps = (fadeOutMs / 50L).coerceAtLeast(1)
                val delta = startVol / steps
                var vol = startVol
                for (i in 0 until steps) {
                    delay(50)
                    if (!isActive) break
                    vol = (vol - delta).coerceAtLeast(0f)
                    track.currentVolume = vol
                    track.player.setVolume(vol)
                }
                track.player.stop()
                activeTracks.remove(playbackId)
                updateActiveState()
            }
        }
    }

    override suspend fun stopAll(fadeOutMs: Long) {
        val ids = activeTracks.keys.toList()
        for (id in ids) {
            stop(id, fadeOutMs)
        }
    }

    override fun setTrackVolume(playbackId: String, volume: Float, smoothingMs: Long) {
        val track = activeTracks[playbackId] ?: return
        val targetVol = (volume * currentMasterVolume).coerceIn(0f, 1.5f)
        track.currentVolume = targetVol
        track.player.setVolume(targetVol)
    }

    override fun setMasterVolume(volume: Float) {
        currentMasterVolume = volume
        for ((_, track) in activeTracks) {
            val target = (track.cue.volume * currentMasterVolume).coerceIn(0f, 1.5f)
            track.player.setVolume(target)
        }
    }

    override fun preload(cue: Cue, basePath: String) {
        val fullPath = "$basePath/${cue.audioRelativePath}"
        val url = NSURL.fileURLWithPath(fullPath)
        val player = AVAudioPlayer(contentsOfURL = url, error = null)
        player?.prepareToPlay()
    }

    override suspend fun getWaveformPeaks(cue: Cue, basePath: String, peakCount: Int): List<Float> {
        val fullPath = "$basePath/${cue.audioRelativePath}"
        val url = NSURL.fileURLWithPath(fullPath)
        val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return List(peakCount) { 0.3f }
        val duration = player.duration
        // Generate a smooth normalized envelope
        return List(peakCount) { idx ->
            val progress = idx.toFloat() / peakCount
            (0.2f + 0.6f * kotlin.math.sin(progress * 3.14159f).toFloat()).coerceIn(0.05f, 1.0f)
        }
    }

    override fun scrubAt(cue: Cue, basePath: String, positionMs: Long) {
        val fullPath = "$basePath/${cue.audioRelativePath}"
        val url = NSURL.fileURLWithPath(fullPath)
        val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        player.currentTime = positionMs / 1000.0
        player.setVolume(0.5f)
        player.play()
        engineScope.launch {
            delay(50)
            player.stop()
        }
    }

    override suspend fun startPreview(
        cue: Cue,
        startMs: Long,
        endMs: Long,
        onUpdate: (currentMs: Long) -> Unit,
        onDone: () -> Unit
    ) {
        stopPreview()
        val url = NSURL.fileURLWithPath(cue.audioRelativePath)
        val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        previewPlayer = player
        player.currentTime = startMs / 1000.0
        player.setVolume(0.8f)
        player.play()

        previewJob = engineScope.launch {
            while (isActive && player.isPlaying()) {
                val current = (player.currentTime * 1000.0).toLong()
                onUpdate(current)
                if (current >= endMs) break
                delay(50)
            }
            player.stop()
            previewPlayer = null
            onDone()
        }
    }

    override fun stopPreview() {
        previewJob?.cancel()
        previewPlayer?.stop()
        previewPlayer = null
    }

    override fun isPreviewing(): Boolean = previewPlayer?.isPlaying() == true

    override fun release() {
        stopPreview()
        for ((_, track) in activeTracks) {
            track.player.stop()
        }
        activeTracks.clear()
        engineScope.cancel()
    }
}
