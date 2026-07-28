package com.miruplay.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

internal fun handlePlaybackTimelineKey(
    key: Key,
    type: KeyEventType,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false

    return when (key) {
        Key.DirectionLeft -> {
            onSkipBackward()
            true
        }
        Key.DirectionRight -> {
            onSkipForward()
            true
        }
        else -> false
    }
}
