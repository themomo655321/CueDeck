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
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var assigningSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showStorageModal by remember { mutableStateOf(false) }
    var showDiagnosticsModal by remember { mutableStateOf(false) }

    val armedCue = show.findCue(show.armedCueId) ?: show.cues.firstOrNull()

    val screenBg = if (settings.isHighContrastStageTheme) Color(0xFF0D0303) else DarkBackground

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
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
                // Landscape Dual-Pane Layout (Tablet / Rotated Phone)
                // Left: Now Playing + 4 Fader Channels; Right: Standby + Cue Stack + GO Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Column: Now Playing Active Drawer + 4 Mixer Faders
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        ActiveCuesDrawer(
                            activePlaybacks = activePlaybacks,
                            onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) },
                            modifier = Modifier.weight(1f)
                        )

                        FaderBank(
                            mixerSlots = mixerSlots,
                            queueCount = mixerQueue.size,
                            faderVolumes = faderVolumes,
                            fineModeEnabled = settings.fineModeEnabled,
                            onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                            onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) }
                        )
                    }

                    // Right Column: Armed Standby + Full Height Cue Stack + GO Button
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1.3f).fillMaxHeight()
                    ) {
                        ArmedCueCard(armedCue = armedCue)

                        CueListSection(
                            cues = show.cues,
                            armedCueId = armedCue?.id,
                            isPerformanceLocked = show.isPerformanceLocked,
                            onArmCue = { viewModel.armCue(it) },
                            onEditCue = { editingCue = it },
                            onAddCueClick = { showImportChoiceDialog = true },
                            onMoveUp = { viewModel.moveCueUp(it) },
                            onMoveDown = { viewModel.moveCueDown(it) },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        GoButton(
                            armedCue = armedCue,
                            onGoClick = { viewModel.pressGo() },
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        )
                    }
                }
            } else {
                // Mobile Portrait Split-Screen Layout (Phone)
                // Left: Now Playing & Standby; Right: Full-Height Cue Stack; Bottom: Faders + Full-Width GO
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Middle Split Screen: Left (Now Playing & Standby) vs Right (Cue Stack)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Left Column: Armed Standby Card + Now Playing Active Drawer
                        Column(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ArmedCueCard(armedCue = armedCue)

                            ActiveCuesDrawer(
                                activePlaybacks = activePlaybacks,
                                onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Right Column: Dedicated Full Height Cue Stack
                        Box(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                        ) {
                            CueListSection(
                                cues = show.cues,
                                armedCueId = armedCue?.id,
                                isPerformanceLocked = show.isPerformanceLocked,
                                onArmCue = { viewModel.armCue(it) },
                                onEditCue = { editingCue = it },
                                onAddCueClick = { showImportChoiceDialog = true },
                                onMoveUp = { viewModel.moveCueUp(it) },
                                onMoveDown = { viewModel.moveCueDown(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4 Channel Mixer Faders
                    FaderBank(
                        mixerSlots = mixerSlots,
                        queueCount = mixerQueue.size,
                        faderVolumes = faderVolumes,
                        fineModeEnabled = settings.fineModeEnabled,
                        onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                        onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) }
                    )

                    // Persistent Full-Width Giant GO Button at bottom
                    GoButton(
                        armedCue = armedCue,
                        onGoClick = { viewModel.pressGo() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
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

    // Dialog: Import Audio (Local File vs Web Link)
    if (showImportChoiceDialog) {
        ImportAudioDialog(
            onImportLocalFile = onImportAudioRequest,
            onImportUrl = { viewModel.importFromUrl(it) },
            onDismiss = { showImportChoiceDialog = false }
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
