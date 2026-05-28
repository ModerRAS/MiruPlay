package com.miruplay.tv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.miruplay.tv.design.MiruPlayComposeKeyProfile
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.isActivationIntent
import com.miruplay.tv.design.miruPlayInputIntentFromComposeKeyCode

internal fun Key.isTvActivateKey(): Boolean =
    isTvActivateIntent(toMiruPlayInputIntent())

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    toAndroidComposeKeyIntent()
        ?: miruPlayInputIntentFromComposeKeyCode(
            keyCode = keyCode,
            profile = MiruPlayComposeKeyProfile.Android,
        )

private fun Key.toAndroidComposeKeyIntent(): MiruPlayInputIntent? =
    when (this) {
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        Key.Spacebar -> MiruPlayInputIntent.Activate
        Key.Back -> MiruPlayInputIntent.Back
        Key.DirectionLeft -> MiruPlayInputIntent.DirectionLeft
        Key.DirectionRight -> MiruPlayInputIntent.DirectionRight
        Key.DirectionUp -> MiruPlayInputIntent.DirectionUp
        Key.DirectionDown -> MiruPlayInputIntent.DirectionDown
        else -> null
    }

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

internal fun Modifier.tvFocusableClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
    .onKeyEvent { event ->
        tvActivateKeyEvent(
            key = event.key,
            type = event.type,
            enabled = enabled,
            onActivate = onClick,
        )
    }
    .focusable(
        enabled = enabled,
        interactionSource = interactionSource,
    )
