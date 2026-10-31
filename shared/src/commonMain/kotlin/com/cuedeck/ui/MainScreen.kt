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
    onHapticFeedback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val show by viewModel.show.collectAsState()
    val activePlaybacks by viewModel.activePlaybacks.collectAsState()
    val mixerSlots by viewModel.mixerSlots.collectAsState()
    val mixerQueue by viewModel.mixerQueue.collectAsState()
    val faderVolumes by viewModel.faderVolumes.collectAsState()
    val faderTailEnabled by viewModel.faderTailEnabled.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cartPageIndex by viewModel.cartPageIndex.collectAsState()
    val saveFeedback by viewModel.saveFeedback.collectAsState()
    val storageInspection by viewModel.storageInspection.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val importFileName by viewModel.importFileName.collectAsState()
    val isImportMinimized by viewModel.isImportMinimized.collectAsState()
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
    var showLayoutManagerModal by remember { mutableStateOf(false) }
    var isCurrentOrientationLandscape by remember { mutableStateOf(false) }

    val armedCue = show.findCue(show.armedCueId) ?: show.cues.firstOrNull()

    val screenBg = if (settings.isHighContrastStageTheme) Color(0xFF0D0303) else DarkBackground

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        val isWideScreen = maxWidth > 650.dp // Tablet or landscape orientation
        isCurrentOrientationLandscape = isWideScreen
        val activeLayout = viewModel.getActiveLayout(isWideScreen)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val workspaceLayoutState by viewModel.workspaceLayoutState.collectAsState()

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
                onPanicStop = {
                    onHapticFeedback()
                    viewModel.panicStop()
                },
                onSaveShow = { viewModel.saveCurrentShow(userInitiated = true) },
                onOpenShowManager = {
                    viewModel.refreshSavedShows()
                    showShowManagerModal = true
                },
                onOpenLayoutManager = { showLayoutManagerModal = true },
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
                // LANDSCAPE WORKSPACE LAYOUTS
                when (activeLayout.layoutType) {
                    LayoutType.CUE_MASTER_LEFT -> {
                        // Reversed: Cue Stack on Left, Full-Height Mixer on Right
                        val leftRatio = workspaceLayoutState.currentLandscapeRatio.coerceIn(0.20f, 0.80f)
                        val rightRatio = 1f - leftRatio

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(leftRatio)
                                    .fillMaxHeight()
                            ) {
                                ArmedCueCard(armedCue = armedCue, onEditCue = { editingCue = it })
                                ActiveCuesDrawer(activePlaybacks = activePlaybacks, onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }, modifier = Modifier.fillMaxWidth())
                                CueListSection(
                                    cues = show.cues,
                                    armedCueId = armedCue?.id,
                                    isPerformanceLocked = show.isPerformanceLocked,
                                    onArmCue = { onHapticFeedback(); viewModel.armCue(it) },
                                    onEditCue = { editingCue = it },
                                    onAddCueClick = { showImportChoiceDialog = true },
                                    onMoveUp = { viewModel.moveCueUp(it) },
                                    onMoveDown = { viewModel.moveCueDown(it) },
                                    onMoveCue = { from, to -> viewModel.moveCue(from, to) },
                                    onDuplicateCue = { onHapticFeedback(); viewModel.duplicateCue(it) },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                GoButton(
                                    armedCue = armedCue,
                                    onGoClick = { onHapticFeedback(); viewModel.pressGo() },
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                )
                            }

                            HorizontalResizeDivider(
                                splitRatio = leftRatio,
                                onRatioChange = { viewModel.updateHorizontalSplitRatio(isLandscape = true, it) },
                                totalWidthPx = totalWidthPx
                            )

                            FaderBank(
                                mixerSlots = mixerSlots,
                                queueCount = mixerQueue.size,
                                faderVolumes = faderVolumes,
                                faderTailEnabled = faderTailEnabled,
                                fineModeEnabled = settings.fineModeEnabled,
                                onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                                onToggleFaderTail = { viewModel.toggleFaderTail(it) },
                                onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) },
                                modifier = Modifier
                                    .weight(rightRatio)
                                    .fillMaxHeight()
                            )
                        }
                    }
                    LayoutType.BROADCAST_BOTTOM -> {
                        // Broadcast Mode: Stack & Monitors on Top, Mixer across Bottom
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(0.9f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ArmedCueCard(armedCue = armedCue, onEditCue = { editingCue = it })
                                    ActiveCuesDrawer(activePlaybacks = activePlaybacks, onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }, modifier = Modifier.weight(1f))
                                }
                                Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                                    CueListSection(
                                        cues = show.cues,
                                        armedCueId = armedCue?.id,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onArmCue = { onHapticFeedback(); viewModel.armCue(it) },
                                        onEditCue = { editingCue = it },
                                        onAddCueClick = { showImportChoiceDialog = true },
                                        onMoveUp = { viewModel.moveCueUp(it) },
                                        onMoveDown = { viewModel.moveCueDown(it) },
                                        onMoveCue = { from, to -> viewModel.moveCue(from, to) },
                                        onDuplicateCue = { onHapticFeedback(); viewModel.duplicateCue(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            VerticalResizeDivider(
                                onDragDelta = { dy ->
                                    val newH = (workspaceLayoutState.currentFaderHeightDp - dy.toInt()).coerceIn(120, 350)
                                    viewModel.updateFaderHeight(newH)
                                }
                            )

                            FaderBank(
                                mixerSlots = mixerSlots,
                                queueCount = mixerQueue.size,
                                faderVolumes = faderVolumes,
                                faderTailEnabled = faderTailEnabled,
                                fineModeEnabled = settings.fineModeEnabled,
                                onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                                onToggleFaderTail = { viewModel.toggleFaderTail(it) },
                                onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) },
                                modifier = Modifier.fillMaxWidth().height(workspaceLayoutState.currentFaderHeightDp.dp)
                            )

                            GoButton(
                                armedCue = armedCue,
                                onGoClick = { onHapticFeedback(); viewModel.pressGo() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(58.dp)
                            )
                        }
                    }
                    else -> {
                        // Default Studio Console (CONSOLE_LEFT_MIXER): Full-Height Mixer on Left, Stack on Right
                        val leftRatio = workspaceLayoutState.currentLandscapeRatio.coerceIn(0.20f, 0.80f)
                        val rightRatio = 1f - leftRatio

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FaderBank(
                                mixerSlots = mixerSlots,
                                queueCount = mixerQueue.size,
                                faderVolumes = faderVolumes,
                                faderTailEnabled = faderTailEnabled,
                                fineModeEnabled = settings.fineModeEnabled,
                                onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                                onToggleFaderTail = { viewModel.toggleFaderTail(it) },
                                onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) },
                                modifier = Modifier
                                    .weight(leftRatio)
                                    .fillMaxHeight()
                            )

                            HorizontalResizeDivider(
                                splitRatio = leftRatio,
                                onRatioChange = { viewModel.updateHorizontalSplitRatio(isLandscape = true, it) },
                                totalWidthPx = totalWidthPx
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(rightRatio)
                                    .fillMaxHeight()
                            ) {
                                ArmedCueCard(armedCue = armedCue, onEditCue = { editingCue = it })
                                ActiveCuesDrawer(activePlaybacks = activePlaybacks, onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }, modifier = Modifier.fillMaxWidth())
                                CueListSection(
                                    cues = show.cues,
                                    armedCueId = armedCue?.id,
                                    isPerformanceLocked = show.isPerformanceLocked,
                                    onArmCue = { onHapticFeedback(); viewModel.armCue(it) },
                                    onEditCue = { editingCue = it },
                                    onAddCueClick = { showImportChoiceDialog = true },
                                    onMoveUp = { viewModel.moveCueUp(it) },
                                    onMoveDown = { viewModel.moveCueDown(it) },
                                    onMoveCue = { from, to -> viewModel.moveCue(from, to) },
                                    onDuplicateCue = { onHapticFeedback(); viewModel.duplicateCue(it) },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                GoButton(
                                    armedCue = armedCue,
                                    onGoClick = { onHapticFeedback(); viewModel.pressGo() },
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // PORTRAIT WORKSPACE LAYOUTS
                when (activeLayout.layoutType) {
                    LayoutType.SPLIT_MOBILE -> {
                        // Split Screen Mobile: Controls on Left, Stack on Right, Faders Bottom
                        val leftRatio = workspaceLayoutState.currentPortraitRatio.coerceIn(0.25f, 0.75f)
                        val rightRatio = 1f - leftRatio

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(leftRatio).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ArmedCueCard(armedCue = armedCue, onEditCue = { editingCue = it })
                                    ActiveCuesDrawer(activePlaybacks = activePlaybacks, onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }, modifier = Modifier.weight(1f))
                                }

                                HorizontalResizeDivider(
                                    splitRatio = leftRatio,
                                    onRatioChange = { viewModel.updateHorizontalSplitRatio(isLandscape = false, it) },
                                    totalWidthPx = totalWidthPx
                                )

                                Box(modifier = Modifier.weight(rightRatio).fillMaxHeight()) {
                                    CueListSection(
                                        cues = show.cues,
                                        armedCueId = armedCue?.id,
                                        isPerformanceLocked = show.isPerformanceLocked,
                                        onArmCue = { onHapticFeedback(); viewModel.armCue(it) },
                                        onEditCue = { editingCue = it },
                                        onAddCueClick = { showImportChoiceDialog = true },
                                        onMoveUp = { viewModel.moveCueUp(it) },
                                        onMoveDown = { viewModel.moveCueDown(it) },
                                        onMoveCue = { from, to -> viewModel.moveCue(from, to) },
                                        onDuplicateCue = { onHapticFeedback(); viewModel.duplicateCue(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            VerticalResizeDivider(
                                onDragDelta = { dy ->
                                    val newH = (workspaceLayoutState.currentFaderHeightDp - dy.toInt()).coerceIn(120, 320)
                                    viewModel.updateFaderHeight(newH)
                                }
                            )

                            FaderBank(
                                mixerSlots = mixerSlots,
                                queueCount = mixerQueue.size,
                                faderVolumes = faderVolumes,
                                faderTailEnabled = faderTailEnabled,
                                fineModeEnabled = settings.fineModeEnabled,
                                onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                                onToggleFaderTail = { viewModel.toggleFaderTail(it) },
                                onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) },
                                modifier = Modifier.fillMaxWidth().height(workspaceLayoutState.currentFaderHeightDp.dp)
                            )

                            GoButton(
                                armedCue = armedCue,
                                onGoClick = { onHapticFeedback(); viewModel.pressGo() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(64.dp)
                            )
                        }
                    }
                    else -> {
                        // Default Classic Theatre (CLASSIC_THEATRE): Armed/Now Playing Top, Stack Mid, Faders Bottom, GO Bottom
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            ArmedCueCard(armedCue = armedCue, onEditCue = { editingCue = it })
                            Spacer(modifier = Modifier.height(4.dp))
                            ActiveCuesDrawer(activePlaybacks = activePlaybacks, onStopCue = { id, fade -> viewModel.stopActivePlayback(id, fade) }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                CueListSection(
                                    cues = show.cues,
                                    armedCueId = armedCue?.id,
                                    isPerformanceLocked = show.isPerformanceLocked,
                                    onArmCue = { onHapticFeedback(); viewModel.armCue(it) },
                                    onEditCue = { editingCue = it },
                                    onAddCueClick = { showImportChoiceDialog = true },
                                    onMoveUp = { viewModel.moveCueUp(it) },
                                    onMoveDown = { viewModel.moveCueDown(it) },
                                    onMoveCue = { from, to -> viewModel.moveCue(from, to) },
                                    onDuplicateCue = { onHapticFeedback(); viewModel.duplicateCue(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            VerticalResizeDivider(
                                onDragDelta = { dy ->
                                    val newH = (workspaceLayoutState.currentFaderHeightDp - dy.toInt()).coerceIn(120, 320)
                                    viewModel.updateFaderHeight(newH)
                                }
                            )

                            FaderBank(
                                mixerSlots = mixerSlots,
                                queueCount = mixerQueue.size,
                                faderVolumes = faderVolumes,
                                faderTailEnabled = faderTailEnabled,
                                fineModeEnabled = settings.fineModeEnabled,
                                onFineModeToggle = { viewModel.updateSettings(settings.copy(fineModeEnabled = it)) },
                                onToggleFaderTail = { viewModel.toggleFaderTail(it) },
                                onFaderChange = { ch, vol -> viewModel.setFaderVolume(ch, vol) },
                                modifier = Modifier.fillMaxWidth().height(workspaceLayoutState.currentFaderHeightDp.dp)
                            )

                            GoButton(
                                armedCue = armedCue,
                                onGoClick = { onHapticFeedback(); viewModel.pressGo() },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(64.dp)
                            )
                        }
                    }
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

    // Modal: Workspace Layout Manager
    if (showLayoutManagerModal) {
        LayoutManagerModal(
            viewModel = viewModel,
            isLandscapeCurrent = isCurrentOrientationLandscape,
            onDismiss = { showLayoutManagerModal = false }
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
