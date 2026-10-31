package com.cuedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuedeck.ui.theme.DarkSurface

@Composable
fun ImportAudioDialog(
    onImportLocalFile: () -> Unit,
    onImportUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showUrlInput by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (showUrlInput) "IMPORT FROM LINK" else "ADD AUDIO CUE",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color.White
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!showUrlInput) {
                    Text(
                        text = "Choose audio source to import into the cue stack:",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Option 1: Local Device Files
                    Button(
                        onClick = {
                            onDismiss()
                            onImportLocalFile()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Device Audio Files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Web / YouTube / Freesound
                    Button(
                        onClick = { showUrlInput = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C2C2C),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import from Web / YouTube / Freesound", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Text(
                        text = "Paste any YouTube, YouTube Music, Freesound.org, or direct audio link (.mp3, .wav):",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://youtube.com/..., freesound.org/...", fontSize = 12.sp, color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showUrlInput = false }) {
                            Text("Back", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputUrl.isNotBlank()) {
                                    onImportUrl(inputUrl.trim())
                                    onDismiss()
                                }
                            },
                            enabled = inputUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Download & Add", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
