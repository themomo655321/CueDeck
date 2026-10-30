package com.cuedeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cuedeck.model.*
import com.cuedeck.ui.components.*
import com.cuedeck.ui.theme.DarkBackground
import com.cuedeck.viewmodel.CueDeckViewModel

@Composable
fun MainScreen(
    viewModel: CueDeckViewModel,
    onImportAudioRequest: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val show by viewModel.show.collectAsState()
    val activePlaybacks by viewModel.activePlaybacks.collectAsState()
    val faderVolumes by viewModel.faderVolumes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cartPageIndex by viewModel.cartPageIndex.collectAsState()
    val saveFeedback by viewModel.saveFeedback.collectAsState()

    var editingCue by remember { mutableStateOf<Cue?>(null) }
    var assigningSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsModal by remember { mutableStateOf(false) }

    val armedCue = show.findCue(show.armedCueId) ?: show.cues.firstOrNull()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        val isWideScreen = maxWidth > 650.dp // Tablet or landscape orientation

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent Top Safety Bar with Save button & Settings button
            TopSafetyBar(
                title = show.title,
                viewMode = show.currentViewMode,
                isPerformanceLocked = show.isPerformanceLocked,
                masterVolume = show.masterVolume,
                onViewModeChange = { viewModel.switchViewMode(it) },
                onToggleLock = { viewModel.togglePerformanceLock() },
                onVolumeChange = { viewModel.setMasterVolume(it) },
                onPanicStop = { viewModel.panicStop() },
                onSaveShow = { viewModel.saveCurrentShow(userInitiated = true) },
                saveFeedback = saveFeedback,
                onOpenSettings = { showSettingsModal = true }
            )

            if (isWideScreen) {
                // Tablet Dual-Pane Layout (ADVAN Sketsa 2 10.1" landscape / desktop)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column: Cue List or Soundboard Matrix + 4 Faders
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1.3f).fillMaxHeight()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (show.currentViewMode) {
                                ViewMode.CUE_LIST -> {
                                    CueListSection(
                                        cues = show.cues,
                                        armedCueId = armedCue?.id,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onArmCue = { viewModel.armCue(it) },
                                        onEditCue = { editingCue = it },
                                        onAddCueClick = onImportAudioRequest
                                    )
                                }
                                ViewMode.CART_MATRIX -> {
                                    CartGridSection(
                                        banks = show.cartBanks,
                                        activeBankIndex = show.activeBankIndex,
                                        cartPageIndex = cartPageIndex,
                                        cues = show.cues,
                                        activePlaybacks = activePlaybacks,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onSelectBank = { viewModel.setActiveBank(it) },
                                        onPrevPage = { viewModel.prevCartPage() },
                                        onNextPage = { viewModel.nextCartPage() },
                                        onTriggerSlot = { viewModel.triggerCartSlot(it) },
                                        onAssignSlot = { assigningSlotIndex = it }
                                    )
                                }
                            }
                        }

                        // 4 Live Mixer Faders on Tablet
                        FaderBank(
                            activePlaybacks = activePlaybacks,
                            faderVolumes = faderVolumes,
                            smoothingPercent = settings.faderStreamLinePercent,
                            onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) }
                        )
                    }

                    // Right Column: Armed Standby, Active Countdown, Big GO
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ArmedCueCard(armedCue = armedCue)
                            ActiveCuesDrawer(
                                activePlaybacks = activePlaybacks,
                                onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }
                            )
                        }

                        GoButton(
                            armedCue = armedCue,
                            onGoClick = { viewModel.pressGo() }
                        )
                    }
                }
            } else {
                // Mobile Portrait Layout (Phone - exactly as drawn in screenshot!)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ArmedCueCard(armedCue = armedCue)

                        ActiveCuesDrawer(
                            activePlaybacks = activePlaybacks,
                            onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }
                        )

                        // Middle Content Section
                        Box(modifier = Modifier.weight(1f)) {
                            when (show.currentViewMode) {
                                ViewMode.CUE_LIST -> {
                                    CueListSection(
                                        cues = show.cues,
                                        armedCueId = armedCue?.id,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onArmCue = { viewModel.armCue(it) },
                                        onEditCue = { editingCue = it },
                                        onAddCueClick = onImportAudioRequest,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                ViewMode.CART_MATRIX -> {
                                    CartGridSection(
                                        banks = show.cartBanks,
                                        activeBankIndex = show.activeBankIndex,
                                        cartPageIndex = cartPageIndex,
                                        cues = show.cues,
                                        activePlaybacks = activePlaybacks,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onSelectBank = { viewModel.setActiveBank(it) },
                                        onPrevPage = { viewModel.prevCartPage() },
                                        onNextPage = { viewModel.nextCartPage() },
                                        onTriggerSlot = { viewModel.triggerCartSlot(it) },
                                        onAssignSlot = { assigningSlotIndex = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 4 Channel Mixer Faders with Procreate StreamLine Smoothing!
                        FaderBank(
                            activePlaybacks = activePlaybacks,
                            faderVolumes = faderVolumes,
                            smoothingPercent = settings.faderStreamLinePercent,
                            onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) }
                        )
                    }

                    // Persistent Giant GO Button at bottom
                    GoButton(
                        armedCue = armedCue,
                        onGoClick = { viewModel.pressGo() },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }

    // Modal: Settings, Fader StreamLine & Auto Updates
    if (showSettingsModal) {
        SettingsModal(
            settings = settings,
            onSaveSettings = { viewModel.updateSettings(it) },
            onCheckForUpdates = onCheckForUpdates,
            onDismiss = { showSettingsModal = false }
        )
    }

    // Modal: Edit Cue
    editingCue?.let { cue ->
        CueEditorModal(
            cue = cue,
            onSave = { viewModel.updateCue(it) },
            onDelete = { viewModel.deleteCue(it) },
            onDismiss = { editingCue = null }
        )
    }

    // Modal: Assign Cue to Soundboard Slot
    assigningSlotIndex?.let { slotIndex ->
        AlertDialog(
            onDismissRequest = { assigningSlotIndex = null },
            title = { Text("Assign Cue to Pad ${slotIndex + 1}") },
            text = {
                Column {
                    if (show.cues.isEmpty()) {
                        Text("No cues available. Add cues in the Cue Stack first.")
                    } else {
                        show.cues.forEach { cue ->
                            TextButton(
                                onClick = {
                                    viewModel.assignCueToCartSlot(show.activeBankIndex, slotIndex, cue.id)
                                    assigningSlotIndex = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${cue.cueNumber} - ${cue.name}")
                            }
                        }
                        TextButton(
                            onClick = {
                                viewModel.assignCueToCartSlot(show.activeBankIndex, slotIndex, null)
                                assigningSlotIndex = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Pad (Unassign)", color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { assigningSlotIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
