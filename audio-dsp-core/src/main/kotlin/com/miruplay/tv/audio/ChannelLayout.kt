package com.miruplay.tv.audio

enum class Channel { L, R, C, LFE, LS, RS, LB, RB, MONO, UNKNOWN }

enum class ChannelLayoutId { MONO, STEREO, SURROUND_5_1, SURROUND_7_1, UNKNOWN }

enum class InputOrder { CANONICAL, AAC_5_1, WAV_5_1, UNKNOWN }

data class ChannelLayout(
    val id: ChannelLayoutId,
    val channels: List<Channel>,
    val defaultInputOrder: InputOrder = InputOrder.UNKNOWN,
) {
    val channelCount: Int get() = channels.size

    fun normalizeInterleaved(samples: FloatArray, inputOrder: InputOrder): FloatArray {
        if (inputOrder == InputOrder.CANONICAL || channelCount < 6) return samples.copyOf()
        val frames = samples.size / channelCount
        if (frames * channelCount != samples.size) return samples.copyOf()
        val order = when (inputOrder) {
            InputOrder.AAC_5_1 -> intArrayOf(0, 1, 2, 5, 3, 4)
            InputOrder.WAV_5_1 -> intArrayOf(0, 1, 2, 3, 4, 5)
            else -> return samples.copyOf()
        }
        return FloatArray(samples.size) { index ->
            val frame = index / channelCount
            val outputChannel = index % channelCount
            samples[frame * channelCount + order[outputChannel]]
        }
    }

    companion object {
        const val ANDROID_5_1_MASK = 4 or 8 or 16 or 32 or 64 or 128
        const val ANDROID_7_1_MASK = ANDROID_5_1_MASK or 2_048 or 4_096

        fun from(channelCount: Int, channelMask: Int?): ChannelLayout {
            val known = when (channelMask) {
                ANDROID_5_1_MASK -> ChannelLayoutId.SURROUND_5_1
                ANDROID_7_1_MASK -> ChannelLayoutId.SURROUND_7_1
                else -> when (channelCount) {
                    1 -> ChannelLayoutId.MONO
                    2 -> ChannelLayoutId.STEREO
                    6 -> ChannelLayoutId.SURROUND_5_1
                    8 -> ChannelLayoutId.SURROUND_7_1
                    else -> ChannelLayoutId.UNKNOWN
                }
            }
            val channels = when (known) {
                ChannelLayoutId.MONO -> listOf(Channel.MONO)
                ChannelLayoutId.STEREO -> listOf(Channel.L, Channel.R)
                ChannelLayoutId.SURROUND_5_1 -> listOf(Channel.L, Channel.R, Channel.C, Channel.LFE, Channel.LS, Channel.RS)
                ChannelLayoutId.SURROUND_7_1 -> listOf(
                    Channel.L, Channel.R, Channel.C, Channel.LFE, Channel.LS, Channel.RS, Channel.LB, Channel.RB,
                )
                ChannelLayoutId.UNKNOWN -> List(channelCount.coerceAtLeast(0)) { Channel.UNKNOWN }
            }
            return ChannelLayout(known, channels)
        }
    }
}
