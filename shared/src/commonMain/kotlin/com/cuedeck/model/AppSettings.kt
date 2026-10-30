package com.cuedeck.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val faderStreamLinePercent: Int = 65, // 0 = Direct, 35 = Light, 65 = Medium/Procreate, 85 = Heavy
    val faderSmoothingMs: Long = 100L,
    val panicFadeMs: Long = 0L
)
