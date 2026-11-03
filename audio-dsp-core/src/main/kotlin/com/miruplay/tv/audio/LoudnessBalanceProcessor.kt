package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioBalanceLevel
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Streaming AGC-style loudness balance: chase RMS toward [params.targetRmsLinear] with separate
 * attack/release, optional calibration window, noise gate, and a linked peak ceiling.
 */
class LoudnessBalanceProcessor(
    level: AudioBalanceLevel,
    sampleRateHz: Int,
) {
    private var params: LoudnessBalanceParams? = LoudnessBalanceParams.forLevel(level)
    private var sampleRate = sampleRateHz.coerceAtLeast(1)

    private var currentGain = 1f
    private var smoothedRms = 0f
    private var calibrationAccum = 0.0
    private var calibrationFrames = 0
    private var calibrated = false

    fun setLevel(level: AudioBalanceLevel, sampleRateHz: Int) {
        val next = LoudnessBalanceParams.forLevel(level)
        val rateChanged = sampleRateHz.coerceAtLeast(1) != sampleRate
        sampleRate = sampleRateHz.coerceAtLeast(1)
        if (next == params && !rateChanged) return
        params = next
        resetState()
    }

    fun resetState() {
        currentGain = 1f
        smoothedRms = 0f
        calibrationAccum = 0.0
        calibrationFrames = 0
        calibrated = false
    }

    fun processInPlace(interleaved: FloatArray, channels: Int) {
        val active = params ?: return
        require(channels > 0) { "channels must be positive" }
        if (interleaved.isEmpty()) return
        val frames = interleaved.size / channels
        val attackCoeff = timeCoefficient(active.attackMs)
        val releaseCoeff = timeCoefficient(active.releaseMs)
        val rmsSmoothCoeff = timeCoefficient(active.rmsSmoothMs)
        val calibrateFrames = (active.calibrateMs * 0.001f * sampleRate).toInt().coerceAtLeast(1)
        val gateLinear = dbToLinear(active.gateDb)
        val targetLinear = active.targetRmsLinear
        val minGain = dbToLinear(-active.maxCutDb)
        val maxGain = dbToLinear(active.maxBoostDb)
        val peakCeiling = active.peakCeiling

        for (frame in 0 until frames) {
            val offset = frame * channels
            var sumSquares = 0.0
            for (channel in 0 until channels) {
                val sample = interleaved[offset + channel].toDouble()
                sumSquares += sample * sample
            }
            val frameRms = sqrt(sumSquares / channels).toFloat()

            if (!calibrated) {
                calibrationAccum += frameRms.toDouble()
                calibrationFrames += 1
                if (calibrationFrames >= calibrateFrames) {
                    smoothedRms = (calibrationAccum / calibrationFrames).toFloat().coerceAtLeast(gateLinear)
                    calibrated = true
                } else {
                    applyGain(interleaved, offset, channels, currentGain, peakCeiling)
                    continue
                }
            }

            smoothedRms += (frameRms - smoothedRms) * rmsSmoothCoeff
            val measured = max(smoothedRms, gateLinear)
            var desiredGain = if (measured <= gateLinear) {
                min(currentGain, 1f)
            } else {
                (targetLinear / measured).coerceIn(minGain, maxGain)
            }

            val coeff = if (desiredGain < currentGain) attackCoeff else releaseCoeff
            currentGain += (desiredGain - currentGain) * coeff
            applyGain(interleaved, offset, channels, currentGain, peakCeiling)
        }
    }

    private fun applyGain(
        interleaved: FloatArray,
        offset: Int,
        channels: Int,
        gain: Float,
        peakCeiling: Float,
    ) {
        var peak = 0f
        for (channel in 0 until channels) {
            val index = offset + channel
            val scaled = interleaved[index] * gain
            interleaved[index] = scaled
            peak = max(peak, kotlin.math.abs(scaled))
        }
        if (peak <= peakCeiling || peak <= 0f) return
        val limit = peakCeiling / peak
        for (channel in 0 until channels) {
            val index = offset + channel
            interleaved[index] *= limit
        }
        currentGain *= limit
    }

    private fun timeCoefficient(timeMs: Float): Float {
        val ms = timeMs.coerceAtLeast(1f)
        return (1f - kotlin.math.exp(-1f / (ms * 0.001f * sampleRate))).coerceIn(0f, 1f)
    }

    private data class LoudnessBalanceParams(
        val maxBoostDb: Float,
        val maxCutDb: Float,
        val attackMs: Float,
        val releaseMs: Float,
        val rmsSmoothMs: Float,
        val gateDb: Float,
        val calibrateMs: Float,
        val targetRmsLinear: Float,
        val peakCeiling: Float,
    ) {
        companion object {
            private const val TARGET_RMS_DBFS = -20f
            private const val PEAK_CEILING = 0.98f

            fun forLevel(level: AudioBalanceLevel): LoudnessBalanceParams? = when (level) {
                AudioBalanceLevel.OFF -> null
                AudioBalanceLevel.LOW -> LoudnessBalanceParams(
                    maxBoostDb = 6f,
                    maxCutDb = 6f,
                    attackMs = 120f,
                    releaseMs = 1800f,
                    rmsSmoothMs = 250f,
                    gateDb = -54f,
                    calibrateMs = 3500f,
                    targetRmsLinear = dbToLinear(TARGET_RMS_DBFS),
                    peakCeiling = PEAK_CEILING,
                )
                AudioBalanceLevel.MEDIUM -> LoudnessBalanceParams(
                    maxBoostDb = 10f,
                    maxCutDb = 8f,
                    attackMs = 80f,
                    releaseMs = 1400f,
                    rmsSmoothMs = 180f,
                    gateDb = -50f,
                    calibrateMs = 2500f,
                    targetRmsLinear = dbToLinear(TARGET_RMS_DBFS),
                    peakCeiling = PEAK_CEILING,
                )
                AudioBalanceLevel.HIGH -> LoudnessBalanceParams(
                    maxBoostDb = 14f,
                    maxCutDb = 10f,
                    attackMs = 50f,
                    releaseMs = 900f,
                    rmsSmoothMs = 120f,
                    gateDb = -48f,
                    calibrateMs = 1800f,
                    targetRmsLinear = dbToLinear(TARGET_RMS_DBFS),
                    peakCeiling = PEAK_CEILING,
                )
            }
        }
    }
}

internal fun dbToLinear(db: Float): Float {
    if (db <= -120f) return 0f
    return kotlin.math.exp((db * ln(10.0) / 20.0).toFloat())
}
