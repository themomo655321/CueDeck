package com.cuedeck.model

import kotlinx.serialization.Serializable

/**
 * How a cue behaves when triggered.
 */
@Serializable
enum class TriggerMode {
    /** Plays from in-point to out-point once and stops. */
    PLAY_ONCE,

    /** Loops continuously between in-point and out-point until manually stopped or faded. */
    LOOP,

    /** If tapped while already playing, restarts playback from the in-point. */
    RETRIGGER,

    /** Plays while pad is held down, stops immediately on release (Soundboard only). */
    GATE
}

/**
 * Sequential QLab-style follow action for the cue list.
 */
@Serializable
enum class FollowAction {
    /** Stop and wait for the operator to press GO for the next cue. */
    WAIT_GO,

    /** Automatically fire the next cue immediately when this cue finishes playing. */
    AUTO_FOLLOW,

    /** Fire the next cue simultaneously with this cue (with optional pre-wait offset). */
    AUTO_CONTINUE
}

/**
 * The primary UI view mode.
 */
@Serializable
enum class ViewMode {
    /** Sequential cue stack with large GO button (QLab style). */
    CUE_LIST,

    /** Color-coded matrix/grid of instant soundboard trigger pads (SoundShow style). */
    CART_MATRIX
}
