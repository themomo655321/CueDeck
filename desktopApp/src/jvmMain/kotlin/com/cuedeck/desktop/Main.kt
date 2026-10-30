package com.cuedeck.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cuedeck.audio.DesktopAudioEngine
import com.cuedeck.model.Cue
import com.cuedeck.storage.PlatformStorage
import com.cuedeck.storage.ShowRepository
import com.cuedeck.ui.MainScreen
import com.cuedeck.ui.theme.CueDeckTheme
import com.cuedeck.viewmodel.CueDeckViewModel
import java.io.File
import java.util.UUID
import javax.sound.sampled.AudioSystem
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = application {
    val windowState = rememberWindowState(width = 950.dp, height = 750.dp)

    val storage = PlatformStorage()
    val repository = ShowRepository(storage)
    val audioEngine = DesktopAudioEngine()
    val viewModel = CueDeckViewModel(repository, audioEngine)

    Window(
        onCloseRequest = {
            audioEngine.release()
            exitApplication()
        },
        title = "CueDeck - Live Cue Trigger & Show Control",
        state = windowState
    ) {
        CueDeckTheme {
            MainScreen(
                viewModel = viewModel,
                onImportAudioRequest = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Select Audio File (CueDeck)"
                        fileFilter = FileNameExtensionFilter("Audio Files (*.wav, *.mp3, *.aiff)", "wav", "mp3", "aiff")
                    }
                    val result = chooser.showOpenDialog(null)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        val selectedFile = chooser.selectedFile
                        handleDesktopAudioImport(selectedFile, viewModel, repository)
                    }
                }
            )
        }
    }
}

private fun handleDesktopAudioImport(file: File, viewModel: CueDeckViewModel, repository: ShowRepository) {
    try {
        val showId = viewModel.show.value.id
        val relativePath = repository.importAudioFile(showId, file.absolutePath, file.name)

        var durationMs = 15000L
        try {
            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val format = audioInputStream.format
            val frames = audioInputStream.frameLength
            val durationInSeconds = (frames + 0.0) / format.frameRate
            durationMs = (durationInSeconds * 1000).toLong().coerceAtLeast(1000L)
        } catch (ignored: Exception) {}

        val cueCount = viewModel.show.value.cues.size
        val cueNumber = "${cueCount + 1}.0"
        val cleanTitle = file.nameWithoutExtension

        val cue = Cue(
            id = UUID.randomUUID().toString(),
            cueNumber = cueNumber,
            name = cleanTitle,
            audioRelativePath = relativePath,
            originalFileName = file.name,
            durationMs = durationMs,
            inPointMs = 0L,
            outPointMs = durationMs
        )

        viewModel.addCue(cue)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
