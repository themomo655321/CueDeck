package com.cuedeck.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cuedeck.model.*
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed
import com.cuedeck.viewmodel.CueDeckViewModel

@Composable
fun LayoutManagerModal(
    viewModel: CueDeckViewModel,
    isLandscapeCurrent: Boolean,
    onDismiss: () -> Unit
) {
    val layoutState by viewModel.workspaceLayoutState.collectAsState()
    var selectedOrientationIsLandscape by remember { mutableStateOf(isLandscapeCurrent) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newLayoutName by remember { mutableStateOf("") }

    val defaultLayouts = if (selectedOrientationIsLandscape) DEFAULT_LANDSCAPE_LAYOUTS else DEFAULT_PORTRAIT_LAYOUTS
    val customLayouts = layoutState.customLayouts.filter { it.isLandscape == selectedOrientationIsLandscape }
    val activeId = if (selectedOrientationIsLandscape) layoutState.activeLandscapeId else layoutState.activePortraitId

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WORKSPACE LAYOUTS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Orientation Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedOrientationIsLandscape) Color(0xFF00E5FF) else Color.Transparent)
                            .clickable { selectedOrientationIsLandscape = true }
                    ) {
                        Text(
                            text = "LANDSCAPE MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedOrientationIsLandscape) Color.Black else Color.Gray
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!selectedOrientationIsLandscape) Color(0xFF00E5FF) else Color.Transparent)
                            .clickable { selectedOrientationIsLandscape = false }
                    ) {
                        Text(
                            text = "PORTRAIT MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!selectedOrientationIsLandscape) Color.Black else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Layout List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section 1: Factory Default Layouts (Protected & Read-Only)
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FACTORY DEFAULT PRESETS (READ-ONLY)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(defaultLayouts) { layout ->
                        val isActive = layout.id == activeId
                        LayoutPresetCard(
                            layout = layout,
                            isActive = isActive,
                            isDefault = true,
                            onApply = { viewModel.selectLayout(layout) },
                            onUpdate = {},
                            onDelete = {}
                        )
                    }

                    // Section 2: Custom Saved Layouts
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Custom",
                                tint = Color(0xFF69F0AE),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CUSTOM SAVED LAYOUTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF69F0AE),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (customLayouts.isEmpty()) {
                        item {
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "No custom layouts saved for this orientation yet. Drag the splitters on the main screen and tap 'Save Current Layout As...' below.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(customLayouts) { layout ->
                            val isActive = layout.id == activeId
                            LayoutPresetCard(
                                layout = layout,
                                isActive = isActive,
                                isDefault = false,
                                onApply = { viewModel.selectLayout(layout) },
                                onUpdate = { viewModel.updateCustomLayout(layout) },
                                onDelete = { viewModel.deleteCustomLayout(layout.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons: Save Current Layout & Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset to Factory Default Button
                    OutlinedButton(
                        onClick = { viewModel.resetToDefaultLayout(selectedOrientationIsLandscape) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Default", fontSize = 12.sp)
                    }

                    // Save As New Layout Button
                    Button(
                        onClick = {
                            newLayoutName = ""
                            showSaveDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1.3f).height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Layout As...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Save Layout Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text("Save Custom Layout", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Factory defaults cannot be overwritten. Enter a name to save your current splitters and panel positions as a new custom layout:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newLayoutName,
                        onValueChange = { newLayoutName = it },
                        label = { Text("Layout Name") },
                        placeholder = { Text("e.g. Sunday Service Console") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLayoutName.isNotBlank()) {
                            viewModel.saveCurrentLayoutAs(newLayoutName, selectedOrientationIsLandscape)
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Save Layout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun LayoutPresetCard(
    layout: WorkspaceLayout,
    isActive: Boolean,
    isDefault: Boolean,
    onApply: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isActive) Color(0xFF1B2B38) else Color(0xFF1E1E1E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) Color(0xFF00E5FF) else Color(0xFF2C2C2C)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = layout.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (isDefault) {
                        Surface(
                            color = Color(0xFF332A00),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFFFB300))
                        ) {
                            Text(
                                text = "🔒 READ-ONLY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFB300),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF0A2B18),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF69F0AE))
                        ) {
                            Text(
                                text = "CUSTOM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF69F0AE),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                val desc = when (layout.layoutType) {
                    LayoutType.CONSOLE_LEFT_MIXER -> "Mixer on Left (${(layout.horizontalSplitRatio * 100).toInt()}%), Cue Stack on Right"
                    LayoutType.CUE_MASTER_LEFT -> "Cue Stack on Left (${(layout.horizontalSplitRatio * 100).toInt()}%), Mixer on Right"
                    LayoutType.BROADCAST_BOTTOM -> "Cue Stack on Top, Mixer across Bottom (${layout.faderHeightDp}dp)"
                    LayoutType.CLASSIC_THEATRE -> "Portrait Stack: Armed/Now Playing Top, Stack Mid, Faders Bottom (${layout.faderHeightDp}dp)"
                    LayoutType.SPLIT_MOBILE -> "Portrait 2-Column Split: Controls Left (${(layout.horizontalSplitRatio * 100).toInt()}%), Stack Right"
                }
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Surface(
                        color = Color(0xFF00E5FF),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onApply,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Apply", fontSize = 11.sp, color = Color.White)
                    }
                }

                if (!isDefault) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // Overwrite/Update button for custom layout
                    IconButton(
                        onClick = onUpdate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Update Layout with Current Splitters",
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Custom Layout",
                            tint = PanicRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
