package com.cuedeck.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val fineModeEnabled: Boolean = false,
    val fineTuneRatio: Float = 4.0f, // 2x, 4x, or 8x slower
    val volumeButtonsTriggerGo: Boolean = false, // When ON: Vol UP = GO, Vol DOWN = Panic
    val keepScreenAwake: Boolean = true, // FLAG_KEEP_SCREEN_ON during shows
    val hapticFeedbackEnabled: Boolean = true, // Tactile click on GO/Panic/Triggers
    val panicFadeMs: Long = 0L,
    val faderSmoothingMs: Long = 100L
)
