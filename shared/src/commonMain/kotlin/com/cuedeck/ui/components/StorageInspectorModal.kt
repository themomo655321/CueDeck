package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.storage.StorageInspection
import com.cuedeck.ui.theme.DarkSurface
import com.cuedeck.ui.theme.PanicRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageInspectorModal(
    inspection: StorageInspection?,
    onPurgeUnused: () -> Unit,
    onOpenInSystem: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROJECT AUDIO STORAGE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (inspection == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            } else {
                // Folder Path & Size Card
                Surface(
                    color = Color(0xFF181818),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "STORAGE DIRECTORY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Text(
                            text = inspection.directoryPath,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        val totalMb = inspection.totalSizeBytes / (1024f * 1024f)
                        Text(
                            text = "TOTAL CONSUMED: %.2f MB (%d audio files)".format(totalMb, inspection.files.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalMb > 300f) Color(0xFFFFB300) else Color(0xFF4CAF50)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenInSystem,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open in Files App", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = onPurgeUnused,
                        colors = ButtonDefaults.buttonColors(containerColor = PanicRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Purge Unused", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "FILES IN PROJECT (${inspection.files.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(6.dp))

                // File List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(inspection.files) { file ->
                        val sizeMb = file.sizeBytes / (1024f * 1024f)
                        Surface(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.fileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "%.2f MB".format(sizeMb),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (file.isActiveInShow) Color(0xFF1B5E20) else Color(0xFF424242))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (file.isActiveInShow) "IN USE" else "UNUSED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (file.isActiveInShow) Color(0xFF81C784) else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
