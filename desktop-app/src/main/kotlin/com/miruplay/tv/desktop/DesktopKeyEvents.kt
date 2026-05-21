package com.miruplay.tv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

internal fun isDesktopConfirmKey(key: Key): Boolean =
    key == Key.Enter ||
        key == Key.NumPadEnter ||
        key == Key.DirectionCenter

internal fun desktopConfirmOrNavigationKeyEvent(
    key: Key,
    type: KeyEventType,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (!isDesktopConfirmKey(key)) return onNavigationKey(key)
    if (!enabled) return false

    onClick()
    return true
}
