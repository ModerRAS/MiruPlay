package com.miruplay.tv.ui.components

import androidx.compose.ui.input.key.Key
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.isActivationIntent

internal fun Key.isTvActivateKey(): Boolean =
    toMiruPlayInputIntent()?.isActivationIntent() == true

internal fun Key.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    when (this) {
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        Key.Spacebar,
        -> MiruPlayInputIntent.Activate
        Key.Back -> MiruPlayInputIntent.Back
        Key.DirectionLeft -> MiruPlayInputIntent.DirectionLeft
        Key.DirectionRight -> MiruPlayInputIntent.DirectionRight
        Key.DirectionUp -> MiruPlayInputIntent.DirectionUp
        Key.DirectionDown -> MiruPlayInputIntent.DirectionDown
        Key.MediaPlayPause -> MiruPlayInputIntent.MediaPlayPause
        Key.MediaPlay -> MiruPlayInputIntent.MediaPlay
        Key.MediaPause -> MiruPlayInputIntent.MediaPause
        Key.MediaStop -> MiruPlayInputIntent.MediaStop
        else -> null
    }
