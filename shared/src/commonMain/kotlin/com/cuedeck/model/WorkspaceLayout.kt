package com.cuedeck.model

import kotlinx.serialization.Serializable

@Serializable
enum class LayoutType {
    CONSOLE_LEFT_MIXER,  // Landscape default: Mixer on Left, Cue Stack + Controls on Right
    CUE_MASTER_LEFT,     // Reversed: Cue Stack on Left, Mixer on Right
    BROADCAST_BOTTOM,    // Stacked: Cue Stack + Now Playing on Top, Mixer across Bottom
    CLASSIC_THEATRE,     // Portrait default: Armed + Now Playing Top, Cue Stack Mid, Faders Bottom, GO Bottom
    SPLIT_MOBILE         // Portrait 2 columns (Left: Armed/Now Playing, Right: Stack, Bottom: Faders/GO)
}

@Serializable
data class WorkspaceLayout(
    val id: String,
    val name: String,
    val isDefault: Boolean = false, // Protected factory preset! Cannot be overwritten or deleted
    val isLandscape: Boolean = true,
    val layoutType: LayoutType = LayoutType.CONSOLE_LEFT_MIXER,
    val horizontalSplitRatio: Float = 0.45f, // Left col vs Right col (0.20f .. 0.80f)
    val faderHeightDp: Int = 185             // Height for bottom mixer (120 .. 350)
)

val DEFAULT_LANDSCAPE_LAYOUTS = listOf(
    WorkspaceLayout(
        id = "def_landscape_console",
        name = "Studio Console",
        isDefault = true,
        isLandscape = true,
        layoutType = LayoutType.CONSOLE_LEFT_MIXER,
        horizontalSplitRatio = 0.45f
    ),
    WorkspaceLayout(
        id = "def_landscape_cue_master",
        name = "Cue Master (Reversed)",
        isDefault = true,
        isLandscape = true,
        layoutType = LayoutType.CUE_MASTER_LEFT,
        horizontalSplitRatio = 0.55f
    ),
    WorkspaceLayout(
        id = "def_landscape_broadcast",
        name = "Broadcast Deck",
        isDefault = true,
        isLandscape = true,
        layoutType = LayoutType.BROADCAST_BOTTOM,
        horizontalSplitRatio = 0.50f,
        faderHeightDp = 200
    )
)

val DEFAULT_PORTRAIT_LAYOUTS = listOf(
    WorkspaceLayout(
        id = "def_portrait_classic",
        name = "Classic Theatre",
        isDefault = true,
        isLandscape = false,
        layoutType = LayoutType.CLASSIC_THEATRE,
        faderHeightDp = 185
    ),
    WorkspaceLayout(
        id = "def_portrait_split",
        name = "Split Screen Mobile",
        isDefault = true,
        isLandscape = false,
        layoutType = LayoutType.SPLIT_MOBILE,
        horizontalSplitRatio = 0.48f,
        faderHeightDp = 185
    )
)

@Serializable
data class WorkspaceLayoutState(
    val activeLandscapeId: String = "def_landscape_console",
    val activePortraitId: String = "def_portrait_classic",
    val customLayouts: List<WorkspaceLayout> = emptyList(),
    // Current runtime split adjustments
    val currentLandscapeRatio: Float = 0.45f,
    val currentPortraitRatio: Float = 0.48f,
    val currentFaderHeightDp: Int = 185
)
