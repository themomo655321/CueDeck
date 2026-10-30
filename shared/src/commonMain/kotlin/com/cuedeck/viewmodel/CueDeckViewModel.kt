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

    init {
        // Auto-follow event listener
        coroutineScope.launch {
            audioEngine.playbackCompletedEvents.collect { completedCue ->
                handlePlaybackCompleted(completedCue)
            }
        }
    }

    private fun createDefaultShow(): Show {
        val showId = UUID.randomUUID().toString()
        val defaultBank = CartBank(
            id = UUID.randomUUID().toString(),
            name = "Main Cart",
            rows = 3,
            cols = 3,
            slots = (0..8).map { CartSlot(it, null) }
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
    fun panicStop(fadeOutMs: Long = 0L) {
        coroutineScope.launch {
            audioEngine.stopAll(fadeOutMs)
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

    fun saveCurrentShow() {
        repository.saveShow(_show.value)
    }

    fun loadShow(showId: String) {
        val loaded = repository.loadShow(showId)
        if (loaded != null) {
            _show.value = loaded
            audioEngine.setMasterVolume(loaded.masterVolume)
        }
    }
}
