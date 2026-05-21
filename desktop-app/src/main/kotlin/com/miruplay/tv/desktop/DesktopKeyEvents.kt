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

internal fun desktopToggleKeyEvent(
    key: Key,
    type: KeyEventType,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
): Boolean =
    desktopConfirmOrNavigationKeyEvent(
        key = key,
        type = type,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
    )

internal fun desktopOpenPickerKeyEvent(
    key: Key,
    type: KeyEventType,
    enabled: Boolean = true,
    onOpen: () -> Unit,
): Boolean =
    desktopConfirmOrNavigationKeyEvent(
        key = key,
        type = type,
        enabled = enabled,
        onClick = onOpen,
    )
