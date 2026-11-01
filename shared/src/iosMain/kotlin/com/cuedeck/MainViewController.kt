package com.cuedeck

import androidx.compose.ui.window.ComposeUIViewController
import com.cuedeck.audio.IosAudioEngine
import com.cuedeck.storage.PlatformStorage
import com.cuedeck.storage.ShowRepository
import com.cuedeck.ui.MainScreen
import com.cuedeck.viewmodel.CueDeckViewModel
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val storage = PlatformStorage()
    val repository = ShowRepository(storage)
    val audioEngine = IosAudioEngine()
    val viewModel = CueDeckViewModel(repository, audioEngine)

    val hapticGenerator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    hapticGenerator.prepare()

    MainScreen(
        viewModel = viewModel,
        onHapticFeedback = {
            hapticGenerator.impactOccurred()
        }
    )
}
