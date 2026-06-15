package com.miruplay.tv.player

enum class PlaybackDecoderPreference {
    DEFAULT,
    PREFER_SOFTWARE_HEVC_FOR_HDR,
    PREFER_SOFTWARE_VIDEO_FOR_HDR,
}

object PlaybackCodecSelectionState {
    @Volatile
    var decoderPreference: PlaybackDecoderPreference = PlaybackDecoderPreference.DEFAULT
}
