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
    MediaRewind,
    MediaFastForward,
    MediaPrevious,
    MediaNext,
    Captions,
    Menu,
    Info,
}

enum class MiruPlayPlaybackInputAction {
    Launch,
    TogglePause,
    Resume,
    Pause,
    SeekBack,
    SeekForward,
    Stop,
    PreviousEpisode,
    NextEpisode,
    OpenCaptions,
    FocusOptions,
    ToggleInfo,
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

fun MiruPlayInputIntent.isDedicatedPlayerRemoteIntent(): Boolean =
    when (this) {
        MiruPlayInputIntent.MediaRewind,
        MiruPlayInputIntent.MediaFastForward,
        MiruPlayInputIntent.MediaPrevious,
        MiruPlayInputIntent.MediaNext,
        MiruPlayInputIntent.MediaStop,
        MiruPlayInputIntent.Captions,
        MiruPlayInputIntent.Menu,
        MiruPlayInputIntent.Info,
        -> true
        else -> false
    }

fun MiruPlayInputIntent.allowsPlayerRemoteRepeat(): Boolean =
    this == MiruPlayInputIntent.MediaRewind ||
        this == MiruPlayInputIntent.MediaFastForward

fun MiruPlayInputIntent.tvPlaybackOverlayAction(
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
): MiruPlayPlaybackInputAction? =
    when {
        this == MiruPlayInputIntent.Back && hasOpenMenu -> MiruPlayPlaybackInputAction.CloseMenu
        hasOpenMenu && (
            this == MiruPlayInputIntent.DirectionLeft ||
                this == MiruPlayInputIntent.DirectionRight ||
                this == MiruPlayInputIntent.DirectionUp ||
                this == MiruPlayInputIntent.DirectionDown
            ) -> null
        this == MiruPlayInputIntent.MediaRewind -> MiruPlayPlaybackInputAction.SeekBack
        this == MiruPlayInputIntent.MediaFastForward -> MiruPlayPlaybackInputAction.SeekForward
        this == MiruPlayInputIntent.MediaPrevious -> MiruPlayPlaybackInputAction.PreviousEpisode
        this == MiruPlayInputIntent.MediaNext -> MiruPlayPlaybackInputAction.NextEpisode
        this == MiruPlayInputIntent.MediaStop -> MiruPlayPlaybackInputAction.Stop
        this == MiruPlayInputIntent.Captions -> MiruPlayPlaybackInputAction.OpenCaptions
        this == MiruPlayInputIntent.Menu -> MiruPlayPlaybackInputAction.FocusOptions
        this == MiruPlayInputIntent.Info -> MiruPlayPlaybackInputAction.ToggleInfo
        else -> tvPlaybackOverlayActionForDpadAndTransport(
            controlsVisible = controlsVisible,
            hasOpenMenu = hasOpenMenu,
        )
    }

private fun MiruPlayInputIntent.tvPlaybackOverlayActionForDpadAndTransport(
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
): MiruPlayPlaybackInputAction? =
    if (controlsVisible) {
        when (this) {
            MiruPlayInputIntent.DirectionLeft,
            MiruPlayInputIntent.DirectionRight,
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
        MiruPlayPlaybackInputAction.PreviousEpisode,
        MiruPlayPlaybackInputAction.NextEpisode,
        MiruPlayPlaybackInputAction.OpenCaptions,
        MiruPlayPlaybackInputAction.FocusOptions,
        -> true
        MiruPlayPlaybackInputAction.TogglePause,
        MiruPlayPlaybackInputAction.Resume,
        MiruPlayPlaybackInputAction.Pause,
        -> !controlsVisible
        else -> false
    }
