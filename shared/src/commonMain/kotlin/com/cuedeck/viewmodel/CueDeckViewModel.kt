package com.cuedeck.viewmodel

import com.cuedeck.audio.AudioEngine
import com.cuedeck.diagnostics.ErrorLogger
import com.cuedeck.importer.PlatformWebAudioImporter
import com.cuedeck.model.*
import com.cuedeck.storage.SessionState
import com.cuedeck.storage.ShowRepository
import com.cuedeck.storage.ShowSummary
import com.cuedeck.storage.StorageInspection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class CueDeckViewModel(
    private val repository: ShowRepository,
    private val audioEngine: AudioEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private val _show = MutableStateFlow(createDefaultShow())
    val show: StateFlow<Show> = _show.asStateFlow()

    val activePlaybacks: StateFlow<List<ActivePlayback>> = audioEngine.activePlaybacks

    // Fixed 4-slot mixer channels: tracks stay pinned to their slot until playback finishes
    private val _mixerSlots = MutableStateFlow<List<ActivePlayback?>>(listOf(null, null, null, null))
    val mixerSlots: StateFlow<List<ActivePlayback?>> = _mixerSlots.asStateFlow()

    // Overflow queue for simultaneous audio beyond 4 channels
    private val _mixerQueue = MutableStateFlow<List<ActivePlayback>>(emptyList())
    val mixerQueue: StateFlow<List<ActivePlayback>> = _mixerQueue.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _faderVolumes = MutableStateFlow(listOf(1.0f, 1.0f, 1.0f, 1.0f))
    val faderVolumes: StateFlow<List<Float>> = _faderVolumes.asStateFlow()

    private val _saveFeedback = MutableStateFlow<String?>(null)
    val saveFeedback: StateFlow<String?> = _saveFeedback.asStateFlow()
    private var feedbackJob: Job? = null

    private val _storageInspection = MutableStateFlow<StorageInspection?>(null)
    val storageInspection: StateFlow<StorageInspection?> = _storageInspection.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    private val _importFileName = MutableStateFlow<String?>(null)
    val importFileName: StateFlow<String?> = _importFileName.asStateFlow()

    private val _isImportMinimized = MutableStateFlow(false)
    val isImportMinimized: StateFlow<Boolean> = _isImportMinimized.asStateFlow()
    private var importJob: Job? = null

    private val _savedShows = MutableStateFlow<List<ShowSummary>>(emptyList())
    val savedShows: StateFlow<List<ShowSummary>> = _savedShows.asStateFlow()

    private val _userErrorMessage = MutableStateFlow<String?>(null)
    val userErrorMessage: StateFlow<String?> = _userErrorMessage.asStateFlow()

    private val _workspaceLayoutState = MutableStateFlow(WorkspaceLayoutState())
    val workspaceLayoutState: StateFlow<WorkspaceLayoutState> = _workspaceLayoutState.asStateFlow()

    init {
        loadInitialShow()
        restoreSessionState()
        refreshSavedShows()
        loadWorkspaceLayouts()

        // Auto-follow event listener
        coroutineScope.launch {
            audioEngine.playbackCompletedEvents.collect { completedCue ->
                handlePlaybackCompleted(completedCue)
            }
        }

        // Auto-preload armed cue for zero latency
        coroutineScope.launch {
            _show.collect { currentShow ->
                val armed = currentShow.findCue(currentShow.armedCueId) ?: currentShow.cues.firstOrNull()
                if (armed != null) {
                    preloadArmedCue(armed, currentShow.id)
                }
            }
        }

        // Live mixer slot synchronization: pin audio to fixed channels 1-4 and manage overflow queue
        coroutineScope.launch {
            audioEngine.activePlaybacks.collect { activeList ->
                syncMixerSlots(activeList)
            }
        }
    }

    private fun syncMixerSlots(activeList: List<ActivePlayback>) {
        val currentSlots = _mixerSlots.value.toMutableList()
        val currentQueue = _mixerQueue.value.toMutableList()
        val activeMap = activeList.associateBy { it.playbackId }

        // 1. Refresh active items in current slots, or clear vacated slots to null
        for (i in 0..3) {
            val track = currentSlots.getOrNull(i)
            if (track != null) {
                val updated = activeMap[track.playbackId]
                if (updated != null) {
                    currentSlots[i] = updated
                } else {
                    currentSlots[i] = null // Audio ended; slot is now free
                }
            }
        }

        // 2. Filter out finished items from the queue, update remaining
        currentQueue.removeAll { it.playbackId !in activeMap }
        for (i in currentQueue.indices) {
            activeMap[currentQueue[i].playbackId]?.let { currentQueue[i] = it }
        }

        // 3. Find newly started tracks that aren't yet slotted or queued
        val slottedIds = currentSlots.mapNotNull { it?.playbackId }.toSet()
        val queuedIds = currentQueue.map { it.playbackId }.toSet()
        val newTracks = activeList.filter { it.playbackId !in slottedIds && it.playbackId !in queuedIds }

        for (newTrack in newTracks) {
            val emptySlotIndex = currentSlots.indexOfFirst { it == null }
            if (emptySlotIndex != -1) {
                currentSlots[emptySlotIndex] = newTrack
                val faderVol = _faderVolumes.value.getOrElse(emptySlotIndex) { 1.0f }
                audioEngine.setTrackVolume(newTrack.playbackId, faderVol, 0L)
            } else {
                currentQueue.add(newTrack)
            }
        }

        // 4. If slots became free and queue has waiting tracks, assign to freed slots
        while (currentQueue.isNotEmpty() && currentSlots.any { it == null }) {
            val nextTrack = currentQueue.removeAt(0)
            val freeIndex = currentSlots.indexOfFirst { it == null }
            if (freeIndex != -1) {
                currentSlots[freeIndex] = nextTrack
                val faderVol = _faderVolumes.value.getOrElse(freeIndex) { 1.0f }
                audioEngine.setTrackVolume(nextTrack.playbackId, faderVol, 0L)
            }
        }

        _mixerSlots.value = currentSlots
        _mixerQueue.value = currentQueue
    }

    private fun preloadArmedCue(cue: Cue, showId: String) {
        val basePath = repository.getBasePath(showId)
        audioEngine.preload(cue, basePath)
    }

    fun armCue(cueId: String) {
        setArmedCue(cueId)
    }

    private fun sanitizeCues(cues: List<Cue>): List<Cue> {
        return cues.map { cue ->
            if (cue.cueNumber.endsWith(".0")) {
                cue.copy(cueNumber = cue.cueNumber.removeSuffix(".0"))
            } else {
                cue
            }
        }
    }

    private fun loadInitialShow() {
        val existing = repository.loadLastActiveShow()
        if (existing != null) {
            _show.value = existing.copy(cues = sanitizeCues(existing.cues))
            audioEngine.setMasterVolume(existing.masterVolume)
        } else {
            saveCurrentShow()
        }
    }

    private fun createDefaultShow(): Show {
        val showId = "default_show"
        return Show(
            id = showId,
            title = "New Show",
            cues = emptyList(),
            masterVolume = 1.0f,
            armedCueId = null,
            isPerformanceLocked = false
        )
    }

    /**
     * Big GO button pressed (QLab sequential trigger)
     */
    fun pressGo() {
        val currentShow = _show.value
        if (currentShow.cues.isEmpty()) return

        val armedCue = currentShow.findCue(currentShow.armedCueId) ?: currentShow.cues.first()

        coroutineScope.launch {
            try {
                if (armedCue.preWaitMs > 0) {
                    delay(armedCue.preWaitMs)
                }

                // If this cue has crossfadeOnGo enabled, smoothly fade out any currently playing tracks
                if (armedCue.crossfadeOnGo) {
                    val active = activePlaybacks.value
                    val xFadeMs = armedCue.crossfadeDurationMs
                    for (track in active) {
                        audioEngine.stop(track.playbackId, xFadeMs)
                    }
                }

                val playId = audioEngine.play(
                    cue = armedCue,
                    basePath = repository.getBasePath(currentShow.id),
                    masterVolume = currentShow.masterVolume
                )

                if (playId == null) {
                    showUserError("Audio file not found: ${armedCue.audioRelativePath}")
                }

                // Determine next armed cue
                val nextCue = currentShow.nextCueAfter(armedCue.id)
                _show.update { it.copy(armedCueId = nextCue?.id) }

                // Auto-continue (fire simultaneously)
                if (armedCue.followAction == FollowAction.AUTO_CONTINUE && nextCue != null) {
                    if (armedCue.postWaitMs > 0) delay(armedCue.postWaitMs)
                    pressGo()
                }
            } catch (e: Exception) {
                ErrorLogger.error("CueDeckViewModel", "Error pressing GO", e)
                showUserError("Playback failed: ${e.message}")
            }
        }
    }

    private fun handlePlaybackCompleted(completedCue: Cue) {
        val currentShow = _show.value
        if (completedCue.followAction == FollowAction.AUTO_FOLLOW) {
            val nextCue = currentShow.nextCueAfter(completedCue.id)
            if (nextCue != null) {
                coroutineScope.launch {
                    if (completedCue.postWaitMs > 0) delay(completedCue.postWaitMs)
                    _show.update { it.copy(armedCueId = nextCue.id) }
                    pressGo()
                }
            }
        }
    }

    fun setFaderVolume(channelIndex: Int, volume: Float) {
        val list = _faderVolumes.value.toMutableList()
        if (channelIndex in list.indices) {
            val clamped = volume.coerceIn(0f, 1.25f)
            list[channelIndex] = clamped
            _faderVolumes.value = list

            val activeTrack = _mixerSlots.value.getOrNull(channelIndex)
            if (activeTrack != null) {
                // 0ms smoothing on live manual fader control eliminates touch cancellation audio lag
                audioEngine.setTrackVolume(activeTrack.playbackId, clamped, 0L)
            }
        }
    }

    suspend fun getWaveformPeaks(cue: Cue, peakCount: Int = 100): List<Float> {
        val basePath = repository.getBasePath(_show.value.id)
        return audioEngine.getWaveformPeaks(cue, basePath, peakCount)
    }

    fun scrubPreview(cue: Cue, positionMs: Long) {
        val basePath = repository.getBasePath(_show.value.id)
        audioEngine.scrubAt(cue, basePath, positionMs)
    }

    fun startPreview(
        cue: Cue,
        startMs: Long,
        endMs: Long,
        onPositionUpdate: (Long) -> Unit,
        onCompleted: () -> Unit
    ) {
        coroutineScope.launch {
            val basePath = repository.getBasePath(_show.value.id)
            audioEngine.startPreview(cue, basePath, startMs, endMs, onPositionUpdate, onCompleted)
        }
    }

    fun stopPreview() {
        audioEngine.stopPreview()
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
    }

    fun stopActivePlayback(playbackId: String, fadeOutMs: Long = 500L) {
        coroutineScope.launch {
            audioEngine.stop(playbackId, fadeOutMs)
        }
    }

    fun panicStop(fadeOutMs: Long? = null) {
        val effectiveFade = fadeOutMs ?: _settings.value.panicFadeMs
        coroutineScope.launch {
            audioEngine.stopAll(effectiveFade)
        }
    }

    fun setArmedCue(cueId: String) {
        _show.update { it.copy(armedCueId = cueId) }
    }

    fun togglePerformanceLock() {
        _show.update { it.copy(isPerformanceLocked = !it.isPerformanceLocked) }
    }

    fun setMasterVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1.5f)
        _show.update { it.copy(masterVolume = clamped) }
        audioEngine.setMasterVolume(clamped)
    }

    fun addCue(cue: Cue) {
        _show.update { show ->
            val cleanCue = if (cue.cueNumber.endsWith(".0")) cue.copy(cueNumber = cue.cueNumber.removeSuffix(".0")) else cue
            val updatedCues = show.cues + cleanCue
            show.copy(
                cues = updatedCues,
                armedCueId = show.armedCueId ?: cleanCue.id
            )
        }
        saveCurrentShow()
    }

    fun updateCue(updatedCue: Cue) {
        _show.update { show ->
            val cleanCue = if (updatedCue.cueNumber.endsWith(".0")) updatedCue.copy(cueNumber = updatedCue.cueNumber.removeSuffix(".0")) else updatedCue
            show.copy(cues = show.cues.map { if (it.id == cleanCue.id) cleanCue else it })
        }
        saveCurrentShow()
    }

    fun deleteCue(cueId: String) {
        _show.update { show ->
            val filtered = show.cues.filter { it.id != cueId }
            val newArmed = if (show.armedCueId == cueId) filtered.firstOrNull()?.id else show.armedCueId
            show.copy(cues = filtered, armedCueId = newArmed)
        }
        saveCurrentShow()
    }

    fun moveCueUp(cueId: String) {
        _show.update { show ->
            val index = show.cues.indexOfFirst { it.id == cueId }
            if (index <= 0) return@update show
            val list = show.cues.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            show.copy(cues = list)
        }
        saveCurrentShow()
    }

    fun moveCueDown(cueId: String) {
        _show.update { show ->
            val index = show.cues.indexOfFirst { it.id == cueId }
            if (index < 0 || index >= show.cues.size - 1) return@update show
            val list = show.cues.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            show.copy(cues = list)
        }
        saveCurrentShow()
    }

    fun moveCue(fromIndex: Int, toIndex: Int) {
        _show.update { show ->
            if (fromIndex !in show.cues.indices || toIndex !in show.cues.indices || fromIndex == toIndex) return@update show
            val list = show.cues.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            // Renumber cues sequentially 1, 2, 3...
            val renumbered = list.mapIndexed { idx, cue ->
                cue.copy(cueNumber = "${idx + 1}")
            }
            show.copy(cues = renumbered)
        }
        saveCurrentShow()
    }

    fun selectPreviousCue() {
        val cues = _show.value.cues
        if (cues.isEmpty()) return
        val currentArmedId = _show.value.armedCueId
        val currentIndex = cues.indexOfFirst { it.id == currentArmedId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else cues.size - 1
        armCue(cues[prevIndex].id)
    }

    fun selectNextCue() {
        val cues = _show.value.cues
        if (cues.isEmpty()) return
        val currentArmedId = _show.value.armedCueId
        val currentIndex = cues.indexOfFirst { it.id == currentArmedId }
        val nextIndex = if (currentIndex in 0 until cues.size - 1) currentIndex + 1 else 0
        armCue(cues[nextIndex].id)
    }

    fun duplicateCue(cueId: String) {
        _show.update { show ->
            val index = show.cues.indexOfFirst { it.id == cueId }
            if (index < 0) return@update show
            val source = show.cues[index]
            val newId = UUID.randomUUID().toString()
            val duplicated = source.copy(
                id = newId,
                name = "${source.name} (Copy)"
            )
            val list = show.cues.toMutableList()
            list.add(index + 1, duplicated)
            val renumbered = list.mapIndexed { idx, cue ->
                cue.copy(cueNumber = "${idx + 1}")
            }
            show.copy(cues = renumbered)
        }
        saveCurrentShow()
        showSaveFeedback("Cue duplicated ✓")
    }

    fun importFromUrl(url: String) {
        importJob?.cancel()
        _isImportMinimized.value = false
        importJob = coroutineScope.launch {
            try {
                _importFileName.value = "Connecting to source..."
                _importProgress.value = 0.05f

                val targetDir = repository.getBasePath(_show.value.id)
                val importer = PlatformWebAudioImporter()
                val results = importer.downloadAudio(url, targetDir) { progress, status ->
                    _importProgress.value = progress
                    _importFileName.value = status
                }

                if (isActive && results.isNotEmpty()) {
                    for (result in results) {
                        val nextNum = _show.value.cues.size + 1
                        val newCue = Cue(
                            id = UUID.randomUUID().toString(),
                            cueNumber = "$nextNum",
                            name = result.title,
                            audioRelativePath = result.relativePath,
                            originalFileName = result.fileName,
                            durationMs = result.durationMs,
                            volume = 1.0f,
                            fadeInMs = 0L,
                            fadeOutMs = 1000L
                        )
                        addCue(newCue)
                    }
                    showSaveFeedback(if (results.size == 1) "Imported: ${results[0].title} ✓" else "Imported ${results.size} cues ✓")
                } else if (isActive) {
                    showUserError("Failed to import audio from link. Please check the URL.")
                }
            } catch (e: CancellationException) {
                // User cancelled import
                showSaveFeedback("Download cancelled")
            } catch (e: Exception) {
                ErrorLogger.error("CueDeckViewModel", "Error importing web audio", e)
                showUserError("Import error: ${e.message}")
            } finally {
                _importProgress.value = null
                _importFileName.value = null
                _isImportMinimized.value = false
                importJob = null
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importProgress.value = null
        _importFileName.value = null
        _isImportMinimized.value = false
        showSaveFeedback("Download cancelled")
    }

    fun minimizeImport() {
        _isImportMinimized.value = true
    }

    fun expandImport() {
        _isImportMinimized.value = false
    }

    fun showSaveFeedback(message: String) {
        feedbackJob?.cancel()
        feedbackJob = coroutineScope.launch {
            _saveFeedback.value = message
            delay(3000L)
            _saveFeedback.value = null
        }
    }

    fun refreshSavedShows() {
        _savedShows.value = repository.listShowSummaries()
    }

    fun createNewShow(title: String = "New Show") {
        val newShow = createDefaultShow().copy(
            id = UUID.randomUUID().toString(),
            title = title,
            cues = emptyList()
        )
        repository.saveShow(newShow)
        _show.value = newShow
        refreshSavedShows()
        showSaveFeedback("Created: $title ✓")
    }

    fun cycleFineMultiplier() {
        val current = _settings.value.fineMultiplier
        val next = when (current) {
            0 -> 4
            4 -> 8
            8 -> 16
            else -> 0
        }
        updateSettings(_settings.value.copy(
            fineMultiplier = next,
            fineModeEnabled = next > 0,
            fineTuneRatio = if (next > 0) next.toFloat() else 4.0f
        ))
    }

    fun loadShowById(showId: String) {
        val loaded = repository.loadShow(showId)
        if (loaded != null) {
            coroutineScope.launch {
                audioEngine.stopAll(fadeOutMs = 0L)
                _show.value = loaded.copy(cues = sanitizeCues(loaded.cues))
                refreshSavedShows()
                showSaveFeedback("Loaded: ${loaded.title} ✓")
            }
        } else {
            showUserError("Failed to load show")
        }
    }

    fun saveShowAs(newTitle: String) {
        val newId = UUID.randomUUID().toString()
        val copied = _show.value.copy(
            id = newId,
            title = newTitle
        )
        repository.saveShow(copied)
        _show.value = copied
        refreshSavedShows()
        showSaveFeedback("Saved As: $newTitle ✓")
    }

    fun renameCurrentShow(newTitle: String) {
        _show.update { it.copy(title = newTitle) }
        saveCurrentShow()
        refreshSavedShows()
        showSaveFeedback("Renamed to: $newTitle ✓")
    }

    fun deleteShowById(showId: String) {
        if (_show.value.id == showId) {
            showUserError("Cannot delete currently open show")
            return
        }
        repository.deleteShow(showId)
        refreshSavedShows()
        showSaveFeedback("Show deleted")
    }

    fun saveCurrentShow(userInitiated: Boolean = false) {
        repository.saveShow(_show.value)
        saveSessionState()
        refreshSavedShows()
        if (userInitiated) {
            showSaveFeedback("Show Saved ✓")
        }
    }

    fun saveSessionState() {
        val state = SessionState(
            showId = _show.value.id,
            armedCueId = _show.value.armedCueId,
            faderVolumes = _faderVolumes.value
        )
        repository.saveSession(state)
    }

    fun restoreSessionState() {
        val state = repository.loadSession() ?: return
        if (state.showId == _show.value.id) {
            if (state.armedCueId != null) {
                _show.update { it.copy(armedCueId = state.armedCueId) }
            }
            if (state.faderVolumes.size == 4) {
                _faderVolumes.value = state.faderVolumes
            }
        }
    }

    fun inspectCurrentStorage() {
        coroutineScope.launch(Dispatchers.IO) {
            val res = repository.inspectStorage(_show.value)
            _storageInspection.value = res
        }
    }

    fun purgeUnusedAudio() {
        coroutineScope.launch(Dispatchers.IO) {
            repository.purgeUnusedAudio(_show.value)
            inspectCurrentStorage()
        }
    }

    fun openStorageFolder() {
        repository.openFolderInSystem(_show.value.id)
    }

    fun setImportProgress(fileName: String?, progress: Float?) {
        _importFileName.value = fileName
        _importProgress.value = progress
    }

    fun showUserError(message: String) {
        coroutineScope.launch {
            _userErrorMessage.value = message
            delay(4000)
            _userErrorMessage.value = null
        }
    }

    fun loadWorkspaceLayouts() {
        val saved = repository.loadWorkspaceLayoutState()
        if (saved != null) {
            _workspaceLayoutState.value = saved
        }
    }

    private fun persistWorkspaceLayouts() {
        repository.saveWorkspaceLayoutState(_workspaceLayoutState.value)
    }

    fun selectLayout(layout: WorkspaceLayout) {
        _workspaceLayoutState.update { current ->
            if (layout.isLandscape) {
                current.copy(
                    activeLandscapeId = layout.id,
                    currentLandscapeRatio = layout.horizontalSplitRatio,
                    currentFaderHeightDp = layout.faderHeightDp
                )
            } else {
                current.copy(
                    activePortraitId = layout.id,
                    currentPortraitRatio = layout.horizontalSplitRatio,
                    currentFaderHeightDp = layout.faderHeightDp
                )
            }
        }
        persistWorkspaceLayouts()
    }

    fun updateHorizontalSplitRatio(isLandscape: Boolean, ratio: Float) {
        _workspaceLayoutState.update { current ->
            if (isLandscape) {
                current.copy(currentLandscapeRatio = ratio)
            } else {
                current.copy(currentPortraitRatio = ratio)
            }
        }
        persistWorkspaceLayouts()
    }

    fun updateFaderHeight(heightDp: Int) {
        _workspaceLayoutState.update { it.copy(currentFaderHeightDp = heightDp.coerceIn(120, 450)) }
        persistWorkspaceLayouts()
    }

    fun saveCurrentLayoutAs(name: String, isLandscape: Boolean) {
        if (name.isBlank()) return
        val current = _workspaceLayoutState.value
        val activeLayout = getActiveLayout(isLandscape)

        val newId = "custom_" + UUID.randomUUID().toString().take(8)
        val newLayout = WorkspaceLayout(
            id = newId,
            name = name.trim(),
            isDefault = false,
            isLandscape = isLandscape,
            layoutType = activeLayout.layoutType,
            horizontalSplitRatio = if (isLandscape) current.currentLandscapeRatio else current.currentPortraitRatio,
            faderHeightDp = current.currentFaderHeightDp
        )

        _workspaceLayoutState.update { state ->
            val updatedList = state.customLayouts.filter { it.id != newId } + newLayout
            if (isLandscape) {
                state.copy(
                    activeLandscapeId = newId,
                    customLayouts = updatedList
                )
            } else {
                state.copy(
                    activePortraitId = newId,
                    customLayouts = updatedList
                )
            }
        }
        persistWorkspaceLayouts()
    }

    fun updateCustomLayout(layout: WorkspaceLayout) {
        if (layout.isDefault) return // Safeguard: never overwrite default layouts
        val current = _workspaceLayoutState.value
        val updatedLayout = layout.copy(
            horizontalSplitRatio = if (layout.isLandscape) current.currentLandscapeRatio else current.currentPortraitRatio,
            faderHeightDp = current.currentFaderHeightDp
        )
        _workspaceLayoutState.update { state ->
            state.copy(customLayouts = state.customLayouts.map { if (it.id == layout.id) updatedLayout else it })
        }
        persistWorkspaceLayouts()
    }

    fun deleteCustomLayout(layoutId: String) {
        val current = _workspaceLayoutState.value
        val target = current.customLayouts.find { it.id == layoutId } ?: return
        if (target.isDefault) return // Safeguard

        _workspaceLayoutState.update { state ->
            val remaining = state.customLayouts.filter { it.id != layoutId }
            var newLandId = state.activeLandscapeId
            var newPortId = state.activePortraitId
            if (newLandId == layoutId) {
                newLandId = DEFAULT_LANDSCAPE_LAYOUTS.first().id
            }
            if (newPortId == layoutId) {
                newPortId = DEFAULT_PORTRAIT_LAYOUTS.first().id
            }
            state.copy(
                customLayouts = remaining,
                activeLandscapeId = newLandId,
                activePortraitId = newPortId
            )
        }
        persistWorkspaceLayouts()
    }

    fun resetToDefaultLayout(isLandscape: Boolean) {
        val defaultLayout = if (isLandscape) DEFAULT_LANDSCAPE_LAYOUTS.first() else DEFAULT_PORTRAIT_LAYOUTS.first()
        _workspaceLayoutState.update { state ->
            if (isLandscape) {
                state.copy(
                    activeLandscapeId = defaultLayout.id,
                    currentLandscapeRatio = defaultLayout.horizontalSplitRatio,
                    currentFaderHeightDp = defaultLayout.faderHeightDp
                )
            } else {
                state.copy(
                    activePortraitId = defaultLayout.id,
                    currentPortraitRatio = defaultLayout.horizontalSplitRatio,
                    currentFaderHeightDp = defaultLayout.faderHeightDp
                )
            }
        }
        persistWorkspaceLayouts()
    }

    fun getActiveLayout(isLandscape: Boolean): WorkspaceLayout {
        val state = _workspaceLayoutState.value
        val activeId = if (isLandscape) state.activeLandscapeId else state.activePortraitId
        val allLayouts = (if (isLandscape) DEFAULT_LANDSCAPE_LAYOUTS else DEFAULT_PORTRAIT_LAYOUTS) + state.customLayouts.filter { it.isLandscape == isLandscape }
        return allLayouts.find { it.id == activeId } ?: (if (isLandscape) DEFAULT_LANDSCAPE_LAYOUTS.first() else DEFAULT_PORTRAIT_LAYOUTS.first())
    }
}
