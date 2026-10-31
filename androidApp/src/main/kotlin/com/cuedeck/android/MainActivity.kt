package com.cuedeck.android

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
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
import com.cuedeck.diagnostics.ErrorLogger
import com.cuedeck.model.Cue
import com.cuedeck.service.ShowPlaybackService
import com.cuedeck.storage.PlatformStorage
import com.cuedeck.storage.ShowRepository
import com.cuedeck.ui.MainScreen
import com.cuedeck.ui.theme.CueDeckTheme
import com.cuedeck.viewmodel.CueDeckViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var storage: PlatformStorage
    private lateinit var repository: ShowRepository
    private lateinit var audioEngine: AndroidAudioEngine
    private lateinit var viewModel: CueDeckViewModel

    // In-app Auto Update States
    private val updateInfoState = mutableStateOf<UpdateInfo?>(null)
    private val downloadProgressState = mutableStateOf<Float?>(null)

    // Audio File Import Launcher via Storage Access Framework
    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleImportAudioUriAsync(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash and uncaught exception interceptor
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            ErrorLogger.error("CrashInterceptor", "Uncaught crash on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        storage = PlatformStorage(applicationContext)
        repository = ShowRepository(storage)
        audioEngine = AndroidAudioEngine(applicationContext)
        viewModel = CueDeckViewModel(repository, audioEngine)

        // Ensure playback service stays alive during performance
        ShowPlaybackService.start(this)

        // Check for updates in the background on startup
        checkAppUpdate(silent = true)

        setContent {
            val updateInfo by updateInfoState
            val downloadProgress by downloadProgressState
            val settings by viewModel.settings.collectAsState()

            // Keep screen awake during show if enabled
            LaunchedEffect(settings.keepScreenAwake) {
                if (settings.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

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

    override fun onResume() {
        super.onResume()
        if (viewModel.settings.value.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        super.onPause()
        // Auto-save show & session immediately when focus is lost
        viewModel.saveCurrentShow()
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveCurrentShow()
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

    /**
     * Imports audio from local storage or cloud (Google Drive) asynchronously with progress
     */
    private fun handleImportAudioUriAsync(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var fileName = "imported_audio_${System.currentTimeMillis()}.mp3"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                viewModel.setImportProgress(fileName, 0.01f)

                val showId = viewModel.show.value.id
                // Copy stream to managed show directory with progress callbacks
                val relativePath = repository.importAudioFile(showId, uri.toString(), fileName) { progress ->
                    viewModel.setImportProgress(fileName, progress)
                }

                // Extract audio duration
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(applicationContext, uri)
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

                withContext(Dispatchers.Main) {
                    viewModel.addCue(newCue)
                    viewModel.setImportProgress(null, null)
                    triggerHapticClick()
                }
            } catch (e: Exception) {
                ErrorLogger.error("Import", "Failed to import audio from $uri", e)
                withContext(Dispatchers.Main) {
                    viewModel.setImportProgress(null, null)
                    viewModel.showUserError("Failed to import audio: ${e.message}")
                }
            }
        }
    }

    private fun triggerHapticClick() {
        if (viewModel.settings.value.hapticFeedbackEnabled) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Physical hardware volume keys handling.
     * When volumeButtonsTriggerGo is ON: Vol UP = GO, Vol DOWN = Panic.
     * When OFF: Normal device volume changes.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.settings.value.volumeButtonsTriggerGo) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event?.repeatCount == 0) {
                        triggerHapticClick()
                        viewModel.pressGo()
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (event?.repeatCount == 0) {
                        triggerHapticClick()
                        viewModel.panicStop()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.settings.value.volumeButtonsTriggerGo) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        ShowPlaybackService.stop(this)
    }
}
