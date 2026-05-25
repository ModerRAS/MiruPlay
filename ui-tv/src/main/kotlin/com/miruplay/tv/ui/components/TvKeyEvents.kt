package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.miruplay.tv.design.MiruPlayComposeKeyProfile
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.isActivationIntent
import com.miruplay.tv.design.miruPlayInputIntentFromComposeKeyCode

internal fun Key.isTvActivateKey(): Boolean =
    isTvActivateIntent(toMiruPlayInputIntent())

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    miruPlayInputIntentFromComposeKeyCode(
        keyCode = keyCode,
        profile = MiruPlayComposeKeyProfile.Android,
    )

internal fun isTvActivateIntent(intent: MiruPlayInputIntent?): Boolean =
    intent?.isActivationIntent() == true

internal inline fun tvActivateKeyEvent(
    key: Key,
    type: KeyEventType,
    enabled: Boolean = true,
    onActivate: () -> Unit,
): Boolean =
    tvActivateIntentEvent(
        intent = key.toMiruPlayInputIntent(),
        type = type,
        enabled = enabled,
        onActivate = onActivate,
    )

internal inline fun tvActivateIntentEvent(
    intent: MiruPlayInputIntent?,
    type: KeyEventType,
    enabled: Boolean = true,
    onActivate: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (!enabled) return false
    if (!isTvActivateIntent(intent)) return false
    onActivate()
    return true
}
