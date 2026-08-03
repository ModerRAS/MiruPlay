package com.miruplay.tv.audio

import kotlin.math.max

class StreamingDspProcessor(
    initialPlan: CompiledDspPlan,
    private val crossfadeFrames: Int = (initialPlan.sampleRateHz * 20 / 1_000).coerceAtLeast(1),
) {
    private var activePlan = initialPlan
    private var activeState = FilterState(initialPlan)
    private var pendingPlan: CompiledDspPlan? = null
    private var pendingState: FilterState? = null
    private var crossfadeProgress = 0

    fun queuePlan(plan: CompiledDspPlan) {
        require(plan.layout.channelCount == activePlan.layout.channelCount) { "input channel count cannot change during playback" }
        require(plan.outputChannelCount == activePlan.outputChannelCount) { "output channel count cannot change during playback" }
        pendingPlan = plan
        pendingState = FilterState(plan)
        crossfadeProgress = 0
    }

    fun process(interleavedPcm: FloatArray, frameCount: Int): FloatArray {
        val inputChannels = activePlan.layout.channelCount
        val outputChannels = activePlan.outputChannelCount
        require(frameCount >= 0 && interleavedPcm.size == frameCount * inputChannels) {
            "PCM buffer does not match the active channel layout"
        }
        if (frameCount == 0) return FloatArray(0)
        val output = FloatArray(frameCount * outputChannels)
        for (frame in 0 until frameCount) {
            val inputOffset = frame * inputChannels
            val outputOffset = frame * outputChannels
            val oldFrame = routeFrame(activeState.processFrame(interleavedPcm, inputOffset), activePlan)
            val nextState = pendingState
            if (nextState == null) {
                oldFrame.copyInto(output, outputOffset)
                continue
            }
            val newFrame = routeFrame(nextState.processFrame(interleavedPcm, inputOffset), pendingPlan ?: activePlan)
            crossfadeProgress += 1
            val amount = (crossfadeProgress.toFloat() / crossfadeFrames).coerceIn(0f, 1f)
            for (channel in 0 until outputChannels) {
                output[outputOffset + channel] = oldFrame[channel] * (1f - amount) + newFrame[channel] * amount
            }
            if (amount >= 1f) {
                activePlan = pendingPlan ?: activePlan
                activeState = nextState
                pendingPlan = null
                pendingState = null
                crossfadeProgress = 0
            }
        }
        return output
    }

    fun endOfStream(): FloatArray {
        val channels = activePlan.layout.channelCount
        val frames = max(activePlan.groupDelayFrames, pendingPlan?.groupDelayFrames ?: 0)
        if (frames == 0) return FloatArray(0)
        return process(FloatArray(frames * channels), frames)
    }

    private fun routeFrame(source: FloatArray, plan: CompiledDspPlan): FloatArray = when (plan.outputMode) {
        com.miruplay.tv.model.AudioDspOutputMode.AUTO_PRESERVE -> source
        com.miruplay.tv.model.AudioDspOutputMode.STEREO_DOWNMIX -> SurroundDownmix.standard(source, plan.layout)
        com.miruplay.tv.model.AudioDspOutputMode.HRTF_BINAURAL -> SurroundDownmix.hrtf(source, plan.layout)
    }

    private class FilterState(private val plan: CompiledDspPlan) {
        private val biquadStates = plan.biquadsByChannel.map { chain ->
            Array(chain.size) { BiquadState() }
        }
        private val firHistory = plan.firTapsByChannel.map { taps -> FloatArray(taps.size) }
        private var firCursor = 0

        fun processFrame(input: FloatArray, offset: Int): FloatArray {
            val result = FloatArray(plan.layout.channelCount)
            for (channel in result.indices) {
                var value = input[offset + channel].toDouble()
                plan.biquadsByChannel[channel].forEachIndexed { index, coefficients ->
                    value = biquadStates[channel][index].process(value, coefficients)
                }
                val taps = plan.firTapsByChannel[channel]
                if (taps.isNotEmpty()) {
                    val history = firHistory[channel]
                    history[firCursor] = value.toFloat()
                    var filtered = 0.0
                    for (tap in taps.indices) {
                        filtered += taps[tap] * history[(firCursor - tap + history.size) % history.size]
                    }
                    value = filtered
                }
                result[channel] = value.toFloat()
            }
            if (firHistory.any { it.isNotEmpty() }) {
                firCursor = (firCursor + 1) % firHistory.first { it.isNotEmpty() }.size
            }
            return result
        }
    }

    private class BiquadState {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(input: Double, coefficients: BiquadCoefficients): Double {
            val output = coefficients.b0 * input + z1
            z1 = coefficients.b1 * input - coefficients.a1 * output + z2
            z2 = coefficients.b2 * input - coefficients.a2 * output
            return output
        }
    }
}
