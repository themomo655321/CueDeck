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

    private val _activeTab = MutableStateFlow(0) // 0 = Cue Stack, 1 = Soundboard
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _cartPageIndex = MutableStateFlow(0)
    val cartPageIndex: StateFlow<Int> = _cartPageIndex.asStateFlow()

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

    private val _autoCrossfadeEnabled = MutableStateFlow(true)
    val autoCrossfadeEnabled: StateFlow<Boolean> = _autoCrossfadeEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(1500L)
    val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private val _savedShows = MutableStateFlow<List<ShowSummary>>(emptyList())
    val savedShows: StateFlow<List<ShowSummary>> = _savedShows.asStateFlow()

    private val _userErrorMessage = MutableStateFlow<String?>(null)
    val userErrorMessage: StateFlow<String?> = _userErrorMessage.asStateFlow()

    init {
        loadInitialShow()
        restoreSessionState()
        refreshSavedShows()

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

    fun switchViewMode(mode: ViewMode) {
        _show.update { it.copy(currentViewMode = mode) }
    }

    fun armCue(cueId: String) {
        setArmedCue(cueId)
    }

    private fun loadInitialShow() {
        val existing = repository.loadLastActiveShow()
        if (existing != null) {
            _show.value = existing
            audioEngine.setMasterVolume(existing.masterVolume)
        } else {
            saveCurrentShow()
        }
    }

    private fun createDefaultShow(): Show {
        val showId = "default_show"
        val defaultBank = CartBank(
            id = "default_bank_main",
            name = "Main Cart",
            rows = 1,
            cols = 3,
            slots = (0..17).map { CartSlot(it, null) }
        )
        return Show(
            id = showId,
            title = "New Show",
            cues = emptyList(),
            cartBanks = listOf(defaultBank),
            activeBankIndex = 0,
            masterVolume = 1.0f,
            armedCueId = null,
            isPerformanceLocked = false,
            currentViewMode = ViewMode.CUE_LIST
        )
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
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

                // If auto-crossfade is enabled, smoothly fade out any currently playing tracks
                if (_autoCrossfadeEnabled.value) {
                    val active = activePlaybacks.value
                    val xFadeMs = _crossfadeDurationMs.value
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

    /**
     * Triggers a pad in the SoundShow cart matrix
     */
    fun triggerCartSlot(slotIndex: Int) {
        val currentShow = _show.value
        val bank = currentShow.cartBanks.getOrNull(currentShow.activeBankIndex) ?: return
        val slot = bank.slots.find { it.index == slotIndex } ?: return
        val cue = currentShow.findCue(slot.cueId) ?: return

        val active = activePlaybacks.value.find { it.cueId == cue.id }

        coroutineScope.launch {
            try {
                if (active != null) {
                    when (cue.triggerMode) {
                        TriggerMode.RETRIGGER -> {
                            audioEngine.stop(active.playbackId, 0L)
                            audioEngine.play(cue, repository.getBasePath(currentShow.id), currentShow.masterVolume)
                        }
                        TriggerMode.LOOP, TriggerMode.PLAY_ONCE -> {
                            audioEngine.stop(active.playbackId, cue.fadeOutMs)
                        }
                        TriggerMode.GATE -> {
                            audioEngine.stop(active.playbackId, 0L)
                        }
                    }
                } else {
                    audioEngine.play(cue, repository.getBasePath(currentShow.id), currentShow.masterVolume)
                }
            } catch (e: Exception) {
                ErrorLogger.error("CueDeckViewModel", "Error triggering cart pad $slotIndex", e)
                showUserError("Pad playback failed: ${e.message}")
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
                audioEngine.setTrackVolume(activeTrack.playbackId, clamped, _settings.value.faderSmoothingMs)
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

    fun nextCartPage() {
        val totalPages = 6 // 18 slots / 3
        _cartPageIndex.update { (it + 1) % totalPages }
    }

    fun prevCartPage() {
        val totalPages = 6
        _cartPageIndex.update { (it - 1 + totalPages) % totalPages }
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

    fun setActiveBank(bankIndex: Int) {
        _show.update { it.copy(activeBankIndex = bankIndex) }
    }

    fun addCue(cue: Cue) {
        _show.update { show ->
            val updatedCues = show.cues + cue
            show.copy(
                cues = updatedCues,
                armedCueId = show.armedCueId ?: cue.id
            )
        }
        saveCurrentShow()
    }

    fun updateCue(updatedCue: Cue) {
        _show.update { show ->
            show.copy(cues = show.cues.map { if (it.id == updatedCue.id) updatedCue else it })
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
            // Renumber cues sequentially 1.0, 2.0, 3.0...
            val renumbered = list.mapIndexed { idx, cue ->
                cue.copy(cueNumber = "%.1f".format(idx + 1.0))
            }
            show.copy(cues = renumbered)
        }
        saveCurrentShow()
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
                        val nextNum = (_show.value.cues.size + 1).toDouble()
                        val newCue = Cue(
                            id = UUID.randomUUID().toString(),
                            cueNumber = "%.1f".format(nextNum),
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

    fun setAutoCrossfadeEnabled(enabled: Boolean) {
        _autoCrossfadeEnabled.value = enabled
    }

    fun setCrossfadeDuration(ms: Long) {
        _crossfadeDurationMs.value = ms
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

    fun loadShowById(showId: String) {
        val loaded = repository.loadShow(showId)
        if (loaded != null) {
            coroutineScope.launch {
                audioEngine.stopAll(fadeOutMs = 0L)
                _show.value = loaded
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

    fun assignCueToCartSlot(bankIndex: Int, slotIndex: Int, cueId: String?) {
        _show.update { show ->
            val bank = show.cartBanks.getOrNull(bankIndex) ?: return@update show
            val newSlots = bank.slots.map { if (it.index == slotIndex) it.copy(cueId = cueId) else it }
            val updatedBank = bank.copy(slots = newSlots)
            val updatedBanks = show.cartBanks.mapIndexed { idx, b -> if (idx == bankIndex) updatedBank else b }
            show.copy(cartBanks = updatedBanks)
        }
        saveCurrentShow()
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
            activeTab = _activeTab.value,
            cartPageIndex = _cartPageIndex.value,
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
            _activeTab.value = state.activeTab
            _cartPageIndex.value = state.cartPageIndex
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
}
