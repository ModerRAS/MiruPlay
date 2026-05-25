package com.miruplay.tv.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayComposeKeyProfile
import com.miruplay.tv.design.isActivationIntent
import com.miruplay.tv.design.miruPlayInputIntentFromComposeKeyCode

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    miruPlayInputIntentFromComposeKeyCode(
        keyCode = keyCode,
        profile = MiruPlayComposeKeyProfile.Desktop,
        includeDesktopBackAliases = true,
    )

internal fun isDesktopConfirmKey(key: Key): Boolean =
    key.toMiruPlayInputIntent()?.isActivationIntent() == true

internal fun desktopConfirmOrNavigationKeyEvent(
    key: Key,
    type: KeyEventType,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    val intent = key.toMiruPlayInputIntent()
    if (intent?.isActivationIntent() != true) return onNavigationKey(key)
    if (!enabled) return false

    onClick()
    return true
}

internal fun desktopConfirmOrNavigationIntentEvent(
    key: Key,
    type: KeyEventType,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    val intent = key.toMiruPlayInputIntent() ?: return false
    if (!intent.isActivationIntent()) return onNavigationIntent(intent)
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

internal fun desktopNavigationKeyEvent(
    key: Key,
    type: KeyEventType,
    onNavigationKey: (Key) -> Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown || isDesktopConfirmKey(key)) return false
    return onNavigationKey(key)
}

internal fun desktopNavigationIntentEvent(
    key: Key,
    type: KeyEventType,
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    val intent = key.toMiruPlayInputIntent() ?: return false
    if (intent.isActivationIntent()) return false
    return onNavigationIntent(intent)
}

internal fun Modifier.desktopNavigationKeyHandler(
    onNavigationKey: (Key) -> Boolean,
): Modifier =
    onPreviewKeyEvent { event ->
        desktopNavigationKeyEvent(
            key = event.key,
            type = event.type,
            onNavigationKey = onNavigationKey,
        )
    }

internal fun Modifier.desktopNavigationIntentHandler(
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean,
): Modifier =
    onPreviewKeyEvent { event ->
        desktopNavigationIntentEvent(
            key = event.key,
            type = event.type,
            onNavigationIntent = onNavigationIntent,
        )
    }
