package com.miruplay.tv.desktop

import com.miruplay.tv.player.mpv.MpvRuntimeVerifier

internal fun mpvRuntimeStatusFromInputs(
    mpvPath: String,
    configDir: String,
): String =
    MpvRuntimeVerifier.statusFromInputs(mpvPath, configDir)
