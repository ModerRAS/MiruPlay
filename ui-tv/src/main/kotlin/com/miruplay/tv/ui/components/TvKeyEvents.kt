package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayKeyInput
import com.miruplay.tv.design.isActivationIntent
import com.miruplay.tv.design.toMiruPlayInputIntent as toSharedMiruPlayInputIntent

internal fun Key.isTvActivateKey(): Boolean =
    toMiruPlayInputIntent()?.isActivationIntent() == true

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    toMiruPlayKeyInput()?.toSharedMiruPlayInputIntent()

private fun Key.toMiruPlayKeyInput(): MiruPlayKeyInput? =
    when (this) {
        Key.DirectionCenter -> MiruPlayKeyInput.DirectionCenter
        Key.Enter -> MiruPlayKeyInput.Enter
        Key.NumPadEnter -> MiruPlayKeyInput.NumPadEnter
        Key.Spacebar -> MiruPlayKeyInput.Spacebar
        Key.Back -> MiruPlayKeyInput.Back
        Key.Escape -> MiruPlayKeyInput.Escape
        Key.NavigatePrevious -> MiruPlayKeyInput.NavigatePrevious
        Key.NavigateOut -> MiruPlayKeyInput.NavigateOut
        Key.DirectionLeft -> MiruPlayKeyInput.DirectionLeft
        Key.DirectionRight -> MiruPlayKeyInput.DirectionRight
        Key.DirectionUp -> MiruPlayKeyInput.DirectionUp
        Key.DirectionDown -> MiruPlayKeyInput.DirectionDown
        Key.MediaPlayPause -> MiruPlayKeyInput.MediaPlayPause
        Key.MediaPlay -> MiruPlayKeyInput.MediaPlay
        Key.MediaPause -> MiruPlayKeyInput.MediaPause
        Key.MediaStop -> MiruPlayKeyInput.MediaStop
        else -> null
    }
