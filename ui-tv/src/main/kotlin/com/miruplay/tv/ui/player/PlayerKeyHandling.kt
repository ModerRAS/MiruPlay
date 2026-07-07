package com.miruplay.tv.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

internal data class PlayerKeyActions(
    val skipBackward: () -> Unit,
    val skipForward: () -> Unit,
    val togglePlayback: () -> Unit,
    val resume: () -> Unit,
    val pause: () -> Unit,
    val showControls: () -> Unit,
    val hideControls: () -> Unit,
    val closeMenu: () -> Unit,
    val navigateBack: () -> Unit
)

internal fun handlePlayerKey(
    key: Key,
    type: KeyEventType,
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
    actions: PlayerKeyActions
): Boolean {
    if (type != KeyEventType.KeyDown) return false

    if (controlsVisible) {
        return when (key) {
            Key.DirectionLeft -> if (hasOpenMenu) {
                false
            } else {
                actions.showControls()
                actions.skipBackward()
                true
            }
            Key.DirectionRight -> if (hasOpenMenu) {
                false
            } else {
                actions.showControls()
                actions.skipForward()
                true
            }
            Key.MediaPlayPause -> {
                actions.togglePlayback()
                true
            }
            Key.MediaPlay -> {
                actions.resume()
                true
            }
            Key.MediaPause -> {
                actions.pause()
                true
            }
            Key.Back -> {
                if (hasOpenMenu) {
                    actions.closeMenu()
                } else {
                    actions.hideControls()
                }
                true
            }
            else -> false
        }
    }

    return when (key) {
        Key.DirectionLeft -> {
            actions.showControls()
            actions.skipBackward()
            true
        }
        Key.DirectionRight -> {
            actions.showControls()
            actions.skipForward()
            true
        }
        Key.DirectionUp,
        Key.DirectionDown -> {
            actions.showControls()
            true
        }
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        Key.Spacebar,
        Key.MediaPlayPause -> {
            actions.showControls()
            actions.togglePlayback()
            true
        }
        Key.MediaPlay -> {
            actions.showControls()
            actions.resume()
            true
        }
        Key.MediaPause -> {
            actions.showControls()
            actions.pause()
            true
        }
        Key.Back -> {
            actions.navigateBack()
            true
        }
        else -> false
    }
}
