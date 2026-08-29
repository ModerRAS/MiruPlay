package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspFirQuality
import com.miruplay.tv.model.AudioDspPhaseMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Test

class FirBenchmarkTest {

    private fun ms(iterations: Int, block: () -> Unit): Double {
        var total = 0L
        repeat(iterations) {
            val s = System.nanoTime()
            block()
            total += System.nanoTime() - s
        }
        return total / 1_000_000.0 / iterations
    }

    @Test
    fun benchmarkDesign() {
        val bins = 256
        val target = FloatArray(bins) { 0f }
        for (i in bins/4 until bins/2) target[i] = 6f

        for (taps in listOf(1024, 2048, 4096)) {
            repeat(3) { LinearPhaseFirDesigner.designKotlinFft(target, taps) }
            repeat(1) { LinearPhaseFirDesigner.designLegacy(target, taps) }

            val legacyMs = ms(5) { LinearPhaseFirDesigner.designLegacy(target, taps) }
            val fftMs = ms(10) { LinearPhaseFirDesigner.designKotlinFft(target, taps) }
            val nativeMs: Double? = if (NativeDspBridge.isAvailable()) {
                ms(10) { NativeDspBridge.designFir(target, taps) }
            } else null

            val speedup = legacyMs / fftMs
            println("[FIR-Design] taps=$taps legacy=${String.format("%.2f", legacyMs)}ms fft=${String.format("%.2f", fftMs)}ms native=${nativeMs?.let { String.format("%.2f", it) } ?: "N/A"} speedup=${String.format("%.1f", speedup)}x")

            val a = LinearPhaseFirDesigner.designLegacy(target, taps)
            val b = LinearPhaseFirDesigner.designKotlinFft(target, taps)
            var maxErr = 0f
            for (i in a.indices) maxErr = maxOf(maxErr, kotlin.math.abs(a[i] - b[i]))
            println("  maxErr legacy vs fft = $maxErr")
            assert(maxErr < 1e-5f)

            if (nativeMs != null) {
                val c = NativeDspBridge.designFir(target, taps)!!
                var maxErrNative = 0f
                for (i in a.indices) maxErrNative = maxOf(maxErrNative, kotlin.math.abs(a[i] - c[i]))
                println("  maxErr legacy vs native = $maxErrNative")
                assert(maxErrNative < 1e-4f)
            }
        }
    }

    @Test
    fun benchmarkStreaming() {
        val sampleRate = 48000
        val frames = 48000
        val channels = 2
        val presetLow = AudioDspPreset(
            id = "bench", name = "Bench",
            phaseMode = AudioDspPhaseMode.LINEAR,
            firQuality = AudioDspFirQuality.LOW,
            rules = listOf(AudioDspChannelRule(bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1000f, 6f, 1f))))
        )
        val presetMed = presetLow.copy(firQuality = AudioDspFirQuality.MEDIUM)
        val presetHigh = presetLow.copy(firQuality = AudioDspFirQuality.HIGH)

        val cases = listOf("LOW_1024" to presetLow, "MED_2048" to presetMed, "HIGH_4096" to presetHigh)
        for ((label, preset) in cases) {
            val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(channels, null), sampleRate)
            val input = FloatArray(frames * channels) { idx -> kotlin.math.sin(2 * Math.PI * 1000 * (idx / channels) / sampleRate).toFloat() * 0.5f }

            val nativeMs = ms(5) {
                val p = StreamingDspProcessor(plan)
                p.process(input, frames)
                p.release()
            }

            // scalar fallback = limit enabled forces fallback (native disabled when limiter != null)
            val scalarMs = ms(5) {
                val limPlan = plan.copy(limiter = com.miruplay.tv.model.AudioDspLimiter(enabled = true, ceilingDb = -1f))
                val p = StreamingDspProcessor(limPlan)
                p.process(input, frames)
                p.release()
            }

            val speedup = if (nativeMs > 0) scalarMs / nativeMs else 0.0
            println("[FIR-Stream] $label scalar=${String.format("%.1f", scalarMs)}ms native=${String.format("%.1f", nativeMs)}ms speedup=${String.format("%.1f", speedup)}x frames=$frames taps=${plan.firTapsByChannel.first().size}")
        }
    }

    @Test
    fun benchmarkZeroAlloc() {
        val plan = AudioDspPlanCompiler.compile(
            AudioDspPreset(id = "bench", name = "Bench", phaseMode = AudioDspPhaseMode.LINEAR, firQuality = AudioDspFirQuality.LOW,
                rules = listOf(AudioDspChannelRule(bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1000f, 6f, 1f))))),
            ChannelLayout.from(2, null), 48000
        )
        val input = FloatArray(1024 * 2) { 0.1f }
        val proc = StreamingDspProcessor(plan)
        val before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        repeat(1000) { proc.process(input, 1024) }
        System.gc()
        Thread.sleep(100)
        val after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val deltaKb = (after - before) / 1024
        println("[ZeroAlloc] delta=${deltaKb}KB after 1000 batches")
        proc.release()
    }
}
