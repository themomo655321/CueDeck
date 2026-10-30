package com.cuedeck.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val faderSmoothingMs: Long = 150L,
    val panicFadeMs: Long = 0L
)
