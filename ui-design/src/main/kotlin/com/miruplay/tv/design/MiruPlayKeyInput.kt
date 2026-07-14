package com.miruplay.tv.design

enum class MiruPlayKeyInput {
    DirectionCenter,
    Enter,
    NumPadEnter,
    Spacebar,
    Back,
    Escape,
    NavigatePrevious,
    NavigateOut,
    DirectionLeft,
    DirectionRight,
    DirectionUp,
    DirectionDown,
    MediaPlayPause,
    MediaPlay,
    MediaPause,
    MediaStop,
}

fun MiruPlayKeyInput.toMiruPlayInputIntent(): MiruPlayInputIntent? =
    when (this) {
        MiruPlayKeyInput.DirectionCenter,
        MiruPlayKeyInput.Enter,
        MiruPlayKeyInput.NumPadEnter,
        MiruPlayKeyInput.Spacebar,
        -> MiruPlayInputIntent.Activate
        MiruPlayKeyInput.Back -> MiruPlayInputIntent.Back
        MiruPlayKeyInput.Escape,
        MiruPlayKeyInput.NavigatePrevious,
        MiruPlayKeyInput.NavigateOut,
        -> null
        MiruPlayKeyInput.DirectionLeft -> MiruPlayInputIntent.DirectionLeft
        MiruPlayKeyInput.DirectionRight -> MiruPlayInputIntent.DirectionRight
        MiruPlayKeyInput.DirectionUp -> MiruPlayInputIntent.DirectionUp
        MiruPlayKeyInput.DirectionDown -> MiruPlayInputIntent.DirectionDown
        MiruPlayKeyInput.MediaPlayPause -> MiruPlayInputIntent.MediaPlayPause
        MiruPlayKeyInput.MediaPlay -> MiruPlayInputIntent.MediaPlay
        MiruPlayKeyInput.MediaPause -> MiruPlayInputIntent.MediaPause
        MiruPlayKeyInput.MediaStop -> MiruPlayInputIntent.MediaStop
    }

fun MiruPlayKeyInput.isMiruPlayActivationKey(): Boolean =
    toMiruPlayInputIntent()?.isActivationIntent() == true
