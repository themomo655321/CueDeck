package com.cuedeck

import androidx.compose.runtime.remember
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
    val storage = remember { PlatformStorage() }
    val repository = remember { ShowRepository(storage) }
    val audioEngine = remember { IosAudioEngine() }
    val viewModel = remember { CueDeckViewModel(repository, audioEngine) }

    val hapticGenerator = remember {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply {
            prepare()
        }
    }

    MainScreen(
        viewModel = viewModel,
        onHapticFeedback = {
            hapticGenerator.impactOccurred()
        }
    )
}

fun mainViewController(): UIViewController = MainViewController()
