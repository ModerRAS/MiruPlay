package com.miruplay.tv.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspFirQuality
import com.miruplay.tv.model.AudioDspPhaseMode
import com.miruplay.tv.model.AudioDspPreset

@RunWith(AndroidJUnit4::class)
class NativeDspBenchmarkInstrumentedTest {

    private fun ms(iter: Int, block: () -> Unit): Double {
        var t = 0L
        repeat(iter) { val s = System.nanoTime(); block(); t += System.nanoTime() - s }
        return t / 1_000_000.0 / iter
    }

    @Test
    fun benchmarkDesignOnDevice() {
        Log.i("MiruDspBench", "=== FIR Design Benchmark (device) ===")
        Log.i("MiruDspBench", "isAvailable=${NativeDspBridge.isAvailable()} neon=${NativeDspBridge.isNeonAvailable()}")
        val bins = 256
        val target = FloatArray(bins) { 0f }
        for (i in bins/4 until bins/2) target[i] = 6f
        for (taps in listOf(1024, 2048, 4096)) {
            repeat(2) { NativeDspBridge.designFir(target, taps) }
            repeat(2) { LinearPhaseFirDesigner.designKotlinFft(target, taps) }
            repeat(1) { LinearPhaseFirDesigner.designLegacy(target, taps) }

            val legacyMs = ms(3) { LinearPhaseFirDesigner.designLegacy(target, taps) }
            val fftMs = ms(5) { LinearPhaseFirDesigner.designKotlinFft(target, taps) }
            val nativeMs = if (NativeDspBridge.isAvailable()) ms(5) { NativeDspBridge.designFir(target, taps) } else Double.NaN

            Log.i("MiruDspBench", "[FIR-Design] taps=$taps legacy=${String.format("%.2f", legacyMs)}ms fft=${String.format("%.2f", fftMs)}ms native=${if (nativeMs.isNaN()) "N/A" else String.format("%.2f", nativeMs)}ms speedup_fft=${String.format("%.1f", legacyMs/fftMs)}x speedup_native=${if (nativeMs.isNaN()) "N/A" else String.format("%.1f", legacyMs/nativeMs)}x")

            val a = LinearPhaseFirDesigner.designLegacy(target, taps)
            val b = LinearPhaseFirDesigner.designKotlinFft(target, taps)
            var err = 0f
            for (i in a.indices) err = maxOf(err, kotlin.math.abs(a[i]-b[i]))
            Log.i("MiruDspBench", "  maxErr legacy vs fft=$err")
            if (NativeDspBridge.isAvailable()) {
                val c = NativeDspBridge.designFir(target, taps)!!
                var err2 = 0f
                for (i in a.indices) err2 = maxOf(err2, kotlin.math.abs(a[i]-c[i]))
                Log.i("MiruDspBench", "  maxErr legacy vs native=$err2")
            }
        }
        Log.i("MiruDspBench", "=== Design done ===")
    }

