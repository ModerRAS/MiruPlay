@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.os.Looper
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

internal fun addLibassTextRenderers(
    session: LibassSubtitleSession,
    output: TextOutput,
    outputLooper: Looper,
    out: ArrayList<Renderer>,
) {
    out += NativeAssTextRenderer(session)
    out += TextRenderer(output, outputLooper).apply {
        experimentalSetLegacyDecodingEnabled(true)
    }
}
