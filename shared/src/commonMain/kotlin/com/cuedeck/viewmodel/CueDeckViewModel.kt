package com.cuedeck.viewmodel

import com.cuedeck.audio.AudioEngine
import com.cuedeck.model.*
import com.cuedeck.storage.ShowRepository
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

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _faderVolumes = MutableStateFlow(listOf(1.0f, 1.0f, 1.0f, 1.0f))
    val faderVolumes: StateFlow<List<Float>> = _faderVolumes.asStateFlow()

    private val _cartPageIndex = MutableStateFlow(0)
    val cartPageIndex: StateFlow<Int> = _cartPageIndex.asStateFlow()

    private val _saveFeedback = MutableStateFlow<String?>(null)
    val saveFeedback: StateFlow<String?> = _saveFeedback.asStateFlow()

    init {
        loadInitialShow()

        // Auto-follow event listener
        coroutineScope.launch {
            audioEngine.playbackCompletedEvents.collect { completedCue ->
                handlePlaybackCompleted(completedCue)
            }
        }
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

    /**
     * Big GO button pressed (QLab sequential trigger)
     */
    fun pressGo() {
        val currentShow = _show.value
        if (currentShow.cues.isEmpty()) return

        val armedCue = currentShow.findCue(currentShow.armedCueId) ?: currentShow.cues.first()

        coroutineScope.launch {
            // Pre-wait delay if configured
            if (armedCue.preWaitMs > 0) {
                delay(armedCue.preWaitMs)
            }

            audioEngine.play(
                cue = armedCue,
                basePath = repository.getBasePath(currentShow.id),
                masterVolume = currentShow.masterVolume
            )

            // Determine next armed cue
            val nextCue = currentShow.nextCueAfter(armedCue.id)
            _show.update { it.copy(armedCueId = nextCue?.id) }

            // Auto-continue (fire simultaneously)
            if (armedCue.followAction == FollowAction.AUTO_CONTINUE && nextCue != null) {
                if (armedCue.postWaitMs > 0) delay(armedCue.postWaitMs)
                pressGo()
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
            if (active != null) {
                when (cue.triggerMode) {
                    TriggerMode.RETRIGGER -> {
                        audioEngine.stop(active.playbackId, 0L)
                        audioEngine.play(cue, repository.getBasePath(currentShow.id), currentShow.masterVolume)
                    }
                    TriggerMode.LOOP, TriggerMode.PLAY_ONCE -> {
                        // Toggle stop with fade out
                        audioEngine.stop(active.playbackId, cue.fadeOutMs)
                    }
                    TriggerMode.GATE -> {
                        audioEngine.stop(active.playbackId, 0L)
                    }
                }
            } else {
                audioEngine.play(cue, repository.getBasePath(currentShow.id), currentShow.masterVolume)
            }
        }
    }

    /**
     * Set fader volume (0.0 to 1.25) for a specific mixer channel (0..3).
     * Automatically maps to the active sound on that channel with configured smoothing.
     */
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

    /**
     * Stop a specific active playback
     */
    fun stopActivePlayback(playbackId: String, fadeOutMs: Long = 500L) {
        coroutineScope.launch {
            audioEngine.stop(playbackId, fadeOutMs)
        }
    }

    /**
     * Emergency Panic Stop: Stops all active audio immediately or with panic fade
     */
    fun panicStop(fadeOutMs: Long? = null) {
        val effectiveFade = fadeOutMs ?: _settings.value.panicFadeMs
        coroutineScope.launch {
            audioEngine.stopAll(effectiveFade)
        }
    }

    fun armCue(cueId: String) {
        _show.update { it.copy(armedCueId = cueId) }
    }

    fun togglePerformanceLock() {
        _show.update { it.copy(isPerformanceLocked = !it.isPerformanceLocked) }
    }

    fun switchViewMode(mode: ViewMode) {
        _show.update { it.copy(currentViewMode = mode) }
    }

    fun setMasterVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1.5f)
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
        if (userInitiated) {
            coroutineScope.launch {
                _saveFeedback.value = "Show Saved ✓"
                delay(2000)
                _saveFeedback.value = null
            }
        }
    }

    fun loadShow(showId: String) {
        val loaded = repository.loadShow(showId)
        if (loaded != null) {
            _show.value = loaded
            audioEngine.setMasterVolume(loaded.masterVolume)
        }
    }
}