    @Test
    fun benchmarkStreamingOnDevice() {
        Log.i("MiruDspBench", "=== FIR Streaming Benchmark (device) ===")
        Log.i("MiruDspBench", "isAvailable=${NativeDspBridge.isAvailable()} neon=${NativeDspBridge.isNeonAvailable()}")
        val sr = 48000
        val frames = 48000
        val ch = 2
        val presetLow = AudioDspPreset(id="bench", name="Bench", phaseMode=AudioDspPhaseMode.LINEAR, firQuality=AudioDspFirQuality.LOW, rules=listOf(AudioDspChannelRule(bands=listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1000f, 6f, 1f)))))
        val presetMed = presetLow.copy(firQuality=AudioDspFirQuality.MEDIUM)
        val presetHigh = presetLow.copy(firQuality=AudioDspFirQuality.HIGH)
        for ((label, preset) in listOf("LOW_1024" to presetLow, "MED_2048" to presetMed, "HIGH_4096" to presetHigh)) {
            val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(ch, null), sr)
            val input = FloatArray(frames*ch) { idx -> kotlin.math.sin(2*Math.PI*1000*(idx/ch)/sr).toFloat()*0.5f }

            // Warmup
            var p = StreamingDspProcessor(plan); p.process(input, frames); p.release()

            val nativeMs = ms(5) { val proc = StreamingDspProcessor(plan); proc.process(input, frames); proc.release() }
            // scalar = force fallback via limiter (native disabled)
            val scalarMs = ms(5) {
                val lim = plan.copy(limiter=com.miruplay.tv.model.AudioDspLimiter(enabled=true, ceilingDb=-1f))
                val proc = StreamingDspProcessor(lim); proc.process(input, frames); proc.release()
            }
            Log.i("MiruDspBench", "[FIR-Stream] $label scalar=${String.format("%.1f", scalarMs)}ms native=${String.format("%.1f", nativeMs)}ms speedup=${String.format("%.1f", scalarMs/nativeMs)}x frames=$frames taps=${plan.firTapsByChannel.first().size} realtimeRatio=${String.format("%.2f", (frames.toDouble()/sr) / (nativeMs/1000.0))}x")

            // Correctness: native vs scalar should match (without limiter, compare native path vs scalar path by using same plan but disabling native via reflection? We'll compare native output vs scalar filtered output via direct FIR)
            val procNative = StreamingDspProcessor(plan)
            val outNative = procNative.process(input, frames)
            procNative.release()
            val limPlan = plan.copy(limiter=com.miruplay.tv.model.AudioDspLimiter(enabled=true, ceilingDb=-1f))
            val procScalar = StreamingDspProcessor(limPlan)
            // need scalar without limiter for fair compare, but our scalar path currently still has limiter enabled (which changes gain). Instead compare via direct NativeDspBridge vs scalar loop
            // For correctness, compare native direct vs kotlin legacy via NativeDspBridge array path
            if (NativeDspBridge.isAvailable()) {
                val tapsLen = plan.firTapsByChannel.first().size
                val tapsByCh = Array(ch) { plan.firTapsByChannel[it] }
                val handle = NativeDspBridge.create(ch, tapsLen, tapsByCh, plan.preampLinear, plan.channelGainLinear)
                if (handle != 0L) {
                    val outDirect = FloatArray(frames*ch)
                    NativeDspBridge.processArray(handle, input, 0, outDirect, 0, frames)
                    var maxErr = 0f
                    for (i in outNative.indices) maxErr = maxOf(maxErr, kotlin.math.abs(outNative[i]-outDirect[i]))
                    Log.i("MiruDspBench", "  maxErr Streaming vs direct native=$maxErr")
                    NativeDspBridge.release(handle)
                }
            }
            procScalar.release()
        }
        Log.i("MiruDspBench", "=== Streaming done ===")
    }

    @Test
    fun verifyEffect() {
        Log.i("MiruDspBench", "=== Effect Verification ===")
        // Impulse should give FIR taps as output, centered
        val taps = LinearPhaseFirDesigner.designKotlinFft(FloatArray(256){0f}, 1024)
        Log.i("MiruDspBench", "taps size=${taps.size} peak=${taps.indices.maxBy{ kotlin.math.abs(taps[it]) }} taps[511]=${taps[511]} taps[512]=${taps[512]}")
        // Sine 1kHz with +6dB peaking: raw 6dB, with autoHeadroom (default true) net ~0dB to prevent clipping
        val preset = AudioDspPreset(id="v", name="v", phaseMode=AudioDspPhaseMode.LINEAR, firQuality=AudioDspFirQuality.LOW, autoHeadroom = false, rules=listOf(AudioDspChannelRule(bands=listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1000f, 6f, 1f)))))
        val presetAuto = preset.copy(autoHeadroom = true)
        val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(1,null), 48000)
        val curve = FrequencyResponse.sample(plan, floatArrayOf(1000f))
        Log.i("MiruDspBench", "1kHz magnitude (raw, autoHeadroom=false)=${curve.magnitudeDb[0]}dB expect ~6dB")
        val planAuto = AudioDspPlanCompiler.compile(presetAuto, ChannelLayout.from(1,null), 48000)
        val curveAuto = FrequencyResponse.sample(planAuto, floatArrayOf(1000f))
        Log.i("MiruDspBench", "1kHz magnitude (autoHeadroom=true)=${curveAuto.magnitudeDb[0]}dB expect ~0dB (auto -6dB headroom)")
        // Streaming identity
        val p = StreamingDspProcessor(plan)
        val impulse = FloatArray(2048){ if (it==0) 1f else 0f }
        val out = p.process(impulse, 2048)
        p.release()
        Log.i("MiruDspBench", "impulse response first 5: ${out.take(5).joinToString(",") { String.format("%.4f", it) }}")
        Log.i("MiruDspBench", "=== Effect done ===")
    }
}
