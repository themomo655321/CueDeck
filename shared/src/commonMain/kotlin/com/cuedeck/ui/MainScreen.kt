package com.cuedeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.diagnostics.ErrorLogger
import com.cuedeck.model.*
import com.cuedeck.ui.components.*
import com.cuedeck.ui.theme.DarkBackground
import com.cuedeck.ui.theme.PanicRed
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
    val mixerSlots by viewModel.mixerSlots.collectAsState()
    val mixerQueue by viewModel.mixerQueue.collectAsState()
    val faderVolumes by viewModel.faderVolumes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cartPageIndex by viewModel.cartPageIndex.collectAsState()
    val saveFeedback by viewModel.saveFeedback.collectAsState()
    val storageInspection by viewModel.storageInspection.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importFileName by viewModel.importFileName.collectAsState()
    val userErrorMessage by viewModel.userErrorMessage.collectAsState()
    val errorLogs by ErrorLogger.logs.collectAsState()

    var editingCue by remember { mutableStateOf<Cue?>(null) }
    var assigningSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showStorageModal by remember { mutableStateOf(false) }
    var showDiagnosticsModal by remember { mutableStateOf(false) }

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

            // User Error Banner
            AnimatedVisibility(
                visible = userErrorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = PanicRed,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = userErrorMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

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
                            mixerSlots = mixerSlots,
                            queueCount = mixerQueue.size,
                            faderVolumes = faderVolumes,
                            fineModeEnabled = settings.fineModeEnabled,
                            onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
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
                // Mobile Portrait Layout (Phone)
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

                        // 4 Channel Mixer Faders
                        FaderBank(
                            mixerSlots = mixerSlots,
                            queueCount = mixerQueue.size,
                            faderVolumes = faderVolumes,
                            fineModeEnabled = settings.fineModeEnabled,
                            onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
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

    // Modal: Settings & Updates
    if (showSettingsModal) {
        SettingsModal(
            settings = settings,
            onSaveSettings = { viewModel.updateSettings(it) },
            onOpenStorageInspector = {
                viewModel.inspectCurrentStorage()
                showStorageModal = true
            },
            onOpenDiagnostics = {
                showDiagnosticsModal = true
            },
            onCheckForUpdates = onCheckForUpdates,
            onDismiss = { showSettingsModal = false }
        )
    }

    // Modal: Project Storage Bloat Inspector
    if (showStorageModal) {
        StorageInspectorModal(
            inspection = storageInspection,
            onPurgeUnused = { viewModel.purgeUnusedAudio() },
            onOpenInSystem = { viewModel.openStorageFolder() },
            onDismiss = { showStorageModal = false }
        )
    }

    // Modal: Diagnostics & Error Logs
    if (showDiagnosticsModal) {
        DiagnosticsModal(
            logs = errorLogs,
            onClear = { ErrorLogger.clear() },
            onDismiss = { showDiagnosticsModal = false }
        )
    }

    // Dialog: Cloud / Drive Audio Download Progress Bar
    if (importProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing Audio File...") },
            text = {
                Column {
                    Text(
                        text = importFileName ?: "Streaming file from Drive / storage...",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val pct = ((importProgress ?: 0f) * 100).toInt()
                    Text("Progress: $pct%", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (importProgress ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {}
        )
    }

    // Modal: Edit Cue
    editingCue?.let { cue ->
        CueEditorModal(
            cue = cue,
            onGetPeaks = { viewModel.getWaveformPeaks(it) },
            onScrub = { posMs -> viewModel.scrubPreview(cue, posMs) },
            onStartPreview = { startMs, endMs, onUpdate, onDone ->
                viewModel.startPreview(cue, startMs, endMs, onUpdate, onDone)
            },
            onStopPreview = { viewModel.stopPreview() },
            onSave = {
                viewModel.stopPreview()
                viewModel.updateCue(it)
            },
            onDelete = {
                viewModel.stopPreview()
                viewModel.deleteCue(it)
            },
            onDismiss = {
                viewModel.stopPreview()
                editingCue = null
            }
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
