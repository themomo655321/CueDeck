package com.cuedeck.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    val isImportMinimized by viewModel.isImportMinimized.collectAsState()
    val autoCrossfadeEnabled by viewModel.autoCrossfadeEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val savedShows by viewModel.savedShows.collectAsState()
    val userErrorMessage by viewModel.userErrorMessage.collectAsState()
    val errorLogs by ErrorLogger.logs.collectAsState()

    var editingCue by remember { mutableStateOf<Cue?>(null) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var assigningSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showStorageModal by remember { mutableStateOf(false) }
    var showDiagnosticsModal by remember { mutableStateOf(false) }
    var showShowManagerModal by remember { mutableStateOf(false) }

    val armedCue = show.findCue(show.armedCueId) ?: show.cues.firstOrNull()

    val screenBg = if (settings.isHighContrastStageTheme) Color(0xFF0D0303) else DarkBackground

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        val isWideScreen = maxWidth > 650.dp // Tablet or landscape orientation

        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent Top Safety Bar with Show Switcher, Lock & Settings
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
                onOpenShowManager = {
                    viewModel.refreshSavedShows()
                    showShowManagerModal = true
                },
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
                            onMoveCue = { from, to -> viewModel.moveCue(from, to) },
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
                                onMoveCue = { from, to -> viewModel.moveCue(from, to) },
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

        // Floating Non-blocking Toast Notification (Positioned below TopSafetyBar, never obstructs buttons)
        AnimatedVisibility(
            visible = saveFeedback != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .zIndex(100f)
        ) {
            Surface(
                color = Color(0xFF1B5E20),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF69F0AE),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = saveFeedback ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Minimized Floating Background Download Indicator
        AnimatedVisibility(
            visible = importProgress != null && isImportMinimized,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 74.dp)
                .zIndex(90f)
        ) {
            Surface(
                color = Color(0xFF1A2A38),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(24.dp))
                    .clickable { viewModel.expandImport() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    val pct = ((importProgress ?: 0f) * 100).toInt()
                    CircularProgressIndicator(
                        progress = { (importProgress ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading $pct%: ${(importFileName ?: "Audio").take(22)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.cancelImport() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal: Settings & Updates
    if (showSettingsModal) {
        SettingsModal(
            settings = settings,
            autoCrossfadeEnabled = autoCrossfadeEnabled,
            crossfadeDurationMs = crossfadeDurationMs,
            onToggleAutoCrossfade = { viewModel.setAutoCrossfadeEnabled(it) },
            onChangeCrossfadeDuration = { viewModel.setCrossfadeDuration(it) },
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

    // Modal: Show Files Manager
    if (showShowManagerModal) {
        ShowManagerModal(
            currentShowId = show.id,
            currentShowTitle = show.title,
            savedShows = savedShows,
            onCreateNewShow = { viewModel.createNewShow(it) },
            onLoadShow = { viewModel.loadShowById(it) },
            onSaveShowAs = { viewModel.saveShowAs(it) },
            onRenameShow = { viewModel.renameCurrentShow(it) },
            onDeleteShow = { viewModel.deleteShowById(it) },
            onDismiss = { showShowManagerModal = false }
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

    // Dialog: Cloud / Drive Audio Download Progress Bar (with Cancel and Background options)
    if (importProgress != null && !isImportMinimized) {
        AlertDialog(
            onDismissRequest = { viewModel.minimizeImport() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downloading Audio File...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.minimizeImport() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Minimize, contentDescription = "Minimize", tint = Color.Gray)
                    }
                }
            },
            text = {
                Column {
                    Text(
                        text = importFileName ?: "Streaming file from source...",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val pct = ((importProgress ?: 0f) * 100).toInt()
                    Text("Progress: $pct%", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (importProgress ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.minimizeImport() }) {
                    Text("Run in Background", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) {
                    Text("Cancel Download", color = PanicRed)
                }
            }
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
