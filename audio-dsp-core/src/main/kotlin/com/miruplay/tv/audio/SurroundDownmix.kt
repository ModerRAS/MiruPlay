package com.miruplay.tv.audio

object SurroundDownmix {
    fun standard(source: FloatArray, layout: ChannelLayout): FloatArray {
        if (layout.channelCount <= 2) return source.copyOf(layout.channelCount)
        var left = 0.0
        var right = 0.0
        layout.channels.forEachIndexed { index, channel ->
            val sample = source.getOrElse(index) { 0f }.toDouble()
            when (channel) {
                Channel.L -> left += sample
                Channel.R -> right += sample
                Channel.C -> {
                    left += sample * 0.707
                    right += sample * 0.707
                }
                Channel.LFE -> {
                    left += sample * 0.316
                    right += sample * 0.316
                }
                Channel.LS, Channel.LB -> left += sample * 0.707
                Channel.RS, Channel.RB -> right += sample * 0.707
                else -> Unit
            }
        }
        return floatArrayOf(left.coerceIn(-1.0, 1.0).toFloat(), right.coerceIn(-1.0, 1.0).toFloat())
    }

    fun hrtf(source: FloatArray, layout: ChannelLayout): FloatArray {
        if (layout.channelCount <= 2) return source.copyOf(layout.channelCount)
        var left = 0.0
        var right = 0.0
        layout.channels.forEachIndexed { index, channel ->
            val sample = source.getOrElse(index) { 0f }.toDouble()
            when (channel) {
                Channel.L -> { left += sample; right += sample * 0.08 }
                Channel.R -> { right += sample; left += sample * 0.08 }
                Channel.C -> { left += sample * 0.72; right += sample * 0.72 }
                Channel.LFE -> { left += sample * 0.22; right += sample * 0.22 }
                Channel.LS, Channel.LB -> { left += sample * 0.82; right += sample * 0.24 }
                Channel.RS, Channel.RB -> { right += sample * 0.82; left += sample * 0.24 }
                else -> Unit
            }
        }
        return floatArrayOf(left.coerceIn(-1.0, 1.0).toFloat(), right.coerceIn(-1.0, 1.0).toFloat())
    }
}
