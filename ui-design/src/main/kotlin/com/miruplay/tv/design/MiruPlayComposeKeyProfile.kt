package com.miruplay.tv.design

fun miruPlayKeyInputFromComposeKeyCode(keyCode: Long): MiruPlayKeyInput? =
    when (keyCode) {
        23L -> MiruPlayKeyInput.DirectionCenter
        66L -> MiruPlayKeyInput.Enter
        160L -> MiruPlayKeyInput.NumPadEnter
        62L -> MiruPlayKeyInput.Spacebar
        4L -> MiruPlayKeyInput.Back
        111L -> MiruPlayKeyInput.Escape
        260L -> MiruPlayKeyInput.NavigatePrevious
        263L -> MiruPlayKeyInput.NavigateOut
        21L -> MiruPlayKeyInput.DirectionLeft
        22L -> MiruPlayKeyInput.DirectionRight
        19L -> MiruPlayKeyInput.DirectionUp
        20L -> MiruPlayKeyInput.DirectionDown
        85L -> MiruPlayKeyInput.MediaPlayPause
        126L -> MiruPlayKeyInput.MediaPlay
        127L -> MiruPlayKeyInput.MediaPause
        86L -> MiruPlayKeyInput.MediaStop
        89L -> MiruPlayKeyInput.MediaRewind
        90L -> MiruPlayKeyInput.MediaFastForward
        88L -> MiruPlayKeyInput.MediaPrevious
        87L -> MiruPlayKeyInput.MediaNext
        175L -> MiruPlayKeyInput.Captions
        82L -> MiruPlayKeyInput.Menu
        165L -> MiruPlayKeyInput.Info
        else -> null
    }

fun miruPlayInputIntentFromComposeKeyCode(keyCode: Long): MiruPlayInputIntent? =
    miruPlayKeyInputFromComposeKeyCode(keyCode)?.toMiruPlayInputIntent()
