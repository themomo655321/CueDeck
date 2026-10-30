package com.cuedeck.android

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.cuedeck.audio.AndroidAudioEngine
import com.cuedeck.model.Cue
import com.cuedeck.service.ShowPlaybackService
import com.cuedeck.storage.PlatformStorage
import com.cuedeck.storage.ShowRepository
import com.cuedeck.ui.MainScreen
import com.cuedeck.ui.theme.CueDeckTheme
import com.cuedeck.viewmodel.CueDeckViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var storage: PlatformStorage
    private lateinit var repository: ShowRepository
    private lateinit var audioEngine: AndroidAudioEngine
    private lateinit var viewModel: CueDeckViewModel

    // In-app Auto Update States
    private val updateInfoState = mutableStateOf<UpdateInfo?>(null)
    private val downloadProgressState = mutableStateOf<Float?>(null)

    // Rekordbox-style Audio File Import Launcher via Storage Access Framework
    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleImportAudioUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = PlatformStorage(applicationContext)
        repository = ShowRepository(storage)
        audioEngine = AndroidAudioEngine(applicationContext)
        viewModel = CueDeckViewModel(repository, audioEngine)

        // Ensure playback service stays alive during a performance
        ShowPlaybackService.start(this)

        // Check for updates in the background on startup
        checkAppUpdate(silent = true)

        setContent {
            val updateInfo by updateInfoState
            val downloadProgress by downloadProgressState

            CueDeckTheme {
                MainScreen(
                    viewModel = viewModel,
                    onImportAudioRequest = { audioPickerLauncher.launch("audio/*") },
                    onCheckForUpdates = { checkAppUpdate(silent = false) }
                )

                // In-App Auto Update Dialog
                if (updateInfo != null) {
                    AlertDialog(
                        onDismissRequest = {
                            if (downloadProgress == null) updateInfoState.value = null
                        },
                        title = { Text("CueDeck Update Available 🚀") },
                        text = {
                            Column {
                                Text(
                                    text = "A new build of CueDeck has been published to GitHub.",
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (downloadProgress != null) {
                                    val pct = (downloadProgress!! * 100).toInt()
                                    Text("Downloading update: $pct%", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress!! },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    val sizeMb = updateInfo!!.sizeBytes / (1024 * 1024f)
                                    Text("Download size: %.1f MB".format(sizeMb), fontSize = 12.sp)
                                }
                            }
                        },
                        confirmButton = {
                            if (downloadProgress == null) {
                                Button(
                                    onClick = {
                                        lifecycleScope.launch {
                                            downloadProgressState.value = 0.01f
                                            UpdateManager.downloadAndInstall(
                                                context = applicationContext,
                                                updateInfo = updateInfo!!,
                                                onProgress = { downloadProgressState.value = it }
                                            )
                                            updateInfoState.value = null
                                            downloadProgressState.value = null
                                        }
                                    }
                                ) {
                                    Text("Update Now")
                                }
                            }
                        },
                        dismissButton = {
                            if (downloadProgress == null) {
                                TextButton(onClick = { updateInfoState.value = null }) {
                                    Text("Later")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAppUpdate(silent: Boolean) {
        lifecycleScope.launch {
            val info = UpdateManager.checkForUpdate(applicationContext)
            if (info != null) {
                updateInfoState.value = info
            } else if (!silent) {
                Toast.makeText(applicationContext, "CueDeck is up to date!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleImportAudioUri(uri: Uri) {
        try {
            var fileName = "imported_audio_${System.currentTimeMillis()}.mp3"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val showId = viewModel.show.value.id
            // Copy file to managed internal show storage (with deduplication)
            val relativePath = repository.importAudioFile(showId, uri.toString(), fileName)

            // Extract duration
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 10000L
            retriever.release()

            val cueCount = viewModel.show.value.cues.size
            val cueNumber = "${cueCount + 1}.0"
            val cleanTitle = fileName.substringBeforeLast(".")

            val newCue = Cue(
                id = UUID.randomUUID().toString(),
                cueNumber = cueNumber,
                name = cleanTitle,
                audioRelativePath = relativePath,
                originalFileName = fileName,
                durationMs = durationMs,
                inPointMs = 0L,
                outPointMs = durationMs
            )

            viewModel.addCue(newCue)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Intercept physical hardware volume keys for live stage triggering.
     * Prevents Android from changing volume or beeping during a show.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.pressGo()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.panicStop()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        ShowPlaybackService.stop(this)
    }
}
