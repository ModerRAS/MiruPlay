@file:Suppress("DEPRECATION", "UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup

internal class SubtitleTransformingPlayer(
    delegate: Player,
    private val transform: (List<Cue>) -> List<Cue> = ::restackSubtitleCues,
) : ForwardingPlayer(delegate) {

    override fun getCurrentCues(): CueGroup = super.getCurrentCues().transformed()

    override fun addListener(listener: Player.Listener) {
        super.addListener(TransformingListener(listener, transform))
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(TransformingListener(listener, transform))
    }
}

private fun CueGroup.transformed(transform: (List<Cue>) -> List<Cue> = ::restackSubtitleCues): CueGroup =
    CueGroup(transform(cues), presentationTimeUs)

private class TransformingListener(
    private val listener: Player.Listener,
    private val transform: (List<Cue>) -> List<Cue>,
) : Player.Listener by listener {

    @Deprecated("Deprecated in Media3")
    override fun onCues(cues: List<Cue>) {
        listener.onCues(transform(cues))
    }

    override fun onCues(cueGroup: CueGroup) {
        listener.onCues(cueGroup.transformed(transform))
    }

    override fun equals(other: Any?): Boolean =
        other is TransformingListener && listener == other.listener

    override fun hashCode(): Int = listener.hashCode()
}
