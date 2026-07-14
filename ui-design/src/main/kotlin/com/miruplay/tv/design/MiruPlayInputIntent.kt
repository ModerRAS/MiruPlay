package com.miruplay.tv.design

enum class MiruPlayInputIntent {
    Activate,
    Back,
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

enum class MiruPlayPlaybackInputAction {
    Launch,
    TogglePause,
    Resume,
    Pause,
    SeekBack,
    SeekForward,
    Stop,
    ShowControls,
    HideControls,
    CloseMenu,
    NavigateBack,
}

fun MiruPlayInputIntent.isActivationIntent(): Boolean =
    this == MiruPlayInputIntent.Activate

fun MiruPlayInputIntent.isBackIntent(): Boolean =
    this == MiruPlayInputIntent.Back ||
        this == MiruPlayInputIntent.NavigatePrevious ||
        this == MiruPlayInputIntent.NavigateOut

fun MiruPlayInputIntent.isPlaybackToggleIntent(): Boolean =
    this == MiruPlayInputIntent.Activate ||
        this == MiruPlayInputIntent.MediaPlayPause

fun MiruPlayInputIntent.horizontalNavigationDelta(): Int? =
    when (this) {
        MiruPlayInputIntent.DirectionLeft -> -1
        MiruPlayInputIntent.DirectionRight -> 1
        else -> null
    }

fun MiruPlayInputIntent.verticalNavigationDelta(): Int? =
    when (this) {
        MiruPlayInputIntent.DirectionUp -> -1
        MiruPlayInputIntent.DirectionDown -> 1
        else -> null
    }

fun MiruPlayInputIntent.linearNavigationDelta(): Int? =
    horizontalNavigationDelta() ?: verticalNavigationDelta()

fun MiruPlayInputIntent.tvPlaybackOverlayAction(
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
): MiruPlayPlaybackInputAction? =
    if (controlsVisible) {
        when (this) {
            MiruPlayInputIntent.DirectionLeft -> MiruPlayPlaybackInputAction.SeekBack.takeUnless { hasOpenMenu }
            MiruPlayInputIntent.DirectionRight -> MiruPlayPlaybackInputAction.SeekForward.takeUnless { hasOpenMenu }
            MiruPlayInputIntent.DirectionUp,
            MiruPlayInputIntent.DirectionDown,
            -> null
            MiruPlayInputIntent.MediaPlayPause -> MiruPlayPlaybackInputAction.TogglePause
            MiruPlayInputIntent.MediaPlay -> MiruPlayPlaybackInputAction.Resume
            MiruPlayInputIntent.MediaPause -> MiruPlayPlaybackInputAction.Pause
            MiruPlayInputIntent.Back -> if (hasOpenMenu) {
                MiruPlayPlaybackInputAction.CloseMenu
            } else {
                MiruPlayPlaybackInputAction.HideControls
            }
            else -> null
        }
    } else {
        when (this) {
            MiruPlayInputIntent.DirectionLeft -> MiruPlayPlaybackInputAction.SeekBack
            MiruPlayInputIntent.DirectionRight -> MiruPlayPlaybackInputAction.SeekForward
            MiruPlayInputIntent.DirectionUp,
            MiruPlayInputIntent.DirectionDown,
            -> MiruPlayPlaybackInputAction.ShowControls
            MiruPlayInputIntent.MediaPlay -> MiruPlayPlaybackInputAction.Resume
            MiruPlayInputIntent.MediaPause -> MiruPlayPlaybackInputAction.Pause
            MiruPlayInputIntent.Back -> MiruPlayPlaybackInputAction.NavigateBack
            else -> if (isPlaybackToggleIntent()) {
                MiruPlayPlaybackInputAction.TogglePause
            } else {
                null
            }
        }
    }

fun MiruPlayPlaybackInputAction.shouldRefreshTvPlaybackControls(controlsVisible: Boolean): Boolean =
    when (this) {
        MiruPlayPlaybackInputAction.SeekBack,
        MiruPlayPlaybackInputAction.SeekForward,
        MiruPlayPlaybackInputAction.ShowControls,
        -> true
        MiruPlayPlaybackInputAction.TogglePause,
        MiruPlayPlaybackInputAction.Resume,
        MiruPlayPlaybackInputAction.Pause,
        -> !controlsVisible
        else -> false
    }
