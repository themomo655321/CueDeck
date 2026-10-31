package com.cuedeck.viewmodel

import com.cuedeck.audio.AudioEngine
import com.cuedeck.diagnostics.ErrorLogger
import com.cuedeck.model.*
import com.cuedeck.storage.SessionState
import com.cuedeck.storage.ShowRepository
import com.cuedeck.storage.StorageInspection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class CueDeckViewModel(
    private val repository: ShowRepository,
    private val audioEngine: AudioEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private val _show = MutableStateFlow(createDefaultShow())
    val show: StateFlow<Show> = _show.asStateFlow()

    val activePlaybacks: StateFlow<List<ActivePlayback>> = audioEngine.activePlaybacks

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

    private val _storageInspection = MutableStateFlow<StorageInspection?>(null)
    val storageInspection: StateFlow<StorageInspection?> = _storageInspection.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    private val _importFileName = MutableStateFlow<String?>(null)
    val importFileName: StateFlow<String?> = _importFileName.asStateFlow()

    private val _userErrorMessage = MutableStateFlow<String?>(null)
    val userErrorMessage: StateFlow<String?> = _userErrorMessage.asStateFlow()

    init {
        loadInitialShow()
        restoreSessionState()

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

            val activeTrack = activePlaybacks.value.getOrNull(channelIndex)
            if (activeTrack != null) {
                audioEngine.setTrackVolume(activeTrack.playbackId, clamped, _settings.value.faderSmoothingMs)
            }
        }
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
        if (userInitiated) {
            coroutineScope.launch {
                _saveFeedback.value = "Show Saved ✓"
                delay(2000)
                _saveFeedback.value = null
            }
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
