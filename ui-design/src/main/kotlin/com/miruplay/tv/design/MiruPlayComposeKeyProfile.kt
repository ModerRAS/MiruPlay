package com.miruplay.tv.design

enum class MiruPlayComposeKeyProfile {
    Android,
    Desktop,
}

fun miruPlayKeyInputFromComposeKeyCode(
    keyCode: Long,
    profile: MiruPlayComposeKeyProfile,
): MiruPlayKeyInput? =
    when (profile) {
        MiruPlayComposeKeyProfile.Android -> androidComposeKeyInput(keyCode)
        MiruPlayComposeKeyProfile.Desktop -> desktopComposeKeyInput(keyCode)
    }

fun miruPlayInputIntentFromComposeKeyCode(
    keyCode: Long,
    profile: MiruPlayComposeKeyProfile,
    includeDesktopBackAliases: Boolean = false,
): MiruPlayInputIntent? =
    miruPlayKeyInputFromComposeKeyCode(keyCode, profile)
        ?.toMiruPlayInputIntent(includeDesktopBackAliases)

private fun androidComposeKeyInput(keyCode: Long): MiruPlayKeyInput? =
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
        else -> null
    }

private fun desktopComposeKeyInput(keyCode: Long): MiruPlayKeyInput? =
    when (keyCode) {
        desktopKeyCode(10) -> MiruPlayKeyInput.Enter
        desktopKeyCode(10, location = 4) -> MiruPlayKeyInput.NumPadEnter
        desktopKeyCode(32) -> MiruPlayKeyInput.Spacebar
        desktopKeyCode(-1000000014) -> MiruPlayKeyInput.DirectionCenter
        desktopKeyCode(-1000000003) -> MiruPlayKeyInput.Back
        desktopKeyCode(27) -> MiruPlayKeyInput.Escape
        desktopKeyCode(-1000000004) -> MiruPlayKeyInput.NavigatePrevious
        desktopKeyCode(-1000000007) -> MiruPlayKeyInput.NavigateOut
        desktopKeyCode(37) -> MiruPlayKeyInput.DirectionLeft
        desktopKeyCode(39) -> MiruPlayKeyInput.DirectionRight
        desktopKeyCode(38) -> MiruPlayKeyInput.DirectionUp
        desktopKeyCode(40) -> MiruPlayKeyInput.DirectionDown
        desktopKeyCode(-1000000073) -> MiruPlayKeyInput.MediaPlayPause
        desktopKeyCode(-1000000071) -> MiruPlayKeyInput.MediaPlay
        desktopKeyCode(-1000000072) -> MiruPlayKeyInput.MediaPause
        desktopKeyCode(-1000000074) -> MiruPlayKeyInput.MediaStop
        else -> null
    }

private fun desktopKeyCode(
    nativeKeyCode: Int,
    location: Int = 1,
): Long =
    (location.toLong() shl 32) or (nativeKeyCode.toLong() and 0xffffffffL)
