package com.miruplay.tv.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private var limiter = activePlan.limiter.takeIf { it.enabled }?.let {
        LinkedLimiter(it.ceilingDb, it.releaseMs, activePlan.sampleRateHz)
    }
    // ponytail: pre-allocated native arena, one alloc at create, zero alloc in process
    private var nativeEngine: NativeFirEngine? = createNativeEngine(initialPlan)

    private fun createNativeEngine(plan: CompiledDspPlan): NativeFirEngine? {
        if (plan.firTapsByChannel.all { it.isEmpty() }) return null
        if (!NativeDspBridge.isAvailable()) return null
        val tapsLen = plan.firTapsByChannel.firstOrNull { it.isNotEmpty() }?.size ?: return null
        // Require uniform tapsLen for shared cursor (linear phase guarantees this)
        if (plan.firTapsByChannel.any { it.isNotEmpty() && it.size != tapsLen }) return null
        return try {
            NativeFirEngine(plan)
        } catch (_: Throwable) { null }
    }

    fun queuePlan(plan: CompiledDspPlan) {
        require(plan.layout.channelCount == activePlan.layout.channelCount) { "input channel count cannot change during playback" }
        require(plan.outputChannelCount == activePlan.outputChannelCount) { "output channel count cannot change during playback" }
        // Fast path: native taps update without rebuilding history when geometry matches
        val engine = nativeEngine
        val pendingTapsLen = plan.firTapsByChannel.firstOrNull { it.isNotEmpty() }?.size ?: 0
        if (engine != null && engine.matchesGeometry(plan) && pendingTapsLen == engine.tapsLen) {
            engine.updateTaps(plan)
            // Still need FilterState for crossfade biquad? For LINEAR biquads empty, FilterState unneeded. Keep for limiter fallback.
        }
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
        // Fast native batch path: LINEAR FIR, no pending crossfade, no limiter, uniform taps, no biquads (linear phase has empty biquads)
        val engine = nativeEngine
        val canUseNative = engine != null && pendingState == null && limiter == null &&
            activePlan.biquadsByChannel.all { it.isEmpty() } && activePlan.outputMode == com.miruplay.tv.model.AudioDspOutputMode.AUTO_PRESERVE &&
            inputChannels == outputChannels && engine.tapsLen > 0
        if (canUseNative) {
            val output = FloatArray(frameCount * outputChannels)
            // One JNI per batch, zero per-frame alloc, NEON 4-wide
            engine.processBatch(interleavedPcm, 0, output, 0, frameCount)
            // routeFrame no-op for AUTO_PRESERVE
            return output
        }
        val output = FloatArray(frameCount * outputChannels)
        for (frame in 0 until frameCount) {
            val inputOffset = frame * inputChannels
            val outputOffset = frame * outputChannels
            val oldFrame = routeFrame(activeState.processFrame(interleavedPcm, inputOffset), activePlan)
            val nextState = pendingState
            if (nextState == null) {
                oldFrame.copyInto(output, outputOffset)
                limiter?.process(oldFrame, outputChannels)?.copyInto(output, outputOffset)
                continue
            }
            val newFrame = routeFrame(nextState.processFrame(interleavedPcm, inputOffset), pendingPlan ?: activePlan)
            crossfadeProgress += 1
            val amount = (crossfadeProgress.toFloat() / crossfadeFrames).coerceIn(0f, 1f)
            for (channel in 0 until outputChannels) {
                output[outputOffset + channel] = oldFrame[channel] * (1f - amount) + newFrame[channel] * amount
            }
            limiter?.process(output.copyOfRange(outputOffset, outputOffset + outputChannels), outputChannels)
                ?.copyInto(output, outputOffset)
            if (amount >= 1f) {
                activePlan = pendingPlan ?: activePlan
                activeState = nextState
                // Swap native engine to pending plan if possible (reuse history)
                if (engine != null && pendingPlan != null && engine.matchesGeometry(pendingPlan!!)) {
                    // already updated via updateTaps
                } else {
                    nativeEngine?.release()
                    nativeEngine = pendingPlan?.let { createNativeEngine(it) }
                }
                pendingPlan = null
                pendingState = null
                crossfadeProgress = 0
                limiter = activePlan.limiter.takeIf { it.enabled }?.let {
                    LinkedLimiter(it.ceilingDb, it.releaseMs, activePlan.sampleRateHz)
                }
            }
        }
        return output
    }

    fun endOfStream(): FloatArray {
        val channels = activePlan.layout.channelCount
        val frames = max(firTailFrames(activePlan), pendingPlan?.let(::firTailFrames) ?: 0)
        if (frames == 0) return FloatArray(0)
        return process(FloatArray(frames * channels), frames)
    }

    fun release() {
        nativeEngine?.release()
        nativeEngine = null
    }

    // ponytail: pre-allocated arena wrapper, one native alloc, zero alloc per batch
    private class NativeFirEngine(plan: CompiledDspPlan) {
        val tapsLen: Int = plan.firTapsByChannel.first { it.isNotEmpty() }.size
        val channels: Int = plan.layout.channelCount
        private var handle: Long = 0
        private val channelGain = plan.channelGainLinear.copyOf()
        private var preamp = plan.preampLinear

        init {
            val tapsByChannel = Array(channels) { ch -> plan.firTapsByChannel[ch].copyOf() }
            handle = NativeDspBridge.create(channels, tapsLen, tapsByChannel, preamp, channelGain)
            if (handle == 0L) throw IllegalStateException("nativeCreate failed")
        }

        fun matchesGeometry(plan: CompiledDspPlan): Boolean =
            plan.layout.channelCount == channels && plan.firTapsByChannel.firstOrNull { it.isNotEmpty() }?.size == tapsLen

        fun updateTaps(plan: CompiledDspPlan) {
            val tapsByChannel = Array(channels) { ch -> plan.firTapsByChannel[ch].copyOf() }
            NativeDspBridge.updateTaps(handle, tapsByChannel, plan.preampLinear, plan.channelGainLinear)
            preamp = plan.preampLinear
        }

        fun processBatch(inArray: FloatArray, inOff: Int, outArray: FloatArray, outOff: Int, frames: Int) {
            // Use array path to avoid direct buffer alloc per call; still one JNI per batch
            NativeDspBridge.processArray(handle, inArray, inOff, outArray, outOff, frames)
        }

        fun release() {
            if (handle != 0L) {
                NativeDspBridge.release(handle)
                handle = 0
            }
        }
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
        private val firHistory = plan.firTapsByChannel.map { taps -> DoubleArray(taps.size) } // ponytail: double precision per user request
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
                    history[firCursor] = value
                    var filtered = 0.0
                    for (tap in taps.indices) {
                        filtered += taps[tap].toDouble() * history[(firCursor - tap + history.size) % history.size]
                    }
                    value = filtered
                }
                value *= plan.preampLinear.toDouble() * plan.channelGainLinear.getOrElse(channel) { 1f }.toDouble()
                result[channel] = value.toFloat()
            }
            if (firHistory.any { it.isNotEmpty() }) {
                firCursor = (firCursor + 1) % firHistory.first { it.isNotEmpty() }.size
            }
            return result
        }
    }

    private fun firTailFrames(plan: CompiledDspPlan): Int =
        plan.firTapsByChannel.maxOfOrNull { (it.size - 1).coerceAtLeast(0) } ?: 0

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
